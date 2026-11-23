package com.dragolabs.able.core.data.battery

import com.dragolabs.able.core.database.UserPreferences
import com.dragolabs.able.core.model.AppInterventionLevel.ARRESTA_QUANDO_ESCO
import com.dragolabs.able.core.model.AppInterventionLevel.LIMITA_BACKGROUND
import com.dragolabs.able.core.model.AppInterventionLevel.OSSERVA
import com.dragolabs.able.core.model.BatteryProfile
import com.dragolabs.able.core.model.ProfileTrigger
import com.dragolabs.able.core.model.ProfileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileEngineTest {

    private fun inputs(
        level: Int? = 80,
        power: Boolean = false,
        minute: Int = 12 * 60,
        day: Int = 3,
        screenOffMs: Long? = null,
        paused: Boolean = false,
    ) = ProfileInputs(level, power, minute, day, screenOffMs, paused)

    private fun profile(id: String, vararg triggers: ProfileTrigger, manual: Boolean = false, enabled: Boolean = true) =
        BatteryProfile(id = id, name = id, triggers = triggers.toList(), manualActive = manual, enabled = enabled)

    @Test
    fun freshDefaultsAreAllInactive() {
        val defaults = BuiltInProfiles.defaults()
        assertTrue(defaults.none { it.hasEnabledTrigger || it.manualActive })
        val result = ProfileEngine.evaluate(defaults, inputs(level = 3, power = false, minute = 60, screenOffMs = 10 * HOUR))
        assertTrue(result.active.isEmpty())
        // Seeding from untouched preferences gives the same result (ADR-005).
        assertEquals(defaults, BuiltInProfiles.fromLegacy(UserPreferences()))
    }

    @Test
    fun severalProfilesCanBeActiveTogether() {
        val night = profile("night", ProfileTrigger.Schedule(startMinutes = 22 * 60, endMinutes = 6 * 60, enabled = true))
        val saver = profile("saver", ProfileTrigger.BatteryBelow(25, 35, enabled = true))
        val manual = profile("pause", manual = true)
        val idle = profile("idle", ProfileTrigger.Charging(enabled = true))
        val result = ProfileEngine.evaluate(listOf(night, saver, manual, idle), inputs(level = 20, minute = 23 * 60))
        assertEquals(listOf("night", "saver", "pause"), result.active.map { it.profile.id })
        assertTrue(result.active.single { it.profile.id == "pause" }.manual)
    }

    @Test
    fun disabledProfileOrTriggerNeverActivates() {
        val off = profile("off", ProfileTrigger.BatteryBelow(25, 35, enabled = true), manual = true, enabled = false)
        val triggerOff = profile("t", ProfileTrigger.BatteryBelow(25, 35, enabled = false))
        assertTrue(ProfileEngine.evaluate(listOf(off, triggerOff), inputs(level = 5)).active.isEmpty())
    }

    @Test
    fun batteryTriggerUsesHysteresis() {
        val saver = profile("saver", ProfileTrigger.BatteryBelow(25, 35, enabled = true))
        var result = ProfileEngine.evaluate(listOf(saver), inputs(level = 30))
        assertTrue("between thresholds without latch", result.active.isEmpty())
        result = ProfileEngine.evaluate(listOf(saver), inputs(level = 24), previousLatches = result.latches)
        assertEquals(1, result.active.size)
        result = ProfileEngine.evaluate(listOf(saver), inputs(level = 35), previousLatches = result.latches)
        assertEquals("stays until above reset", 1, result.active.size)
        result = ProfileEngine.evaluate(listOf(saver), inputs(level = 36), previousLatches = result.latches)
        assertTrue(result.active.isEmpty())
        assertTrue(result.latches.isEmpty())
    }

    @Test
    fun onlyUnpluggedClearsOnPowerOtherwiseKeeps() {
        val unplugged = profile("u", ProfileTrigger.BatteryBelow(25, 35, onlyUnplugged = true, enabled = true))
        val any = profile("a", ProfileTrigger.BatteryBelow(25, 35, onlyUnplugged = false, enabled = true))
        val latched = ProfileEngine.evaluate(listOf(unplugged, any), inputs(level = 20)).latches
        val charging = ProfileEngine.evaluate(listOf(unplugged, any), inputs(level = 20, power = true), previousLatches = latched)
        assertEquals(listOf("a"), charging.active.map { it.profile.id })
        // Unplugging again at 30%: the cleared trigger does not resume between thresholds.
        val after = ProfileEngine.evaluate(listOf(unplugged, any), inputs(level = 30), previousLatches = charging.latches)
        assertEquals(listOf("a"), after.active.map { it.profile.id })
    }

    @Test
    fun chargingTriggerFollowsExternalPower() {
        val p = profile("c", ProfileTrigger.Charging(enabled = true))
        assertTrue(ProfileEngine.evaluate(listOf(p), inputs(power = false)).active.isEmpty())
        assertEquals(1, ProfileEngine.evaluate(listOf(p), inputs(power = true)).active.size)
    }

    @Test
    fun scheduleAcrossMidnightBelongsToStartDay() {
        // Monday and Friday nights, 22:00-06:00.
        val s = ProfileTrigger.Schedule(22 * 60, 6 * 60, days = setOf(1, 5), enabled = true)
        assertTrue(ProfileEngine.inSchedule(s, 23 * 60, 1)) // Monday 23:00
        assertTrue(ProfileEngine.inSchedule(s, 3 * 60, 2)) // Tuesday 03:00, started Monday
        assertFalse(ProfileEngine.inSchedule(s, 23 * 60, 2)) // Tuesday 23:00
        assertFalse(ProfileEngine.inSchedule(s, 3 * 60, 1)) // Monday 03:00 started Sunday
        assertTrue(ProfileEngine.inSchedule(s, 5 * 60 + 59, 6)) // Saturday early, started Friday
        assertFalse(ProfileEngine.inSchedule(s, 6 * 60, 6)) // end exclusive
        assertFalse(ProfileEngine.inSchedule(s, 12 * 60, 1))
    }

    @Test
    fun sameDayScheduleAndEdgeCases() {
        val work = ProfileTrigger.Schedule(9 * 60, 17 * 60, days = (1..5).toSet(), enabled = true)
        assertTrue(ProfileEngine.inSchedule(work, 9 * 60, 3))
        assertFalse(ProfileEngine.inSchedule(work, 17 * 60, 3))
        assertFalse(ProfileEngine.inSchedule(work, 10 * 60, 6))
        assertFalse(ProfileEngine.inSchedule(work.copy(endMinutes = 9 * 60), 9 * 60, 3))
        assertFalse(ProfileEngine.inSchedule(work.copy(days = emptySet()), 10 * 60, 3))
    }

    @Test
    fun screenOffNeedsContinuousDelay() {
        val p = profile("n", ProfileTrigger.ScreenOffFor(20, enabled = true))
        assertTrue(ProfileEngine.evaluate(listOf(p), inputs(screenOffMs = null)).active.isEmpty())
        assertTrue(ProfileEngine.evaluate(listOf(p), inputs(screenOffMs = 19 * MINUTE)).active.isEmpty())
        assertEquals(1, ProfileEngine.evaluate(listOf(p), inputs(screenOffMs = 20 * MINUTE)).active.size)
    }

    @Test
    fun pauseIgnoresTriggersButKeepsManual() {
        val auto = profile("auto", ProfileTrigger.Charging(enabled = true))
        val manual = profile("manual", manual = true)
        val result = ProfileEngine.evaluate(listOf(auto, manual), inputs(power = true, paused = true))
        assertEquals(listOf("manual"), result.active.map { it.profile.id })
        assertTrue(result.automationPaused)
    }

    @Test
    fun mostRestrictiveWinsAndProtectedExcluded() {
        val a = BatteryProfile(id = "a", appRules = mapOf("mail" to LIMITA_BACKGROUND, "game" to ARRESTA_QUANDO_ESCO, "alarm" to ARRESTA_QUANDO_ESCO))
        val b = BatteryProfile(id = "b", appRules = mapOf("mail" to ARRESTA_QUANDO_ESCO, "game" to ARRESTA_QUANDO_ESCO, "chat" to OSSERVA))
        val c = BatteryProfile(id = "c", appRules = mapOf("mail" to LIMITA_BACKGROUND))
        val actions = ProfileEngine.resolveAppActions(listOf(a, b, c), protectedPackages = setOf("alarm"))
        assertEquals(ARRESTA_QUANDO_ESCO, actions.getValue("mail").level)
        assertEquals(listOf("b"), actions.getValue("mail").sourceProfileIds)
        assertEquals(listOf("a", "b"), actions.getValue("game").sourceProfileIds)
        assertNull(actions["alarm"])
        assertNull(actions["chat"])
        assertNull(ProfileEngine.effectiveFor("mail", listOf(a, b), setOf("mail")))
    }

    @Test
    fun evaluationResolvesActionsOnlyFromActiveProfiles() {
        val active = BatteryProfile(id = "on", manualActive = true, appRules = mapOf("x" to LIMITA_BACKGROUND))
        val inactive = BatteryProfile(id = "off", appRules = mapOf("x" to ARRESTA_QUANDO_ESCO))
        val result = ProfileEngine.evaluate(listOf(active, inactive), inputs())
        assertEquals(LIMITA_BACKGROUND, result.appActions.getValue("x").level)
    }

    @Test
    fun legacySeedingKeepsUserSettings() {
        val prefs = UserPreferences(
            selectedBatteryProfile = ProfileType.NOTTE,
            notteScheduleEnabled = true,
            notteScreenOffEnabled = false,
            notteStartTime = "23:15",
            notteEndTime = "06:45",
            screenOffDelayMinutes = 45,
            savingsTriggerEnabled = true,
            savingsTriggerPercent = 30,
            savingsResetPercent = 40,
            emergencyTriggerEnabled = false,
            emergencyTriggerPercent = 8,
            emergencyResetPercent = 18,
            appBatteryRules = mapOf(
                "mail" to mapOf(ProfileType.NOTTE to LIMITA_BACKGROUND, ProfileType.EMERGENZA to ARRESTA_QUANDO_ESCO),
                "chat" to mapOf(ProfileType.NOTTE to OSSERVA),
            ),
        )
        val seeded = BuiltInProfiles.fromLegacy(prefs).associateBy { it.builtInType }
        assertEquals(ProfileType.entries.toSet(), seeded.keys)
        val night = seeded.getValue(ProfileType.NOTTE)
        assertTrue(night.manualActive)
        assertEquals(
            listOf(
                ProfileTrigger.Schedule(23 * 60 + 15, 6 * 60 + 45, enabled = true),
                ProfileTrigger.ScreenOffFor(45, enabled = false),
            ),
            night.triggers,
        )
        assertEquals(mapOf("mail" to LIMITA_BACKGROUND), night.appRules)
        assertEquals(listOf(ProfileTrigger.BatteryBelow(30, 40, true, enabled = true)), seeded.getValue(ProfileType.RISPARMIO).triggers)
        assertEquals(listOf(ProfileTrigger.BatteryBelow(8, 18, true, enabled = false)), seeded.getValue(ProfileType.EMERGENZA).triggers)
        assertEquals(mapOf("mail" to ARRESTA_QUANDO_ESCO), seeded.getValue(ProfileType.EMERGENZA).appRules)
        assertFalse(seeded.getValue(ProfileType.NORMALE).manualActive)
        assertTrue(seeded.getValue(ProfileType.RISPARMIO).offerBatterySaver)
    }

    @Test
    fun legacySeedingRepairsInvalidValues() {
        val prefs = UserPreferences(notteStartTime = "bad", savingsTriggerPercent = 40, savingsResetPercent = 30, screenOffDelayMinutes = 0)
        val seeded = BuiltInProfiles.fromLegacy(prefs).associateBy { it.builtInType }
        val schedule = seeded.getValue(ProfileType.NOTTE).triggers.first() as ProfileTrigger.Schedule
        assertEquals(30, schedule.startMinutes)
        assertEquals(ProfileTrigger.BatteryBelow(25, 35), seeded.getValue(ProfileType.RISPARMIO).triggers.single())
        assertTrue(seeded.values.all { ProfileValidation.validate(it).isEmpty() })
    }

    @Test
    fun validationFlagsInconsistentTriggers() {
        val p = BatteryProfile(
            id = "x",
            name = " ",
            triggers = listOf(
                ProfileTrigger.BatteryBelow(30, 30),
                ProfileTrigger.Schedule(60, 60),
                ProfileTrigger.Schedule(60, 120, days = emptySet()),
                ProfileTrigger.ScreenOffFor(0),
            ),
        )
        val kinds = ProfileValidation.validate(p).map { it.kind to it.triggerIndex }
        assertEquals(
            listOf(
                ProfileIssueKind.NAME_REQUIRED to null,
                ProfileIssueKind.RESET_NOT_ABOVE_ACTIVATE to 0,
                ProfileIssueKind.SCHEDULE_EMPTY to 1,
                ProfileIssueKind.NO_DAYS to 2,
                ProfileIssueKind.SCREEN_OFF_OUT_OF_RANGE to 3,
            ),
            kinds,
        )
    }

    @Test
    fun mapperRoundTripsTriggersAndDropsOsserva() {
        val p = BuiltInProfiles.defaultFor(ProfileType.NOTTE).copy(
            name = "  Sleep ",
            triggers = listOf(ProfileTrigger.Schedule(1, 2, setOf(6, 7), true), ProfileTrigger.Charging(true), ProfileTrigger.Manual()),
            appRules = mapOf("a" to OSSERVA, "b" to LIMITA_BACKGROUND),
        )
        val back = BatteryProfileMapper.toModel(BatteryProfileMapper.toEntity(p, 0))
        assertEquals(p.copy(name = "Sleep", appRules = mapOf("b" to LIMITA_BACKGROUND)), back)
        val broken = BatteryProfileMapper.toEntity(p, 0).copy(triggersJson = "{", appRulesJson = "nope")
        val recovered = BatteryProfileMapper.toModel(broken)
        assertTrue(recovered.triggers.isEmpty() && recovered.appRules.isEmpty())
    }


    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
