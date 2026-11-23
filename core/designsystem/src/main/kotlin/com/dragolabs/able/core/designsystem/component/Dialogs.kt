package com.dragolabs.able.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Modifier
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.dragolabs.able.core.designsystem.R

/**
 * Confirmation for restrictive or destructive actions. The message must state the consequence
 * and the recovery route (adaptive-ui interaction patterns).
 */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    icon: ImageVector? = null,
    dismissLabel: String = stringResource(R.string.ds_cancel),
) {
    val haptics = rememberAbleHaptics()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(it, contentDescription = null) } },
        title = { Text(title) },
        text = { ScrollableDialogText(message) },
        confirmButton = {
            Button(
                onClick = { if (destructive) haptics.reject() else haptics.confirm(); onConfirm() },
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = { haptics.click(); onDismiss() }) { Text(dismissLabel) } },
    )
}

/**
 * Dialog body text that scrolls when it is taller than the dialog allows (long "?" help and
 * explanations were cut off on small screens and with large font sizes).
 */
@Composable
fun ScrollableDialogText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.verticalScroll(rememberScrollState()))
}
