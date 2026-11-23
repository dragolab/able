package com.dragolabs.able.core.designsystem.component

import androidx.annotation.StringRes
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.designsystem.R
import com.dragolabs.able.core.model.ProfileType

/** Localized label for a battery profile (BAT-001). */
@StringRes
fun ProfileType.labelRes(): Int = when (this) {
    ProfileType.NORMALE -> R.string.profile_normale
    ProfileType.NOTTE -> R.string.profile_notte
    ProfileType.RISPARMIO -> R.string.profile_risparmio
    ProfileType.EMERGENZA -> R.string.profile_emergenza
    ProfileType.PAUSA_APP -> R.string.profile_pausa_app
}

@StringRes
fun ShizukuConnectionState.labelRes(): Int = when (this) {
    ShizukuConnectionState.READY -> R.string.shizuku_state_ready
    ShizukuConnectionState.WAITING_FOR_PERMISSION -> R.string.shizuku_state_waiting
    ShizukuConnectionState.PERMISSION_DENIED -> R.string.shizuku_state_denied
    ShizukuConnectionState.NOT_AVAILABLE -> R.string.shizuku_state_unavailable
}

fun ShizukuConnectionState.statusLevel(): StatusLevel = when (this) {
    ShizukuConnectionState.READY -> StatusLevel.SUCCESS
    ShizukuConnectionState.WAITING_FOR_PERMISSION -> StatusLevel.WARNING
    ShizukuConnectionState.PERMISSION_DENIED -> StatusLevel.CRITICAL
    ShizukuConnectionState.NOT_AVAILABLE -> StatusLevel.INFO
}
