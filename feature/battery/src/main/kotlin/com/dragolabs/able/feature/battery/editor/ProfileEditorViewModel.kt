package com.dragolabs.able.feature.battery.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.data.battery.BatteryProfileRepository
import com.dragolabs.able.core.data.battery.ProfileIssue
import com.dragolabs.able.core.data.battery.ProfileValidation
import com.dragolabs.able.core.database.UserPreferencesRepository
import com.dragolabs.able.core.model.AppInterventionLevel
import com.dragolabs.able.core.model.BatteryProfile
import com.dragolabs.able.core.model.ProfileColor
import com.dragolabs.able.core.model.ProfileIcon
import com.dragolabs.able.core.model.ProfileTrigger
import com.dragolabs.able.feature.battery.data.LauncherAppsSource
import com.dragolabs.able.feature.battery.data.RuleAppEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How the editor closed; the Battery screen reports it in a snackbar. */
enum class EditorResult { SAVED, DELETED, DUPLICATED, RESET, NOT_FOUND }

enum class NewTriggerType { BATTERY_BELOW, SCHEDULE, SCREEN_OFF, CHARGING }

data class ProfileEditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = false,
    val original: BatteryProfile? = null,
    val draft: BatteryProfile = BatteryProfile(id = ""),
    val apps: List<RuleAppEntry>? = null,
    val protectedPackages: Set<String> = emptySet(),
    val query: String = "",
    val onlyWithRules: Boolean = false,
    val busy: Boolean = false,
    val result: EditorResult? = null,
) {
    val issues: List<ProfileIssue> get() = ProfileValidation.validate(draft)
    val dirty: Boolean get() = original != null && draft != original
    val canSave: Boolean get() = !loading && !busy && dirty && issues.isEmpty()

    /** Apps matching the search, rules first. */
    val visibleApps: List<RuleAppEntry>
        get() {
            val all = apps ?: return emptyList()
            val q = query.trim()
            return all
                .filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
                .filter { !onlyWithRules || it.packageName in draft.restrictiveRules }
                .sortedByDescending { it.packageName in draft.restrictiveRules }
        }
}

/**
 * Profile editor (owner decision 2026-11-22: profiles are customizable and creatable from
 * scratch). Edits a draft; nothing is stored until Save. [profileId] null creates a new profile.
 */
class ProfileEditorViewModel(
    private val profileId: String?,
    private val repository: BatteryProfileRepository,
    preferences: UserPreferencesRepository,
    private val appsSource: LauncherAppsSource,
) : ViewModel() {

    private val editor = MutableStateFlow(ProfileEditorUiState(isNew = profileId == null))
    private val protectedPackages = preferences.userPreferencesFlow.map { it.protectedAppPackages }.distinctUntilChanged()

    val uiState: StateFlow<ProfileEditorUiState> = combine(editor, protectedPackages) { state, protected ->
        state.copy(protectedPackages = protected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), editor.value)

    init {
        viewModelScope.launch {
            val profile = if (profileId == null) {
                BatteryProfile(id = "", iconKey = ProfileIcon.BATTERY.key, colorKey = ProfileColor.TEAL.key)
            } else {
                repository.get(profileId)
            }
            if (profile == null) {
                editor.update { it.copy(loading = false, result = EditorResult.NOT_FOUND) }
                return@launch
            }
            editor.update { it.copy(loading = false, original = profile, draft = profile) }
            val apps = appsSource.apps(profile.appRules.keys)
            editor.update { it.copy(apps = apps) }
        }
    }

    private fun edit(transform: (BatteryProfile) -> BatteryProfile) = editor.update { it.copy(draft = transform(it.draft)) }

    fun setName(name: String) = edit { it.copy(name = name.take(MAX_NAME_LENGTH)) }
    fun setIcon(icon: ProfileIcon) = edit { it.copy(iconKey = icon.key) }
    fun setColor(color: ProfileColor) = edit { it.copy(colorKey = color.key) }
    fun setEnabled(enabled: Boolean) = edit { it.copy(enabled = enabled) }
    fun setOfferBatterySaver(offer: Boolean) = edit { it.copy(offerBatterySaver = offer) }

    /** Adds a trigger switched off (ADR-005); the user turns it on explicitly. */
    fun addTrigger(type: NewTriggerType) = edit {
        val trigger = when (type) {
            NewTriggerType.BATTERY_BELOW -> ProfileTrigger.BatteryBelow()
            NewTriggerType.SCHEDULE -> ProfileTrigger.Schedule()
            NewTriggerType.SCREEN_OFF -> ProfileTrigger.ScreenOffFor()
            NewTriggerType.CHARGING -> ProfileTrigger.Charging()
        }
        it.copy(triggers = it.triggers + trigger)
    }

    fun updateTrigger(index: Int, trigger: ProfileTrigger) = edit {
        if (index !in it.triggers.indices) it else it.copy(triggers = it.triggers.toMutableList().apply { set(index, trigger) })
    }

    fun removeTrigger(index: Int) = edit {
        if (index !in it.triggers.indices) it else it.copy(triggers = it.triggers.toMutableList().apply { removeAt(index) })
    }

    fun setAppLevel(packageName: String, level: AppInterventionLevel) = edit {
        val rules = it.appRules.toMutableMap()
        if (level == AppInterventionLevel.OSSERVA) rules.remove(packageName) else rules[packageName] = level
        it.copy(appRules = rules)
    }

    fun setQuery(query: String) = editor.update { it.copy(query = query) }
    fun setOnlyWithRules(only: Boolean) = editor.update { it.copy(onlyWithRules = only) }

    fun save() {
        val state = uiState.value
        if (!state.canSave) return
        runBusy {
            repository.save(state.draft.copy(name = state.draft.name.trim()))
            EditorResult.SAVED
        }
    }

    fun delete() {
        val id = profileId ?: return
        runBusy { if (repository.delete(id)) EditorResult.DELETED else null }
    }

    fun duplicate(copyName: String) {
        val id = profileId ?: return
        runBusy { repository.duplicate(id, copyName)?.let { EditorResult.DUPLICATED } }
    }

    fun resetToDefault() {
        val id = profileId ?: return
        runBusy { repository.resetToDefault(id)?.let { EditorResult.RESET } }
    }

    fun consumeResult() = editor.update { it.copy(result = null) }

    private fun runBusy(block: suspend () -> EditorResult?) {
        if (editor.value.busy) return
        editor.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = block()
            editor.update { it.copy(busy = false, result = result) }
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 40

        fun factory(profileId: String?) = ableViewModelFactory { container ->
            ProfileEditorViewModel(
                profileId = profileId,
                repository = container.batteryProfiles,
                preferences = container.preferences,
                appsSource = LauncherAppsSource(container.appContext),
            )
        }
    }
}
