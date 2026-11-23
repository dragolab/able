package com.dragolabs.able.feature.tools.buttons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.applock.AppIcon
import com.dragolabs.able.feature.tools.common.ToolsSearchField
import com.dragolabs.able.feature.tools.common.highlightMatches

/** Bottom sheet to choose what a gesture runs: Able actions and camera, apps, or activities. */
@Composable
internal fun TargetPickerSheet(
    gesture: ButtonGesture,
    state: ButtonMapperUiState,
    onLoadApps: () -> Unit,
    onLoadActivities: () -> Unit,
    onPick: (ButtonTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberAbleHaptics()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(tab) {
        when (tab) {
            1 -> onLoadApps()
            2 -> onLoadActivities()
        }
    }
    val pick: (ButtonTarget) -> Unit = { haptics.confirm(); onPick(it) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = AbleSpacing.lg), verticalArrangement = Arrangement.spacedBy(AbleSpacing.md)) {
            Text(
                stringResource(R.string.buttons_picker_title, stringResource(gesture.titleRes())),
                style = MaterialTheme.typography.titleMedium,
            )
            SecondaryTabRow(selectedTabIndex = tab) {
                listOf(R.string.buttons_picker_tab_actions, R.string.buttons_picker_tab_apps, R.string.buttons_picker_tab_activities)
                    .forEachIndexed { index, label ->
                        Tab(
                            selected = tab == index,
                            onClick = { haptics.tick(); tab = index; query = "" },
                            text = { Text(stringResource(label)) },
                        )
                    }
            }
            if (tab != 0) {
                ToolsSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(if (tab == 1) R.string.buttons_picker_search_apps else R.string.buttons_picker_search_activities),
                )
            }
            val q = query.trim()
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                when (tab) {
                    0 -> {
                        item(key = "camera") {
                            PickerRow(
                                title = stringResource(R.string.buttons_target_camera),
                                subtitle = stringResource(R.string.buttons_target_camera_hint),
                                leading = { Icon(Icons.Rounded.CameraAlt, contentDescription = null) },
                                onClick = { pick(ButtonTarget.Camera) },
                            )
                        }
                        items(ButtonTargets.catalogOptions, key = { it.actionId }) { option ->
                            PickerRow(
                                title = stringResource(option.labelRes),
                                leading = { Icon(option.icon, contentDescription = null) },
                                onClick = { pick(ButtonTarget.Catalog(option.actionId)) },
                            )
                        }
                    }
                    1 -> {
                        val apps = state.apps
                        if (apps == null) {
                            item(key = "loading") { LoadingRow() }
                        } else {
                            val filtered = apps.filter { q.isEmpty() || it.label.contains(q, true) || it.packageName.contains(q, true) }
                            items(filtered, key = { it.packageName }) { app ->
                                PickerRow(
                                    title = app.label,
                                    query = q,
                                    subtitle = app.packageName,
                                    leading = { AppIcon(app.packageName) },
                                    onClick = { pick(ButtonTarget.App(app.packageName)) },
                                )
                            }
                        }
                    }
                    else -> {
                        val activities = state.activities
                        if (activities == null) {
                            item(key = "loading") { LoadingRow() }
                        } else {
                            val filtered = activities.filter {
                                q.isEmpty() || it.label.contains(q, true) || it.appLabel.contains(q, true) || it.className.contains(q, true)
                            }
                            items(filtered, key = { it.key }) { activity ->
                                PickerRow(
                                    title = activity.label,
                                    query = q,
                                    subtitle = "${activity.appLabel} · ${activity.shortClassName}",
                                    leading = { AppIcon(activity.packageName) },
                                    onClick = { pick(ButtonTarget.Activity(activity.packageName, activity.className)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(Modifier.fillMaxWidth().padding(AbleSpacing.lg), horizontalArrangement = Arrangement.Center) { LoadingIndicator() }
}

@Composable
private fun PickerRow(
    title: String,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    subtitle: String? = null,
    query: String = "",
) {
    val shape = MaterialTheme.shapes.medium
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .focusRing(shape),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = leading,
        headlineContent = { Text(highlightMatches(title, query)) },
        supportingContent = subtitle?.let { { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1) } },
    )
}
