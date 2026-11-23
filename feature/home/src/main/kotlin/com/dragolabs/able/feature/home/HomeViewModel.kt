package com.dragolabs.able.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dragolabs.able.core.capability.ShizukuAdapter
import com.dragolabs.able.core.data.AbleContainer
import com.dragolabs.able.core.data.ActionLogRepository
import com.dragolabs.able.core.data.BatteryStatusRepository
import com.dragolabs.able.core.data.CapabilityStatusRepository
import com.dragolabs.able.core.data.HomeLayoutRepository
import com.dragolabs.able.core.data.StorageSummaryRepository
import com.dragolabs.able.core.database.UserPreferencesRepository
import com.dragolabs.able.core.database.dao.ShortcutDao
import com.dragolabs.able.core.model.HomeCardType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Failures older than this no longer raise the Home safety banner (they stay in the card/history). */
internal const val RECENT_FAILURE_WINDOW_MS = 24L * 60 * 60 * 1000

class HomeViewModel(
    private val homeLayout: HomeLayoutRepository,
    private val battery: BatteryStatusRepository,
    private val storage: StorageSummaryRepository,
    private val preferences: UserPreferencesRepository,
    private val capabilityStatus: CapabilityStatusRepository,
    private val actionLog: ActionLogRepository,
    private val shizuku: ShizukuAdapter,
    private val shortcutDao: ShortcutDao,
    private val batteryProfiles: com.dragolabs.able.core.data.battery.BatteryProfileRepository,
    private val notices: HomeNoticeStore,
) : ViewModel() {

    constructor(container: AbleContainer) : this(
        batteryProfiles = container.batteryProfiles,
        homeLayout = container.homeLayout,
        battery = container.battery,
        storage = container.storage,
        preferences = container.preferences,
        capabilityStatus = container.capabilityStatus,
        actionLog = container.actionLog,
        shizuku = container.shizuku,
        shortcutDao = container.database.shortcutDao(),
        notices = HomeNoticeStore(container.appContext),
    )

    private val storageState = MutableStateFlow<StorageCardState>(StorageCardState.Loading)
    private val deviceState = MutableStateFlow(DeviceInfo())
    private val message = MutableStateFlow<HomeMessage?>(null)

    private data class DeviceInfo(
        val hasBattery: Boolean = true,
        val hasAccelerometer: Boolean = true,
        val hasMagnetometer: Boolean = true,
    )

    private data class Partial(
        val base: HomeUiState,
    )

    init {
        viewModelScope.launch {
            deviceState.value = withContext(Dispatchers.IO) {
                val device = capabilityStatus.device
                DeviceInfo(
                    hasBattery = battery.hasBattery(),
                    hasAccelerometer = device.hasAccelerometer,
                    hasMagnetometer = device.hasMagnetometer,
                )
            }
        }
        refreshStorage()
    }

    // Active profiles from the profile engine (several can be active at once, BAT-002).
    private val profileFlow = batteryProfiles.evaluation
        .map { evaluation -> evaluation.active.map { HomeActiveProfile(it.profile.builtInType, it.profile.name) } }
        .catch { emit(emptyList()) }
        .distinctUntilChanged()

    private val baseFlow = combine(
        homeLayout.cards,
        battery.status,
        profileFlow,
        capabilityStatus.access,
        deviceState,
    ) { cards, status, profile, access, device ->
        Partial(
            HomeUiState(
                cards = cards,
                battery = status,
                hasBattery = device.hasBattery,
                activeProfiles = profile,
                access = access,
                hasAccelerometer = device.hasAccelerometer,
                hasMagnetometer = device.hasMagnetometer,
            ),
        )
    }

    private val shortcutsFlow = shortcutDao.getAllShortcuts()
        .map(::favoriteShortcuts)
        .catch { emit(emptyList()) }
        .distinctUntilChanged()

    private val extrasFlow = combine(storageState, message, shortcutsFlow) { storageCard, msg, shortcuts ->
        Triple(storageCard, msg, shortcuts)
    }

    private val failureNoticeFlow = combine(actionLog.recentFailures(5), notices.failuresDismissedThrough) { failures, dismissed ->
        failedActionsNoticeFor(failures, System.currentTimeMillis(), dismissed)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        baseFlow,
        actionLog.recent(RECENT_HISTORY_COUNT),
        failureNoticeFlow,
        extrasFlow,
    ) { partial, recent, failureNotice, (storageCard, msg, shortcuts) ->
        partial.base.copy(
            recentActions = recent,
            storage = storageCard,
            shortcuts = shortcuts,
            alerts = alertsFor(partial.base.access.shizuku),
            failedActions = failureNotice,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Re-reads access grants and storage (called when Home resumes; Android does not broadcast most grants). */
    fun refresh() {
        capabilityStatus.refresh()
        refreshStorage()
    }

    private fun refreshStorage() {
        viewModelScope.launch {
            storageState.value = storage.read()?.let(StorageCardState::Available) ?: StorageCardState.Unavailable
        }
    }

    fun setCardVisible(type: HomeCardType, visible: Boolean) {
        viewModelScope.launch { homeLayout.setVisible(type, visible) }
    }

    fun moveCard(type: HomeCardType, offset: Int) {
        viewModelScope.launch { homeLayout.move(type, offset) }
    }

    fun resetLayout() {
        viewModelScope.launch {
            homeLayout.resetToDefault()
            message.value = HomeMessage.LAYOUT_RESET
        }
    }

    fun requestShizukuPermission() {
        if (!shizuku.requestPermission()) {
            message.value = HomeMessage.SHIZUKU_REQUEST_FAILED
        }
        capabilityStatus.refresh()
    }

    /** Reports a shortcut that could not be opened (the Route performs the launch). */
    fun reportShortcutFailure(reason: HomeMessage) {
        message.value = reason
    }

    fun consumeMessage() {
        message.value = null
    }

    /** Hides the failed-actions notice until a failure newer than the ones shown is logged. */
    fun dismissFailures() {
        val notice = uiState.value.failedActions ?: return
        notices.dismissFailuresThrough(notice.latestEpochMs)
    }
}
