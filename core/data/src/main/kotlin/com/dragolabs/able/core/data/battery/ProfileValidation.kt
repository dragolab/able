package com.dragolabs.able.core.data.battery

import com.dragolabs.able.core.model.BatteryProfile
import com.dragolabs.able.core.model.ProfileTrigger

enum class ProfileIssueKind {
    NAME_REQUIRED,
    PERCENT_OUT_OF_RANGE,
    RESET_NOT_ABOVE_ACTIVATE,
    SCHEDULE_EMPTY,
    NO_DAYS,
    SCREEN_OFF_OUT_OF_RANGE,
}

/** A validation problem; [triggerIndex] is null for profile-level issues. */
data class ProfileIssue(val kind: ProfileIssueKind, val triggerIndex: Int? = null)

object ProfileValidation {
    const val MIN_SCREEN_OFF_MINUTES = 1
    const val MAX_SCREEN_OFF_MINUTES = 240

    fun validate(profile: BatteryProfile): List<ProfileIssue> = buildList {
        if (!profile.builtIn && profile.name.isBlank()) add(ProfileIssue(ProfileIssueKind.NAME_REQUIRED))
        profile.triggers.forEachIndexed { index, trigger ->
            when (trigger) {
                is ProfileTrigger.BatteryBelow -> {
                    if (trigger.activatePercent !in 1..99 || trigger.resetPercent !in 2..100) {
                        add(ProfileIssue(ProfileIssueKind.PERCENT_OUT_OF_RANGE, index))
                    } else if (trigger.resetPercent <= trigger.activatePercent) {
                        add(ProfileIssue(ProfileIssueKind.RESET_NOT_ABOVE_ACTIVATE, index))
                    }
                }
                is ProfileTrigger.Schedule -> {
                    if (trigger.startMinutes == trigger.endMinutes ||
                        trigger.startMinutes !in 0 until DAY_MINUTES || trigger.endMinutes !in 0 until DAY_MINUTES
                    ) {
                        add(ProfileIssue(ProfileIssueKind.SCHEDULE_EMPTY, index))
                    }
                    if (trigger.days.none { it in 1..7 }) add(ProfileIssue(ProfileIssueKind.NO_DAYS, index))
                }
                is ProfileTrigger.ScreenOffFor ->
                    if (trigger.minutes !in MIN_SCREEN_OFF_MINUTES..MAX_SCREEN_OFF_MINUTES) {
                        add(ProfileIssue(ProfileIssueKind.SCREEN_OFF_OUT_OF_RANGE, index))
                    }
                is ProfileTrigger.Charging, is ProfileTrigger.Manual -> Unit
            }
        }
    }

    private const val DAY_MINUTES = 24 * 60
}
