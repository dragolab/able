package com.dragolabs.able.feature.battery.domain

import com.dragolabs.able.core.database.UserPreferences
import com.dragolabs.able.core.database.entity.BatterySessionEntity
import com.dragolabs.able.core.data.battery.BuiltInProfiles
import com.dragolabs.able.core.data.battery.ProfileEngine
import com.dragolabs.able.core.data.battery.ProfileInputs
import com.dragolabs.able.core.model.AppInterventionLevel
import com.dragolabs.able.core.model.BatteryProfile
import com.dragolabs.able.core.model.ProfileTrigger
import com.dragolabs.able.core.model.ProfileType
import com.dragolabs.able.feature.battery.data.RuleAppEntry
import com.dragolabs.able.feature.battery.editor.ProfileEditorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryDomainTest {

    @Test
    fun rateNeedsValidWindowAndChange() {
        assertNull(DrainMath.ratePerHour(5, 10 * 60 * 1000L))
        assertNull(DrainMath.ratePerHour(0, 4 * HOUR_MS))
        assertNull(DrainMath.ratePerHour(null, 4 * HOUR_MS))
        assertEquals(2f, DrainMath.ratePerHour(8, 4 * HOUR_MS)!!, 0.001f)
    }

    @Test
    fun migratedBuiltInsKeepTheirTriggersAndActivateTogether() {
        // A user who had Power saver and the Night schedule enabled before v6 (ADR-005 switches).
        val prefs = UserPreferences(
            savingsTriggerEnabled = true,
            notteScheduleEnabled = true,
            notteStartTime = "22:00",
            notteEndTime = "06:00",
            appBatteryRules = mapOf("mail" to mapOf(ProfileType.NOTTE to AppInterventionLevel.LIMITA_BACKGROUND, ProfileType.RISPARMIO to AppInterventionLevel.ARRESTA_QUANDO_ESCO)),
        )
        val profiles = BuiltInProfiles.fromLegacy(prefs)
        val inputs = ProfileInputs(levelPercent = 20, onExternalPower = false, minuteOfDay = 23 * 60, dayOfWeek = 1)
        val result = ProfileEngine.evaluate(profiles, inputs)
        assertEquals(listOf(ProfileType.NOTTE, ProfileType.RISPARMIO), result.active.map { it.profile.builtInType })
        val mail = result.appActions.getValue("mail")
        assertEquals(AppInterventionLevel.ARRESTA_QUANDO_ESCO, mail.level)
        assertEquals(listOf(BuiltInProfiles.idFor(ProfileType.RISPARMIO)), mail.sourceProfileIds)
    }

    @Test
    fun untouchedInstallHasNothingActive() {
        val profiles = BuiltInProfiles.fromLegacy(UserPreferences())
        val inputs = ProfileInputs(levelPercent = 4, onExternalPower = false, minuteOfDay = 60, dayOfWeek = 3, screenOffForMs = 5 * HOUR_MS)
        assertTrue(ProfileEngine.evaluate(profiles, inputs).active.isEmpty())
    }

    @Test
    fun editorStateTracksDirtyValidityAndSearch() {
        val original = BatteryProfile(id = "custom_1", name = "Work", appRules = mapOf("b.mail" to AppInterventionLevel.LIMITA_BACKGROUND))
        val apps = listOf(RuleAppEntry("a.chat", "Chat"), RuleAppEntry("b.mail", "Mail"), RuleAppEntry("c.maps", "Maps"))
        var state = ProfileEditorUiState(loading = false, original = original, draft = original, apps = apps)
        assertFalse(state.dirty)
        assertFalse(state.canSave)
        assertEquals("rules first", "b.mail", state.visibleApps.first().packageName)

        state = state.copy(draft = original.copy(name = " "))
        assertTrue(state.dirty)
        assertFalse("blank custom name is invalid", state.canSave)

        state = state.copy(draft = original.copy(triggers = listOf(ProfileTrigger.BatteryBelow(30, 20))))
        assertFalse("reset must be above activate", state.canSave)

        state = state.copy(draft = original.copy(name = "Office"))
        assertTrue(state.canSave)

        assertEquals(listOf("c.maps"), state.copy(query = "map").visibleApps.map { it.packageName })
        assertEquals(listOf("b.mail"), state.copy(onlyWithRules = true).visibleApps.map { it.packageName })
    }

    private fun idleSession(id: Long, endOffsetDays: Long, startLevel: Int, drop: Int, now: Long): BatterySessionEntity {
        val end = now - endOffsetDays * IdleBaselineCalculator.DAY_MS
        val duration = 6 * HOUR_MS
        return BatterySessionEntity(
            id = id,
            startTimeEpochMs = end - duration,
            endTimeEpochMs = end,
            isCharging = false,
            startLevelPercent = startLevel,
            endLevelPercent = startLevel - drop,
            avgTemperatureCelsius = 0f,
            maxTemperatureCelsius = 0f,
            avgCurrentMilliAmperes = null,
            screenOnDurationMs = 0,
            screenOffDurationMs = duration,
            deepSleepDurationMs = 0,
            hasThermalAlert = false,
        )
    }

    @Test
    fun baselineCollectsUntilFiveValidSessions() {
        val now = 100 * IdleBaselineCalculator.DAY_MS
        val sessions = (1L..4L).map { idleSession(it, it, 80, 6, now) }
        assertEquals(BaselineStatus.COLLECTING, IdleBaselineCalculator.calculate(sessions, now).status)
    }

    @Test
    fun baselineEstablishedAndFlagsHigherLatest() {
        val now = 100 * IdleBaselineCalculator.DAY_MS
        val older = (2L..6L).map { idleSession(it, it, 80, 6, now) }
        val latest = idleSession(1, 0, 80, 18, now)
        val baseline = IdleBaselineCalculator.calculate(older + latest, now)
        assertEquals(BaselineStatus.ESTABLISHED, baseline.status)
        assertEquals(1L, baseline.latestSessionId)
        assertTrue(baseline.latestAboveRange)
    }
}
