package com.dragolabs.able.feature.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.icon
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.component.statusColors
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.apps.data.AppInstallState
import com.dragolabs.able.feature.apps.data.RemovalLevel
import com.dragolabs.able.feature.apps.data.UadList

// region Labels

internal fun RemovalLevel?.labelRes(): Int = when (this) {
    RemovalLevel.RECOMMENDED -> R.string.apps_removal_recommended
    RemovalLevel.ADVANCED -> R.string.apps_removal_advanced
    RemovalLevel.EXPERT -> R.string.apps_removal_expert
    RemovalLevel.UNSAFE -> R.string.apps_removal_unsafe
    null -> R.string.apps_removal_not_listed
}

internal fun RemovalLevel?.descriptionRes(): Int = when (this) {
    RemovalLevel.RECOMMENDED -> R.string.apps_removal_recommended_desc
    RemovalLevel.ADVANCED -> R.string.apps_removal_advanced_desc
    RemovalLevel.EXPERT -> R.string.apps_removal_expert_desc
    RemovalLevel.UNSAFE -> R.string.apps_removal_unsafe_desc
    null -> R.string.apps_removal_not_listed_desc
}

/** Status level per removal level; the pill always pairs it with an icon and a label. */
internal fun RemovalLevel?.statusLevel(): StatusLevel = when (this) {
    RemovalLevel.RECOMMENDED -> StatusLevel.SUCCESS
    RemovalLevel.ADVANCED -> StatusLevel.INFO
    RemovalLevel.EXPERT -> StatusLevel.WARNING
    RemovalLevel.UNSAFE -> StatusLevel.CRITICAL
    null -> StatusLevel.INFO
}

internal fun RemovalFilter.labelRes(): Int = when (this) {
    RemovalFilter.RECOMMENDED -> R.string.apps_removal_recommended
    RemovalFilter.ADVANCED -> R.string.apps_removal_advanced
    RemovalFilter.EXPERT -> R.string.apps_removal_expert
    RemovalFilter.UNSAFE -> R.string.apps_removal_unsafe
    RemovalFilter.NOT_LISTED -> R.string.apps_removal_not_listed
}

internal fun RemovalFilter.level(): RemovalLevel? = when (this) {
    RemovalFilter.RECOMMENDED -> RemovalLevel.RECOMMENDED
    RemovalFilter.ADVANCED -> RemovalLevel.ADVANCED
    RemovalFilter.EXPERT -> RemovalLevel.EXPERT
    RemovalFilter.UNSAFE -> RemovalLevel.UNSAFE
    RemovalFilter.NOT_LISTED -> null
}

internal fun UadList.labelRes(): Int = when (this) {
    UadList.OEM -> R.string.apps_list_oem
    UadList.MISC -> R.string.apps_list_misc
    UadList.AOSP -> R.string.apps_list_aosp
    UadList.CARRIER -> R.string.apps_list_carrier
    UadList.GOOGLE -> R.string.apps_list_google
}

internal fun AppStateFilter.labelRes(): Int = when (this) {
    AppStateFilter.ALL -> R.string.apps_state_all
    AppStateFilter.INSTALLED -> R.string.apps_state_installed
    AppStateFilter.DISABLED -> R.string.apps_state_disabled
    AppStateFilter.UNINSTALLED -> R.string.apps_state_uninstalled
    AppStateFilter.ARCHIVED_BY_SYSTEM -> R.string.apps_state_archived_system
    AppStateFilter.ABLE_ARCHIVE -> R.string.apps_state_able_archive
}

internal fun AppTypeFilter.labelRes(): Int = when (this) {
    AppTypeFilter.ALL -> R.string.apps_filter_all
    AppTypeFilter.USER -> R.string.apps_filter_user
    AppTypeFilter.SYSTEM -> R.string.apps_filter_system
}

/** Pill label for non-default install states; null for installed and enabled. */
internal fun AppInstallState.badgeRes(): Int? = when (this) {
    AppInstallState.INSTALLED -> null
    AppInstallState.DISABLED -> R.string.apps_badge_disabled
    AppInstallState.UNINSTALLED_FOR_USER -> R.string.apps_badge_uninstalled
    AppInstallState.ARCHIVED_BY_SYSTEM -> R.string.apps_badge_archived_system
    AppInstallState.NOT_FOUND -> R.string.apps_state_not_found
}

internal fun AppInstallState.badgeIcon(): ImageVector = when (this) {
    AppInstallState.DISABLED -> Icons.Rounded.Block
    AppInstallState.ARCHIVED_BY_SYSTEM -> Icons.Rounded.Archive
    else -> Icons.Rounded.RemoveCircleOutline
}

// endregion

// region Pills

/** Compact pill for list rows: icon + text, never color alone (NFR-ACC-002). */
@Composable
internal fun MiniPill(
    text: String,
    icon: ImageVector?,
    container: Color,
    content: Color,
    accent: Color = content,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = container, contentColor = content) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = accent)
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
internal fun RemovalPill(removal: RemovalLevel?, modifier: Modifier = Modifier) {
    val level = removal.statusLevel()
    val colors = statusColors(level)
    MiniPill(
        text = stringResource(removal.labelRes()),
        icon = level.icon(),
        container = colors.container,
        content = colors.content,
        accent = colors.accent,
        modifier = modifier,
    )
}

@Composable
internal fun ListChip(list: UadList, modifier: Modifier = Modifier) {
    MiniPill(
        text = stringResource(list.labelRes()),
        icon = null,
        container = MaterialTheme.colorScheme.surfaceContainerHighest,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
internal fun StatePill(state: AppInstallState, modifier: Modifier = Modifier) {
    val res = state.badgeRes() ?: return
    val colors = statusColors(StatusLevel.WARNING)
    MiniPill(stringResource(res), state.badgeIcon(), colors.container, colors.content, colors.accent, modifier)
}

@Composable
internal fun ProtectedPill(modifier: Modifier = Modifier) {
    val colors = statusColors(StatusLevel.SUCCESS)
    MiniPill(stringResource(R.string.apps_badge_protected), Icons.Rounded.Shield, colors.container, colors.content, colors.accent, modifier)
}

@Composable
internal fun ArchivePill(modifier: Modifier = Modifier) {
    MiniPill(
        text = stringResource(R.string.apps_badge_in_archive),
        icon = Icons.Rounded.Inventory2,
        container = MaterialTheme.colorScheme.tertiaryContainer,
        content = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier,
    )
}

// endregion

/** Section header with a trailing "?" help button (UX-002). */
@Composable
internal fun HelpSectionHeader(
    title: String,
    helpTitle: String,
    helpText: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionHeader(title, Modifier.weight(1f))
        HelpButton(title = helpTitle, text = helpText)
    }
}

private val URL_REGEX = Regex("""https?://[^\s<>"')\]]+""")

/** Turns URLs in UAD descriptions into accessible links. */
internal fun linkify(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in URL_REGEX.findAll(text)) {
        val url = match.value.trimEnd('.', ',', ';', ':', '!', '?')
        val start = match.range.first
        append(text.substring(last, start))
        withLink(
            LinkAnnotation.Url(
                url,
                TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
            ),
        ) { append(url) }
        last = start + url.length
    }
    append(text.substring(last))
}

/**
 * Destructive confirmation for disable / uninstall (APP-004). Warnings follow the removal level;
 * Expert, Unsafe and unlisted system packages require an explicit acknowledgement.
 */
@Composable
internal fun PackageOperationDialog(
    operation: PackageOperation,
    label: String,
    removal: RemovalLevel?,
    isSystem: Boolean,
    isProtected: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    uninstallPlan: UninstallPlanUi = UninstallPlanUi.Idle,
) {
    val haptics = rememberAbleHaptics()
    val context = LocalContext.current
    val needsAck = requiresAcknowledgement(removal, isSystem)
    var acknowledged by rememberSaveable(operation, label) { mutableStateOf(false) }
    var storeOnlyAccepted by rememberSaveable(operation, label) { mutableStateOf(false) }
    val uninstall = operation == PackageOperation.UNINSTALL
    val plan = uninstallPlan as? UninstallPlanUi.Ready
    // User apps: Android deletes the APK, so the backup preview must be known before confirming.
    val planPending = uninstall && !isSystem &&
        (uninstallPlan == UninstallPlanUi.Loading || uninstallPlan == UninstallPlanUi.Idle)
    val backupProblem = plan?.problem.takeIf { uninstall && !isSystem }
    val levelColors = statusColors(removal.statusLevel())

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (needsAck) Icons.Rounded.Warning else Icons.Rounded.Block, contentDescription = null) },
        title = {
            Text(
                stringResource(
                    if (uninstall) R.string.apps_uninstall_confirm_title else R.string.apps_disable_confirm_title,
                    label,
                ),
            )
        },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
            ) {
                Text(stringResource(if (uninstall) R.string.apps_uninstall_confirm_message else R.string.apps_disable_confirm_message))
                if (uninstall && !isSystem) {
                    Text(stringResource(R.string.apps_uninstall_confirm_user_app))
                    when {
                        planPending -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
                        ) {
                            com.dragolabs.able.core.designsystem.component.InlineLoading()
                            Text(stringResource(R.string.apps_backup_checking), style = MaterialTheme.typography.bodyMedium)
                        }
                        plan != null && backupProblem == null -> Text(
                            stringResource(
                                if (plan.alreadyBackedUp) R.string.apps_backup_will_reuse else R.string.apps_backup_will_create,
                                android.text.format.Formatter.formatShortFileSize(context, plan.sizeBytes),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        backupProblem != null -> {
                            val colors = statusColors(StatusLevel.CRITICAL)
                            Surface(shape = MaterialTheme.shapes.medium, color = colors.container, contentColor = colors.content) {
                                Row(Modifier.padding(AbleSpacing.md), horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = colors.accent)
                                    Text(
                                        stringResource(R.string.apps_backup_unavailable, stringResource(backupProblem.labelRes())),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .toggleable(value = storeOnlyAccepted, role = Role.Checkbox) {
                                        haptics.toggle(it)
                                        storeOnlyAccepted = it
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
                            ) {
                                Checkbox(checked = storeOnlyAccepted, onCheckedChange = null)
                                Text(stringResource(R.string.apps_backup_accept_store_only), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                Surface(shape = MaterialTheme.shapes.medium, color = levelColors.container, contentColor = levelColors.content) {
                    Row(
                        Modifier.padding(AbleSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
                    ) {
                        Icon(removal.statusLevel().icon(), contentDescription = null, tint = levelColors.accent)
                        Text(
                            stringResource(
                                R.string.apps_confirm_level,
                                stringResource(removal.labelRes()),
                                stringResource(removal.descriptionRes()),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (isProtected) Text(stringResource(R.string.apps_confirm_protected), style = MaterialTheme.typography.bodyMedium)
                if (needsAck) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(value = acknowledged, role = Role.Checkbox) {
                                haptics.toggle(it)
                                acknowledged = it
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
                    ) {
                        Checkbox(checked = acknowledged, onCheckedChange = null)
                        Text(stringResource(R.string.apps_confirm_acknowledge), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { haptics.reject(); onConfirm() },
                enabled = (!needsAck || acknowledged) && !planPending && (backupProblem == null || storeOnlyAccepted),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(if (uninstall) R.string.apps_uninstall_confirm_action else R.string.apps_disable_confirm_action))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.apps_cancel)) } },
    )
}
