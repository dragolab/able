package com.dragolabs.able.core.data.battery

import com.dragolabs.able.core.model.AppInterventionLevel
import com.dragolabs.able.core.model.BatteryProfile
import com.dragolabs.able.core.model.ProfileTrigger

/** Device conditions a profile evaluation depends on. */
data class ProfileInputs(
    val levelPercent: Int?,
    val onExternalPower: Boolean,
    /** Minutes after local midnight, 0..1439. */
    val minuteOfDay: Int,
    /** ISO day of week, 1 = Monday … 7 = Sunday. */
    val dayOfWeek: Int,
    /** How long the screen has been continuously off; null when on or unknown. */
    val screenOffForMs: Long? = null,
    /** "Pause all automation": automatic triggers are ignored, manual activations still apply. */
    val automationPaused: Boolean = false,
)

/** Why a profile is active (BAT-001: "the active profile and trigger are visible"). */
sealed interface ActivationReason {
    data object Manual : ActivationReason
    data class TriggerMet(val trigger: ProfileTrigger) : ActivationReason
}

data class ActiveProfile(val profile: BatteryProfile, val reasons: List<ActivationReason>) {
    val manual: Boolean get() = ActivationReason.Manual in reasons
}

/**
 * The action that applies to one app while the given profiles are active: the most restrictive
 * level among them, with every profile that assigns that level as its source.
 */
data class EffectiveAppAction(
    val packageName: String,
    val level: AppInterventionLevel,
    val sourceProfileIds: List<String>,
)

data class ProfileEvaluation(
    /** Active profiles in the user's profile order. */
    val active: List<ActiveProfile> = emptyList(),
    /** Effective restrictive actions per package (protected apps and Osserva omitted). */
    val appActions: Map<String, EffectiveAppAction> = emptyMap(),
    val automationPaused: Boolean = false,
    /** Battery triggers currently latched (hysteresis); feed back into the next evaluation. */
    val latches: Set<String> = emptySet(),
) {
    val activeIds: Set<String> get() = active.mapTo(mutableSetOf()) { it.profile.id }
}

/**
 * Pure evaluation of battery profiles (owner decision 2026-11-22): several profiles can be active
 * at once (manual ones plus every enabled profile with an enabled trigger currently met); when
 * they assign different actions to an app, the most restrictive wins and protected apps are
 * always excluded. Nothing is active by default (ADR-005).
 */
object ProfileEngine {

    fun latchKey(profileId: String, index: Int, trigger: ProfileTrigger.BatteryBelow): String =
        "$profileId#$index:${trigger.activatePercent}-${trigger.resetPercent}"

    fun evaluate(
        profiles: List<BatteryProfile>,
        inputs: ProfileInputs,
        protectedPackages: Set<String> = emptySet(),
        previousLatches: Set<String> = emptySet(),
    ): ProfileEvaluation {
        val latches = mutableSetOf<String>()
        val active = profiles.mapNotNull { profile ->
            if (!profile.enabled) return@mapNotNull null
            val reasons = mutableListOf<ActivationReason>()
            if (profile.manualActive) reasons += ActivationReason.Manual
            profile.triggers.forEachIndexed { index, trigger ->
                if (!trigger.enabled) return@forEachIndexed
                val met = when (trigger) {
                    is ProfileTrigger.BatteryBelow -> {
                        val key = latchKey(profile.id, index, trigger)
                        batteryBelowMet(trigger, inputs, key in previousLatches).also { if (it) latches += key }
                    }
                    is ProfileTrigger.Schedule -> inSchedule(trigger, inputs.minuteOfDay, inputs.dayOfWeek)
                    is ProfileTrigger.ScreenOffFor -> screenOffReached(inputs.screenOffForMs, trigger.minutes)
                    is ProfileTrigger.Charging -> inputs.onExternalPower
                    is ProfileTrigger.Manual -> false
                }
                if (met && !inputs.automationPaused) reasons += ActivationReason.TriggerMet(trigger)
            }
            if (reasons.isEmpty()) null else ActiveProfile(profile, reasons)
        }
        return ProfileEvaluation(
            active = active,
            appActions = resolveAppActions(active.map { it.profile }, protectedPackages),
            automationPaused = inputs.automationPaused,
            latches = latches,
        )
    }

    /**
     * Battery level trigger with hysteresis: met below [ProfileTrigger.BatteryBelow.activatePercent],
     * and while [wasLatched] it stays met up to [ProfileTrigger.BatteryBelow.resetPercent].
     */
    fun batteryBelowMet(trigger: ProfileTrigger.BatteryBelow, inputs: ProfileInputs, wasLatched: Boolean): Boolean {
        val level = inputs.levelPercent ?: return false
        if (trigger.onlyUnplugged && inputs.onExternalPower) return false
        return level < trigger.activatePercent || (wasLatched && level <= trigger.resetPercent)
    }

    /** True inside the window; a window crossing midnight belongs to the day it starts on. */
    fun inSchedule(schedule: ProfileTrigger.Schedule, minuteOfDay: Int, dayOfWeek: Int): Boolean {
        val start = schedule.startMinutes
        val end = schedule.endMinutes
        if (start == end || schedule.days.isEmpty()) return false
        val yesterday = if (dayOfWeek == 1) 7 else dayOfWeek - 1
        return if (start < end) {
            dayOfWeek in schedule.days && minuteOfDay >= start && minuteOfDay < end
        } else {
            (minuteOfDay >= start && dayOfWeek in schedule.days) || (minuteOfDay < end && yesterday in schedule.days)
        }
    }

    fun screenOffReached(screenOffForMs: Long?, minutes: Int): Boolean =
        screenOffForMs != null && minutes > 0 && screenOffForMs >= minutes * 60_000L

    /** Most-restrictive-wins resolution across [profiles]; protected apps and Osserva are omitted. */
    fun resolveAppActions(profiles: List<BatteryProfile>, protectedPackages: Set<String>): Map<String, EffectiveAppAction> {
        val packages = profiles.flatMapTo(linkedSetOf()) { it.restrictiveRules.keys } - protectedPackages
        return packages.mapNotNull { pkg -> effectiveFor(pkg, profiles, protectedPackages) }.associateBy { it.packageName }
    }

    /** The effective action of [packageName] under [profiles], or null when nothing changes for it. */
    fun effectiveFor(packageName: String, profiles: List<BatteryProfile>, protectedPackages: Set<String>): EffectiveAppAction? {
        if (packageName in protectedPackages) return null
        val levels = profiles.mapNotNull { p -> p.appRules[packageName]?.let { p.id to it } }
        val max = levels.maxOfOrNull { it.second.restrictiveness } ?: return null
        val level = AppInterventionLevel.entries[max]
        if (level == AppInterventionLevel.OSSERVA) return null
        return EffectiveAppAction(packageName, level, levels.filter { it.second == level }.map { it.first })
    }
}
