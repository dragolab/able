package com.dragolabs.able.feature.tools.activities

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.common.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ActivityLauncherUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val query: String = "",
    /** Only activities the launcher shows (the app's main entry). */
    val mainOnly: Boolean = false,
    /** Grouped by app (default) or one flat list sorted by activity name. */
    val groupByApp: Boolean = true,
    /** Apps (and activities) matching [query] and the filters. */
    val results: List<AppActivities> = emptyList(),
    val totalActivities: Int = 0,
    val expanded: Set<String> = emptySet(),
    val selected: LaunchableActivity? = null,
    val message: UiMessage? = null,
) {
    /** While searching or filtering to main entries, every matching group is shown expanded. */
    fun isExpanded(packageName: String): Boolean = query.isNotBlank() || mainOnly || packageName in expanded

    val resultActivityCount: Int get() = results.sumOf { it.activities.size }

    /** Flat list for [groupByApp] = false. */
    val flatResults: List<LaunchableActivity>
        get() = results.flatMap { it.activities }.sortedWith(compareBy<LaunchableActivity> { it.label.lowercase() }.thenBy { it.appLabel.lowercase() })
}

/** Pure search over the catalog, kept separate for unit testing. */
internal fun filterActivities(apps: List<AppActivities>, query: String, mainOnly: Boolean = false): List<AppActivities> {
    val scoped = if (mainOnly) {
        apps.mapNotNull { app -> app.activities.filter { it.isLauncherEntry }.takeIf { it.isNotEmpty() }?.let { app.copy(activities = it) } }
    } else {
        apps
    }
    val q = query.trim().lowercase()
    if (q.isEmpty()) return scoped
    return scoped.mapNotNull { app ->
        if (app.appLabel.lowercase().contains(q) || app.packageName.lowercase().contains(q)) return@mapNotNull app
        val matching = app.activities.filter { it.label.lowercase().contains(q) || it.className.lowercase().contains(q) }
        if (matching.isEmpty()) null else app.copy(activities = matching)
    }
}

class ActivityLauncherViewModel(private val catalog: ActivityCatalog) : ViewModel() {

    private var all: List<AppActivities> = emptyList()
    private val _uiState = MutableStateFlow(ActivityLauncherUiState())
    val uiState: StateFlow<ActivityLauncherUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _uiState.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            val loaded = runCatching { catalog.load() }
            all = loaded.getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    loading = false,
                    loadFailed = loaded.isFailure,
                    results = filterActivities(all, it.query, it.mainOnly),
                    totalActivities = all.sumOf { app -> app.activities.size },
                )
            }
        }
    }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query, results = filterActivities(all, query, it.mainOnly)) }

    fun setMainOnly(mainOnly: Boolean) = _uiState.update { it.copy(mainOnly = mainOnly, results = filterActivities(all, it.query, mainOnly)) }

    fun setGroupByApp(grouped: Boolean) = _uiState.update { it.copy(groupByApp = grouped) }

    fun toggleGroup(packageName: String) = _uiState.update {
        it.copy(expanded = if (packageName in it.expanded) it.expanded - packageName else it.expanded + packageName)
    }

    fun select(activity: LaunchableActivity?) = _uiState.update { it.copy(selected = activity) }

    /** Open is distinct from Create shortcut (ACT-002); a rejection keeps the selection. */
    fun open(activity: LaunchableActivity) {
        val message = when (catalog.open(activity)) {
            LaunchOutcome.STARTED -> null
            LaunchOutcome.NOT_FOUND -> UiMessage(R.string.tools_activities_open_not_found)
            LaunchOutcome.DENIED -> UiMessage(R.string.tools_activities_open_denied)
            LaunchOutcome.FAILED -> UiMessage(R.string.tools_activities_open_failed)
        }
        if (message != null) _uiState.update { it.copy(message = message) }
    }

    suspend fun loadIcon(activity: LaunchableActivity, sizePx: Int): Bitmap? = catalog.loadIcon(activity, sizePx)

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
