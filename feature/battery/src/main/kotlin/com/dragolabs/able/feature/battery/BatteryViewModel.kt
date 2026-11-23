package com.dragolabs.able.feature.battery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dragolabs.able.core.capability.PermissionCoordinator
import com.dragolabs.able.core.capability.ShizukuAdapter
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.data.BatteryStatusRepository
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.data.battery.BatteryProfileRepository
import com.dragolabs.able.core.database.UserPreferencesRepository
import com.dragolabs.able.feature.battery.data.AdvancedBatteryHealthReader
import com.dragolabs.able.feature.battery.data.AdvancedReadResult
import com.dragolabs.able.feature.battery.data.AppLabelResolver
import com.dragolabs.able.feature.battery.data.AppUsageReviewSource
import com.dragolabs.able.feature.battery.data.AppUsageSummary
import com.dragolabs.able.feature.battery.data.BatteryHistoryRepository
import com.dragolabs.able.feature.battery.domain.rates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Status & profiles, hardware health and diagnostics tabs. */
@OptIn(ExperimentalCoroutinesApi::class)
class BatteryViewModel(
    private val battery: BatteryStatusRepository,
    private val preferences: UserPreferencesRepository,
    private val profileRepository: BatteryProfileRepository,
    private val history: BatteryHistoryRepository,
    private val shizuku: ShizukuAdapter,
    private val permissions: PermissionCoordinator,
    private val advancedReader: AdvancedBatteryHealthReader,
    private val usageSource: AppUsageReviewSource,
    private val labelResolver: AppLabelResolver,
) : ViewModel() {

    private data class UsageState(val granted: Boolean, val loading: Boolean = false, val apps: List<AppUsageSummary>? = null)

    private data class LocalState(
        val advanced: AdvancedHealthState,
        val usage: UsageState,
        val reviewed: Set<ChecklistItem>,
        val message: BatteryMessage?,
        val shizuku: ShizukuConnectionState,
    )

    private val hasBattery = runCatching { battery.hasBattery() }.getOrDefault(true)
    private val advanced = MutableStateFlow<AdvancedHealthState>(AdvancedHealthState.Idle)
    private val usage = MutableStateFlow(UsageState(granted = permissions.hasUsageStatsPermission()))
    private val reviewed = MutableStateFlow(emptySet<ChecklistItem>())
    private val message = MutableStateFlow<BatteryMessage?>(null)

    /** Labels for the apps that active profiles would act on ("What applies now"). */
    private val evaluation = profileRepository.evaluation
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    private val actionLabels = evaluation
        .map { it.appActions.keys }
        .distinctUntilChanged()
        .mapLatest { labelResolver.labels(it).toMap() }
        .onStart { emit(emptyMap()) }

    private val protectedApps = preferences.userPreferencesFlow
        .map { it.protectedAppPackages }
        .distinctUntilChanged()
        .mapLatest { labelResolver.labels(it) }
        .onStart { emit(emptyList()) }

    private val localState = combine(advanced, usage, reviewed, message, shizuku.connectionState) { a, u, r, m, s ->
        LocalState(a, u, r, m, s)
    }

    private val profilesState = combine(
        profileRepository.profiles,
        evaluation,
        preferences.userPreferencesFlow.map { it.batteryAutomationPaused }.distinctUntilChanged(),
        protectedApps,
        actionLabels,
    ) { list, eval, paused, protected, labels ->
        ProfilesUiState(
            profiles = list,
            evaluation = eval,
            automationPaused = paused,
            protectedApps = protected,
            appLabels = labels,
        )
    }

    private val inputs = combine(
        battery.status,
        preferences.userPreferencesFlow.map { it.temperatureUnit }.distinctUntilChanged(),
        profilesState,
        history.sessions,
    ) { status, unit, profiles, sessions ->
        val latestDischarge = sessions.firstOrNull { !it.isCharging }?.let { SessionItem(it, it.rates(), null) }
        BatteryUiState(
            loading = false,
            hasBattery = hasBattery,
            status = status,
            temperatureUnit = unit,
            profiles = profiles,
            diagnostics = DiagnosticsUiState(latestDischarge = latestDischarge),
        )
    }

    val uiState: StateFlow<BatteryUiState> = combine(inputs, localState) { base, local ->
        base.copy(
            health = base.health.copy(shizuku = local.shizuku, advanced = local.advanced),
            diagnostics = base.diagnostics.copy(
                reviewed = local.reviewed,
                usageAccessGranted = local.usage.granted,
                usageLoading = local.usage.loading,
                topApps = local.usage.apps,
            ),
            message = local.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BatteryUiState())

    init {
        refreshAccess()
    }

    /** Called when the screen resumes: grants can change in Android settings without a broadcast. */
    fun refreshAccess() {
        shizuku.updateState()
        val granted = permissions.hasUsageStatsPermission()
        usage.update { it.copy(granted = granted) }
        if (granted && usage.value.apps == null) refreshUsage()
    }

    fun refreshUsage() {
        if (!permissions.hasUsageStatsPermission()) {
            usage.value = UsageState(granted = false)
            return
        }
        usage.update { it.copy(granted = true, loading = true) }
        viewModelScope.launch {
            val apps = usageSource.topApps()
            usage.value = UsageState(granted = true, loading = false, apps = apps)
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    fun showMessage(value: BatteryMessage) {
        message.value = value
    }

    fun setProfileEnabled(profileId: String, enabled: Boolean) = viewModelScope.launch {
        profileRepository.setEnabled(profileId, enabled)
    }

    /** Starts or stops a profile by hand; it stays active until stopped (or the profile is switched off). */
    fun setManualActive(profileId: String, active: Boolean) = viewModelScope.launch {
        profileRepository.setManualActive(profileId, active)
    }

    fun setAutomationPaused(paused: Boolean) = viewModelScope.launch { profileRepository.setAutomationPaused(paused) }

    fun readAdvancedHealth() {
        if (advanced.value == AdvancedHealthState.Reading) return
        advanced.value = AdvancedHealthState.Reading
        viewModelScope.launch {
            advanced.value = when (val result = advancedReader.read()) {
                is AdvancedReadResult.Success -> AdvancedHealthState.Result(result.reading)
                AdvancedReadResult.ShizukuUnavailable -> AdvancedHealthState.Idle
                AdvancedReadResult.Failed -> AdvancedHealthState.Failed
            }
        }
    }

    /** Requests Shizuku permission when the binder is alive; otherwise asks the Route to open Shizuku. */
    fun connectShizuku(): Boolean {
        if (shizuku.isBinderAlive()) {
            shizuku.requestPermission()
            return true
        }
        return false
    }

    fun openShizukuApp(context: android.content.Context) {
        if (!shizuku.openShizukuApp(context)) message.value = BatteryMessage.SHIZUKU_NOT_INSTALLED
    }

    fun toggleReviewed(item: ChecklistItem) = reviewed.update { if (item in it) it - item else it + item }

    companion object {
        val Factory = ableViewModelFactory { container ->
            BatteryViewModel(
                battery = container.battery,
                preferences = container.preferences,
                profileRepository = container.batteryProfiles,
                history = BatteryHistoryRepository(container.database.batteryDao(), container.preferences),
                shizuku = container.shizuku,
                permissions = container.permissions,
                advancedReader = AdvancedBatteryHealthReader(container.shizuku),
                usageSource = AppUsageReviewSource(container.appContext),
                labelResolver = AppLabelResolver(container.appContext),
            )
        }
    }
}
