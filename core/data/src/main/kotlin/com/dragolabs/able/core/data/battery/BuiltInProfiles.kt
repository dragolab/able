package com.dragolabs.able.core.data.battery

import com.dragolabs.able.core.database.UserPreferences
import com.dragolabs.able.core.model.AppInterventionLevel
import com.dragolabs.able.core.model.BatteryProfile
import com.dragolabs.able.core.model.ProfileColor
import com.dragolabs.able.core.model.ProfileIcon
import com.dragolabs.able.core.model.ProfileTrigger
import com.dragolabs.able.core.model.ProfileType

/**
 * The five built-in profiles (BAT-001) and their seeding from the pre-v6 preferences, so users
 * who configured thresholds, the Notte schedule/delay, trigger switches or per-app rules keep
 * them. Pure functions, JVM-testable.
 */
object BuiltInProfiles {

    fun idFor(type: ProfileType): String = "builtin_" + type.name.lowercase()

    /** Factory default ("Reset to default"): every trigger off (ADR-005), no app rules. */
    fun defaultFor(type: ProfileType): BatteryProfile {
        val base = BatteryProfile(id = idFor(type), builtInType = type, sortOrder = type.ordinal)
        return when (type) {
            ProfileType.NORMALE -> base.copy(iconKey = ProfileIcon.BATTERY.key, colorKey = ProfileColor.BLUE.key)
            ProfileType.NOTTE -> base.copy(
                iconKey = ProfileIcon.NIGHT.key,
                colorKey = ProfileColor.INDIGO.key,
                triggers = listOf(ProfileTrigger.Schedule(), ProfileTrigger.ScreenOffFor()),
            )
            ProfileType.RISPARMIO -> base.copy(
                iconKey = ProfileIcon.SAVER.key,
                colorKey = ProfileColor.ORANGE.key,
                triggers = listOf(ProfileTrigger.BatteryBelow(activatePercent = 25, resetPercent = 35)),
                offerBatterySaver = true,
            )
            ProfileType.EMERGENZA -> base.copy(
                iconKey = ProfileIcon.ALERT.key,
                colorKey = ProfileColor.RED.key,
                triggers = listOf(ProfileTrigger.BatteryBelow(activatePercent = 10, resetPercent = 20)),
                offerBatterySaver = true,
            )
            ProfileType.PAUSA_APP -> base.copy(iconKey = ProfileIcon.PAUSE.key, colorKey = ProfileColor.PURPLE.key)
        }
    }

    fun defaults(): List<BatteryProfile> = ProfileType.entries.map(::defaultFor)

    /** Built-in profiles carrying the user's pre-v6 settings. */
    fun fromLegacy(prefs: UserPreferences): List<BatteryProfile> = ProfileType.entries.map { type ->
        val base = defaultFor(type)
        val rules = prefs.appBatteryRules.mapNotNull { (pkg, byProfile) ->
            byProfile[type]?.takeIf { it != AppInterventionLevel.OSSERVA }?.let { pkg to it }
        }.toMap()
        val triggers = when (type) {
            ProfileType.NOTTE -> {
                val defaults = ProfileTrigger.Schedule()
                listOf(
                    ProfileTrigger.Schedule(
                        startMinutes = minutesOfDay(prefs.notteStartTime) ?: defaults.startMinutes,
                        endMinutes = minutesOfDay(prefs.notteEndTime) ?: defaults.endMinutes,
                        enabled = prefs.notteScheduleEnabled,
                    ),
                    ProfileTrigger.ScreenOffFor(
                        minutes = prefs.screenOffDelayMinutes.coerceIn(
                            ProfileValidation.MIN_SCREEN_OFF_MINUTES,
                            ProfileValidation.MAX_SCREEN_OFF_MINUTES,
                        ),
                        enabled = prefs.notteScreenOffEnabled,
                    ),
                )
            }
            ProfileType.RISPARMIO -> listOf(
                legacyBattery(prefs.savingsTriggerPercent, prefs.savingsResetPercent, 25, 35, prefs.savingsTriggerEnabled),
            )
            ProfileType.EMERGENZA -> listOf(
                legacyBattery(prefs.emergencyTriggerPercent, prefs.emergencyResetPercent, 10, 20, prefs.emergencyTriggerEnabled),
            )
            ProfileType.NORMALE, ProfileType.PAUSA_APP -> base.triggers
        }
        base.copy(
            triggers = triggers,
            appRules = rules,
            // The old single selection becomes a manual activation (Normale was the implicit default).
            manualActive = type != ProfileType.NORMALE && prefs.selectedBatteryProfile == type,
        )
    }

    private fun legacyBattery(on: Int, off: Int, defaultOn: Int, defaultOff: Int, enabled: Boolean): ProfileTrigger.BatteryBelow {
        val valid = on in 1..99 && off in (on + 1)..100
        return ProfileTrigger.BatteryBelow(
            activatePercent = if (valid) on else defaultOn,
            resetPercent = if (valid) off else defaultOff,
            onlyUnplugged = true,
            enabled = enabled,
        )
    }

    private val timePattern = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$")

    fun minutesOfDay(time: String): Int? {
        val match = timePattern.matchEntire(time) ?: return null
        return match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()
    }
}
