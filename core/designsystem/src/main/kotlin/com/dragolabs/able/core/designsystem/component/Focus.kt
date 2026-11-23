package com.dragolabs.able.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager

/**
 * On touch input, drops focus that Compose restored or assigned when a screen with a text field
 * is entered, so the keyboard opens only when the user taps the field. D-pad/keyboard users keep
 * their focus position (TV navigation).
 */
@Composable
fun ClearRestoredFocusOnTouch() {
    val focusManager = LocalFocusManager.current
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(Unit) {
        if (inputMode == InputMode.Touch) focusManager.clearFocus()
    }
}
