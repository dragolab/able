package com.dragolabs.able.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.dragolabs.able.R
import com.dragolabs.able.core.designsystem.component.AbleButton
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.theme.AbleSpacing

/**
 * "What's new" dialog shown once after Able is updated (UPD-002): the highlights of the
 * installed version from `R.array.whats_new_items`, plus a link to the full release notes.
 */
@Composable
fun WhatsNewDialog(
    versionName: String,
    onDismiss: () -> Unit,
    onOpenReleaseNotes: () -> Unit,
) {
    val items = stringArrayResource(R.array.whats_new_items)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
        title = { Text(stringResource(R.string.whats_new_title, versionName)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
            ) {
                items.forEach { item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                        Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Text(item, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { AbleButton(onClick = onDismiss) { Text(stringResource(R.string.whats_new_ok)) } },
        dismissButton = { AbleTextButton(onClick = onOpenReleaseNotes) { Text(stringResource(R.string.whats_new_full_notes)) } },
    )
}

/**
 * Whether to show the dialog: only after an update, never on a fresh install (onboarding not
 * done yet) and never twice for the same version.
 */
fun shouldShowWhatsNew(onboardingCompleted: Boolean, seenVersionCode: Int, currentVersionCode: Int): Boolean =
    onboardingCompleted && seenVersionCode < currentVersionCode
