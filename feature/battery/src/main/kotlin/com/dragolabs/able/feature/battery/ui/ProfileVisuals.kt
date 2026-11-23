package com.dragolabs.able.feature.battery.ui

import android.content.res.Resources
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.dragolabs.able.core.data.battery.ActivationReason
import com.dragolabs.able.core.designsystem.component.labelRes
import com.dragolabs.able.core.model.AppInterventionLevel
import com.dragolabs.able.core.model.BatteryProfile
import com.dragolabs.able.core.model.ProfileColor
import com.dragolabs.able.core.model.ProfileIcon
import com.dragolabs.able.core.model.ProfileTrigger
import com.dragolabs.able.feature.battery.R
import java.util.Locale

/** Name shown for a profile: the user's name, else the localized built-in label. */
internal fun BatteryProfile.displayName(res: Resources): String =
    name.trim().ifBlank { builtInType?.let { res.getString(it.labelRes()) } ?: res.getString(R.string.battery_profile_untitled) }

@Composable
@ReadOnlyComposable
internal fun BatteryProfile.displayName(): String = displayName(LocalContext.current.resources)

internal fun ProfileIcon.vector(): ImageVector = when (this) {
    ProfileIcon.BATTERY -> Icons.Rounded.BatteryFull
    ProfileIcon.NIGHT -> Icons.Rounded.Bedtime
    ProfileIcon.SAVER -> Icons.Rounded.BatterySaver
    ProfileIcon.ALERT -> Icons.Rounded.BatteryAlert
    ProfileIcon.PAUSE -> Icons.Rounded.PauseCircle
    ProfileIcon.WORK -> Icons.Rounded.Work
    ProfileIcon.HOME -> Icons.Rounded.Home
    ProfileIcon.TRAVEL -> Icons.Rounded.Flight
    ProfileIcon.GAME -> Icons.Rounded.SportsEsports
    ProfileIcon.FOCUS -> Icons.Rounded.CenterFocusStrong
    ProfileIcon.CHARGING -> Icons.Rounded.BatteryChargingFull
    ProfileIcon.LEAF -> Icons.Rounded.Eco
}

internal fun BatteryProfile.iconVector(): ImageVector = ProfileIcon.fromKey(iconKey).vector()

internal data class ProfileTone(val container: Color, val content: Color)

/** Fixed tonal pairs per accent; chosen by the current scheme's brightness so text keeps contrast. */
@Composable
@ReadOnlyComposable
internal fun ProfileColor.tone(): ProfileTone {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val (light, night) = when (this) {
        ProfileColor.BLUE -> (0xFFD6E3FF to 0xFF001B3E) to (0xFF284777 to 0xFFD6E3FF)
        ProfileColor.INDIGO -> (0xFFE0E0FF to 0xFF14175E) to (0xFF3B3F8F to 0xFFE0E0FF)
        ProfileColor.GREEN -> (0xFFC8F0C4 to 0xFF002106) to (0xFF1F5225 to 0xFFC8F0C4)
        ProfileColor.ORANGE -> (0xFFFFDCC2 to 0xFF2E1500) to (0xFF6E3900 to 0xFFFFDCC2)
        ProfileColor.RED -> (0xFFFFDAD6 to 0xFF410002) to (0xFF8C1D18 to 0xFFFFDAD6)
        ProfileColor.PURPLE -> (0xFFF3DAFF to 0xFF2C0A3E) to (0xFF5D3A70 to 0xFFF3DAFF)
        ProfileColor.TEAL -> (0xFFB8F0E8 to 0xFF00201C) to (0xFF00504A to 0xFFB8F0E8)
        ProfileColor.GREY -> (0xFFE2E2E6 to 0xFF1A1C1E) to (0xFF45474A to 0xFFE2E2E6)
    }
    val pair = if (dark) night else light
    return ProfileTone(Color(pair.first), Color(pair.second))
}

@Composable
@ReadOnlyComposable
internal fun BatteryProfile.tone(): ProfileTone = ProfileColor.fromKey(colorKey).tone()

internal fun ProfileColor.labelRes(): Int = when (this) {
    ProfileColor.BLUE -> R.string.battery_color_blue
    ProfileColor.INDIGO -> R.string.battery_color_indigo
    ProfileColor.GREEN -> R.string.battery_color_green
    ProfileColor.ORANGE -> R.string.battery_color_orange
    ProfileColor.RED -> R.string.battery_color_red
    ProfileColor.PURPLE -> R.string.battery_color_purple
    ProfileColor.TEAL -> R.string.battery_color_teal
    ProfileColor.GREY -> R.string.battery_color_grey
}

internal fun ProfileIcon.labelRes(): Int = when (this) {
    ProfileIcon.BATTERY -> R.string.battery_icon_battery
    ProfileIcon.NIGHT -> R.string.battery_icon_night
    ProfileIcon.SAVER -> R.string.battery_icon_saver
    ProfileIcon.ALERT -> R.string.battery_icon_alert
    ProfileIcon.PAUSE -> R.string.battery_icon_pause
    ProfileIcon.WORK -> R.string.battery_icon_work
    ProfileIcon.HOME -> R.string.battery_icon_home
    ProfileIcon.TRAVEL -> R.string.battery_icon_travel
    ProfileIcon.GAME -> R.string.battery_icon_game
    ProfileIcon.FOCUS -> R.string.battery_icon_focus
    ProfileIcon.CHARGING -> R.string.battery_icon_charging
    ProfileIcon.LEAF -> R.string.battery_icon_leaf
}

internal fun AppInterventionLevel.labelRes(): Int = when (this) {
    AppInterventionLevel.OSSERVA -> R.string.battery_level_osserva
    AppInterventionLevel.LIMITA_BACKGROUND -> R.string.battery_level_limita
    AppInterventionLevel.ARRESTA_QUANDO_ESCO -> R.string.battery_level_arresta
}

internal fun AppInterventionLevel.descriptionRes(): Int = when (this) {
    AppInterventionLevel.OSSERVA -> R.string.battery_level_osserva_desc
    AppInterventionLevel.LIMITA_BACKGROUND -> R.string.battery_level_limita_desc
    AppInterventionLevel.ARRESTA_QUANDO_ESCO -> R.string.battery_level_arresta_desc
}

internal fun formatMinutes(minutes: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", (minutes / 60) % 24, minutes % 60)

/** Short one-line description of a trigger ("Battery below 25%", "00:30–07:30"). */
internal fun ProfileTrigger.summary(res: Resources): String = when (this) {
    is ProfileTrigger.BatteryBelow -> res.getString(
        if (onlyUnplugged) R.string.battery_trigger_below_summary_unplugged else R.string.battery_trigger_below_summary,
        activatePercent,
    )
    is ProfileTrigger.Schedule -> {
        val window = res.getString(R.string.battery_trigger_schedule_summary, formatMinutes(startMinutes), formatMinutes(endMinutes))
        if (days.size == 7) window else res.getString(R.string.battery_trigger_schedule_days_summary, window, days.size)
    }
    is ProfileTrigger.ScreenOffFor -> res.getString(R.string.battery_trigger_screen_off_summary, minutes)
    is ProfileTrigger.Charging -> res.getString(R.string.battery_trigger_charging_summary)
    is ProfileTrigger.Manual -> res.getString(R.string.battery_trigger_manual_summary)
}

/** Why a profile is active, for chips and notifications. */
internal fun ActivationReason.text(res: Resources): String = when (this) {
    ActivationReason.Manual -> res.getString(R.string.battery_reason_manual)
    is ActivationReason.TriggerMet -> trigger.summary(res)
}

/** Summary of a profile's enabled triggers for the profile card. */
internal fun BatteryProfile.triggerSummary(res: Resources): String {
    val enabledTriggers = triggers.filter { it.enabled && it !is ProfileTrigger.Manual }
    return if (enabledTriggers.isEmpty()) {
        res.getString(R.string.battery_profile_manual_only)
    } else {
        enabledTriggers.joinToString(" · ") { it.summary(res) }
    }
}

@Composable
@ReadOnlyComposable
internal fun levelLabel(level: AppInterventionLevel): String = stringResource(level.labelRes())
