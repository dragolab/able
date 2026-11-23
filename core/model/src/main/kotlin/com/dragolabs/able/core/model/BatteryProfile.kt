package com.dragolabs.able.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The five built-in battery profiles (BAT-001, ADR-005). Since the 2026-11-22 owner decision
 * profiles are user-defined [BatteryProfile]s; this enum now only identifies the built-in ones
 * (labels, reset-to-default, legacy preferences and migration). [priority] is the legacy
 * single-profile resolution order and is no longer used to pick an "effective" profile.
 */
@Serializable
enum class ProfileType(val priority: Int) {
    NORMALE(1),
    NOTTE(2),
    RISPARMIO(3),
    EMERGENZA(4),
    PAUSA_APP(5)
}

/**
 * App intervention rule levels (BAT-003), declared from least to most restrictive. When several
 * active profiles assign different levels to one app, the most restrictive wins (owner decision
 * 2026-11-22): Osserva < Limita in background < Arresta quando esco.
 */
@Serializable
enum class AppInterventionLevel {
    OSSERVA,
    LIMITA_BACKGROUND,
    ARRESTA_QUANDO_ESCO;

    /** Higher is more restrictive. */
    val restrictiveness: Int get() = ordinal
}

/**
 * Configuration for the dual-trigger Notte profile (BAT-001, BAT-008).
 */
@Serializable
data class NotteTriggerConfig(
    val scheduleEnabled: Boolean = true,
    val startHour: Int = 0,
    val startMinute: Int = 30,
    val endHour: Int = 7,
    val endMinute: Int = 30,
    val screenOffDelayEnabled: Boolean = true,
    val screenOffDelayMinutes: Int = 20
)

/**
 * Per-app battery intervention rule definition (BAT-003, BAT-004).
 */
@Serializable
data class AppBatteryRule(
    val packageName: String,
    val appName: String,
    val interventionLevel: AppInterventionLevel,
    val isProtected: Boolean = false
)

/**
 * Comprehensive profile state container.
 */
@Serializable
data class BatteryProfileState(
    val activeProfile: ProfileType = ProfileType.NORMALE,
    val notteConfig: NotteTriggerConfig = NotteTriggerConfig(),
    val appRules: List<AppBatteryRule> = emptyList(),
    val isEmergencyActive: Boolean = false,
    val pausedAppPackage: String? = null
)

// -------------------------------------------------------------------------------------------------
// User-defined battery profiles (owner decision 2026-11-22: customizable, creatable from scratch,
// several active at the same time; every automatic trigger is off by default, ADR-005).

/**
 * One battery profile. Built-in profiles ([builtInType] set) can be edited and reset but not
 * deleted; custom profiles can be duplicated and deleted.
 *
 * @property name user-chosen name; blank for a built-in profile that keeps its localized label.
 * @property enabled master switch: a disabled profile is never active (neither manually nor by a trigger).
 * @property manualActive the user started the profile by hand; it stays active until stopped.
 * @property triggers automatic triggers; the profile is active while any enabled trigger is met.
 * @property appRules package name to intervention level. Osserva entries are not stored.
 * @property offerBatterySaver suggest Android Battery Saver while the profile is active (Able never turns it on).
 */
@Serializable
data class BatteryProfile(
    val id: String,
    val name: String = "",
    val iconKey: String = ProfileIcon.BATTERY.key,
    val colorKey: String = ProfileColor.BLUE.key,
    val builtInType: ProfileType? = null,
    val enabled: Boolean = true,
    val manualActive: Boolean = false,
    val triggers: List<ProfileTrigger> = emptyList(),
    val appRules: Map<String, AppInterventionLevel> = emptyMap(),
    val offerBatterySaver: Boolean = false,
    val sortOrder: Int = 0,
) {
    val builtIn: Boolean get() = builtInType != null

    /** True when at least one automatic trigger is switched on. */
    val hasEnabledTrigger: Boolean get() = triggers.any { it.enabled && it !is ProfileTrigger.Manual }

    /** App rules that change something (Osserva is the no-change default, BAT-004). */
    val restrictiveRules: Map<String, AppInterventionLevel>
        get() = appRules.filterValues { it != AppInterventionLevel.OSSERVA }
}

/**
 * Automatic activation condition of a profile. Every trigger carries its own [enabled] switch,
 * which defaults to false (ADR-005: nothing activates by itself).
 */
@Serializable
sealed interface ProfileTrigger {
    val enabled: Boolean

    fun withEnabled(value: Boolean): ProfileTrigger = when (this) {
        is BatteryBelow -> copy(enabled = value)
        is Schedule -> copy(enabled = value)
        is ScreenOffFor -> copy(enabled = value)
        is Charging -> copy(enabled = value)
        is Manual -> copy(enabled = value)
    }

    /**
     * Battery level below [activatePercent]; once met it stays met until the level rises above
     * [resetPercent] (hysteresis). With [onlyUnplugged] it is never met on external power and
     * plugging in clears it.
     */
    @Serializable
    @SerialName("battery_below")
    data class BatteryBelow(
        val activatePercent: Int = 25,
        val resetPercent: Int = 35,
        val onlyUnplugged: Boolean = true,
        override val enabled: Boolean = false,
    ) : ProfileTrigger

    /**
     * Daily window from [startMinutes] to [endMinutes] (minutes after midnight, end exclusive). A
     * window that crosses midnight belongs to the day it starts on. [days] uses ISO numbers
     * (1 = Monday … 7 = Sunday).
     */
    @Serializable
    @SerialName("schedule")
    data class Schedule(
        val startMinutes: Int = 30,
        val endMinutes: Int = 7 * 60 + 30,
        val days: Set<Int> = ALL_DAYS,
        override val enabled: Boolean = false,
    ) : ProfileTrigger

    /** The screen has been continuously off for at least [minutes] (needs the live monitor). */
    @Serializable
    @SerialName("screen_off")
    data class ScreenOffFor(
        val minutes: Int = 20,
        override val enabled: Boolean = false,
    ) : ProfileTrigger

    /** The device is on external power. */
    @Serializable
    @SerialName("charging")
    data class Charging(override val enabled: Boolean = false) : ProfileTrigger

    /**
     * Marks a profile meant to be started by hand. It is never evaluated automatically: manual
     * activation is [BatteryProfile.manualActive], available for every enabled profile.
     */
    @Serializable
    @SerialName("manual")
    data class Manual(override val enabled: Boolean = false) : ProfileTrigger

    companion object {
        val ALL_DAYS: Set<Int> = (1..7).toSet()
    }
}

/** Icon choices for a profile; [key] is what is stored. */
enum class ProfileIcon(val key: String) {
    BATTERY("battery"),
    NIGHT("night"),
    SAVER("saver"),
    ALERT("alert"),
    PAUSE("pause"),
    WORK("work"),
    HOME("home"),
    TRAVEL("travel"),
    GAME("game"),
    FOCUS("focus"),
    CHARGING("charging"),
    LEAF("leaf");

    companion object {
        fun fromKey(key: String?): ProfileIcon = entries.firstOrNull { it.key == key } ?: BATTERY
    }
}

/** Accent color choices for a profile; [key] is what is stored. */
enum class ProfileColor(val key: String) {
    BLUE("blue"),
    INDIGO("indigo"),
    GREEN("green"),
    ORANGE("orange"),
    RED("red"),
    PURPLE("purple"),
    TEAL("teal"),
    GREY("grey");

    companion object {
        fun fromKey(key: String?): ProfileColor = entries.firstOrNull { it.key == key } ?: BLUE
    }
}
