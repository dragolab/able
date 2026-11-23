package com.dragolabs.able.feature.settings.backup

import com.dragolabs.able.core.data.battery.BuiltInProfiles
import com.dragolabs.able.core.model.AppInterventionLevel
import com.dragolabs.able.core.model.BatteryProfile
import com.dragolabs.able.core.model.ProfileTrigger
import com.dragolabs.able.core.model.ProfileType
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupSchema2Test {

    private val now = 1_800_000_000_000L
    private val hour = 60 * 60 * 1000L

    private val legacy = BatterySectionDto(
        savingsTriggerPercent = 30, savingsResetPercent = 40, emergencyTriggerPercent = 12, emergencyResetPercent = 22,
        notteScheduleEnabled = true, notteScreenOffEnabled = false, notteStartTime = "23:00", notteEndTime = "06:45",
        screenOffDelayMinutes = 30,
        protectedApps = listOf("com.example.keep"),
        appRules = listOf(
            AppRuleDto("com.example.game", "NOTTE", "ARRESTA_QUANDO_ESCO"),
            AppRuleDto("com.example.chat", "RISPARMIO", "LIMITA_BACKGROUND"),
        ),
    )

    private fun session(start: Long, durationMs: Long = hour, note: BatterySessionNoteDto? = null) = BatterySessionDto(
        startTimeEpochMs = start, endTimeEpochMs = start + durationMs, isCharging = false,
        startLevelPercent = 80, endLevelPercent = 70, avgTemperatureCelsius = 30f, maxTemperatureCelsius = 34f,
        avgCurrentMilliAmperes = -350f, avgVoltageMillivolts = 3_900f, screenOnDurationMs = durationMs / 2,
        screenOffDurationMs = durationMs / 2, note = note,
    )

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, bytes) ->
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
    }.toByteArray()

    private fun schema1Archive(): ByteArray {
        val content = BackupArchive.json.encodeToString(BatterySectionDto.serializer(), legacy).toByteArray()
        val manifest = BackupManifest(
            schemaVersion = 1, appVersion = "0.5.0", createdAt = "2026-10-01T10:00:00Z",
            sections = listOf(BackupSection.LEGACY_BATTERY_KEY),
            entries = listOf(
                ManifestEntry(BackupSection.LEGACY_BATTERY_PATH, BackupSection.LEGACY_BATTERY_KEY, content.size.toLong(), BackupArchive.sha256Hex(content)),
            ),
        )
        val manifestBytes = BackupArchive.json.encodeToString(BackupManifest.serializer(), manifest).toByteArray()
        return zip(BackupArchive.MANIFEST_PATH to manifestBytes, BackupSection.LEGACY_BATTERY_PATH to content)
    }

    private fun read(bytes: ByteArray) = BackupArchive.read(ByteArrayInputStream(bytes))

    // --- Schema 1 compatibility --------------------------------------------------------------

    @Test
    fun schema1BatterySectionIsReadAsBatteryProfiles() {
        val result = read(schema1Archive())
        assertTrue(result is BackupReadResult.Valid)
        val backup = (result as BackupReadResult.Valid).backup
        assertEquals(1, backup.manifest.schemaVersion)
        assertEquals(setOf(BackupSection.BATTERY_PROFILES), backup.payload.sections)
        assertEquals(legacy, backup.payload.legacyBattery)
        assertNull(backup.payload.batteryProfiles)
        assertTrue(backup.unsupported.isEmpty())
    }

    @Test
    fun legacySectionWithForeignPathIsRejected() {
        val content = "{}".toByteArray()
        val manifest = BackupManifest(
            schemaVersion = 1, appVersion = "1", createdAt = "x",
            sections = listOf(BackupSection.LEGACY_BATTERY_KEY),
            entries = listOf(ManifestEntry("other.json", BackupSection.LEGACY_BATTERY_KEY, 2, BackupArchive.sha256Hex(content))),
        )
        assertEquals(
            BackupReadResult.Invalid(BackupError.UnsafePath("other.json")),
            BackupArchive.validate(manifest, mapOf("other.json" to content)),
        )
    }

    @Test
    fun schema2ProfilesSupersedeLegacySection() {
        val legacyBytes = BackupArchive.json.encodeToString(BatterySectionDto.serializer(), legacy).toByteArray()
        val profilesBytes = BackupArchive.json.encodeToString(BatteryProfilesSectionDto.serializer(), BatteryProfilesSectionDto()).toByteArray()
        val manifest = BackupManifest(
            schemaVersion = 2, appVersion = "1", createdAt = "x",
            sections = listOf(BackupSection.LEGACY_BATTERY_KEY, BackupSection.BATTERY_PROFILES.key),
            entries = listOf(
                ManifestEntry(BackupSection.LEGACY_BATTERY_PATH, BackupSection.LEGACY_BATTERY_KEY, legacyBytes.size.toLong(), BackupArchive.sha256Hex(legacyBytes)),
                ManifestEntry(BackupSection.BATTERY_PROFILES.entryPath, BackupSection.BATTERY_PROFILES.key, profilesBytes.size.toLong(), BackupArchive.sha256Hex(profilesBytes)),
            ),
        )
        val result = BackupArchive.validate(
            manifest,
            mapOf(BackupSection.LEGACY_BATTERY_PATH to legacyBytes, BackupSection.BATTERY_PROFILES.entryPath to profilesBytes),
        )
        val backup = (result as BackupReadResult.Valid).backup
        assertNull(backup.payload.legacyBattery)
        assertNotNull(backup.payload.batteryProfiles)
        assertTrue(BackupSection.LEGACY_BATTERY_KEY in backup.unsupported)
    }

    @Test
    fun rejectsSchemaNewerThanTwo() {
        val manifest = BackupManifest(schemaVersion = 3, appVersion = "9", createdAt = "x", sections = emptyList(), entries = emptyList())
        assertEquals(BackupReadResult.Invalid(BackupError.UnsupportedSchema(3)), BackupArchive.validate(manifest, emptyMap()))
    }

    @Test
    fun legacyConversionProducesBuiltInsWithRulesAndThresholds() {
        val s = BackupSanitizer.legacyBatteryProfiles(legacy, BuiltInProfiles.defaults())
        assertTrue(s.legacy)
        assertEquals(ProfileType.entries.toSet(), s.profiles.mapNotNull { it.builtInType }.toSet())
        val notte = s.profiles.first { it.builtInType == ProfileType.NOTTE }
        assertEquals(mapOf("com.example.game" to AppInterventionLevel.ARRESTA_QUANDO_ESCO), notte.appRules)
        val schedule = notte.triggers.filterIsInstance<ProfileTrigger.Schedule>().single()
        assertEquals(23 * 60, schedule.startMinutes)
        assertTrue(schedule.enabled)
        val risparmio = s.profiles.first { it.builtInType == ProfileType.RISPARMIO }
        val below = risparmio.triggers.filterIsInstance<ProfileTrigger.BatteryBelow>().single()
        assertEquals(30, below.activatePercent)
        assertEquals(40, below.resetPercent)
        assertEquals(setOf("com.example.keep"), s.protectedApps)
        assertTrue(s.profiles.none { it.manualActive })
    }

    @Test
    fun legacyConversionWithInvalidThresholdsKeepsCurrentTriggers() {
        val current = BuiltInProfiles.defaults().map {
            if (it.builtInType == ProfileType.EMERGENZA) {
                it.copy(triggers = listOf(ProfileTrigger.BatteryBelow(activatePercent = 15, resetPercent = 25, enabled = true)))
            } else {
                it
            }
        }
        val s = BackupSanitizer.legacyBatteryProfiles(legacy.copy(savingsTriggerPercent = 5), current)
        val emergenza = s.profiles.first { it.builtInType == ProfileType.EMERGENZA }
        assertEquals(current.first { it.builtInType == ProfileType.EMERGENZA }.triggers, emergenza.triggers)
        assertEquals(1, s.rejected)
        // Rules are still converted.
        assertEquals(1, s.profiles.first { it.builtInType == ProfileType.RISPARMIO }.appRules.size)
    }

    // --- Battery profiles --------------------------------------------------------------------

    private fun customDto(id: String, name: String = "Work", triggers: List<ProfileTrigger> = emptyList()) =
        BackupSanitizer.profileToDto(BatteryProfile(id = id, name = name, triggers = triggers, sortOrder = 10))

    @Test
    fun profilesRoundTripThroughArchive() {
        val custom = BatteryProfile(
            id = "custom_1", name = "Travel", iconKey = "travel", colorKey = "teal", enabled = true,
            triggers = listOf(ProfileTrigger.Charging(enabled = true), ProfileTrigger.Schedule(startMinutes = 60, endMinutes = 120, days = setOf(1, 2))),
            appRules = mapOf("com.example.maps" to AppInterventionLevel.LIMITA_BACKGROUND),
            sortOrder = 5,
        )
        val payload = BackupPayload(
            batteryProfiles = BatteryProfilesSectionDto(
                (BuiltInProfiles.defaults() + custom).map(BackupSanitizer::profileToDto),
                listOf("com.example.keep"),
            ),
        )
        val bytes = ByteArrayOutputStream().also { BackupArchive.write(it, payload, "1", "x") }.toByteArray()
        val backup = (read(bytes) as BackupReadResult.Valid).backup
        assertEquals(2, backup.manifest.schemaVersion)
        val s = BackupSanitizer.batteryProfiles(backup.payload.batteryProfiles!!)
        assertEquals(0, s.rejected)
        assertEquals(custom, s.profiles.first { it.id == "custom_1" })
        assertEquals(BuiltInProfiles.defaults(), s.profiles.filter { it.builtIn })
        assertTrue(backup.manifest.excluded.contains(BackupExclusion.BATTERY_HISTORY.key))
    }

    @Test
    fun profileSanitizerDropsInvalidProfilesTriggersAndRules() {
        val unknownTrigger = buildJsonObject {
            put("type", "wifi_connected")
            put("enabled", JsonPrimitive(true))
        }
        val badBattery = BackupSanitizer.encodeTrigger(ProfileTrigger.BatteryBelow(activatePercent = 40, resetPercent = 30))
        val good = customDto("custom_ok", triggers = listOf(ProfileTrigger.Charging(enabled = true)))
        val dto = BatteryProfilesSectionDto(
            profiles = listOf(
                good.copy(
                    triggers = good.triggers + unknownTrigger + badBattery,
                    appRules = listOf(
                        ProfileAppRuleDto("com.example.a", "LIMITA_BACKGROUND"),
                        ProfileAppRuleDto("com.example.b", "OSSERVA"),
                        ProfileAppRuleDto("bad pkg", "LIMITA_BACKGROUND"),
                        ProfileAppRuleDto("com.example.c", "NUKE"),
                    ),
                ),
                customDto("custom_ok"), // duplicate id
                customDto("builtin_fake"), // reserved prefix
                customDto("custom_blank", name = " "), // name required
                BackupSanitizer.profileToDto(BuiltInProfiles.defaultFor(ProfileType.NOTTE)).copy(id = "whatever"),
                BackupSanitizer.profileToDto(BuiltInProfiles.defaultFor(ProfileType.NOTTE)), // duplicate built-in
                customDto("custom_x").copy(builtInType = "TURBO"),
            ),
            protectedApps = listOf("com.example.keep", "../bad"),
        )
        val s = BackupSanitizer.batteryProfiles(dto)
        assertEquals(listOf("custom_ok", BuiltInProfiles.idFor(ProfileType.NOTTE)), s.profiles.map { it.id })
        val ok = s.profiles.first()
        assertEquals(listOf<ProfileTrigger>(ProfileTrigger.Charging(enabled = true)), ok.triggers)
        assertEquals(mapOf("com.example.a" to AppInterventionLevel.LIMITA_BACKGROUND), ok.appRules)
        assertEquals(setOf("com.example.keep"), s.protectedApps)
        // unknown trigger + bad battery trigger + 2 bad rules + 5 bad profiles + 1 bad package
        assertEquals(10, s.rejected)
    }

    @Test
    fun mergeAddsNewKeepsOrOverwritesConflictsByPolicy() {
        val localNotte = BuiltInProfiles.defaultFor(ProfileType.NOTTE).copy(manualActive = true)
        val localCustom = BatteryProfile(id = "custom_a", name = "A", sortOrder = 7)
        val local = listOf(BuiltInProfiles.defaultFor(ProfileType.NORMALE), localNotte, localCustom)
        val importedNotte = localNotte.copy(manualActive = false, appRules = mapOf("com.example.x" to AppInterventionLevel.LIMITA_BACKGROUND))
        val imported = listOf(
            BuiltInProfiles.defaultFor(ProfileType.NORMALE).copy(sortOrder = 99), // identical except order
            importedNotte,
            BatteryProfile(id = "custom_new", name = "New", sortOrder = 1),
        )

        val keep = BackupMerge.planProfiles(local, imported, replace = false, useImported = false)
        assertEquals(1, keep.conflicts)
        assertEquals(1, keep.skipped)
        assertEquals(2, keep.applied)
        assertEquals(listOf("custom_new"), keep.save.map { it.id })
        assertEquals(8, keep.save.single().sortOrder) // appended after the local profiles
        assertTrue(keep.deleteCustomIds.isEmpty())

        val use = BackupMerge.planProfiles(local, imported, replace = false, useImported = true)
        val savedNotte = use.save.first { it.builtInType == ProfileType.NOTTE }
        assertEquals(importedNotte.appRules, savedNotte.appRules)
        assertTrue(savedNotte.manualActive) // manual activation is local state, never imported
        assertEquals(3, use.applied)
    }

    @Test
    fun replaceDeletesMissingCustomsAndResetsMissingBuiltIns() {
        val editedEmergenza = BuiltInProfiles.defaultFor(ProfileType.EMERGENZA).copy(appRules = mapOf("com.example.y" to AppInterventionLevel.ARRESTA_QUANDO_ESCO))
        val local = listOf(BuiltInProfiles.defaultFor(ProfileType.NORMALE), editedEmergenza, BatteryProfile(id = "custom_old", name = "Old"))
        val imported = listOf(BuiltInProfiles.defaultFor(ProfileType.NORMALE), BatteryProfile(id = "custom_new", name = "New", sortOrder = 9))
        val plan = BackupMerge.planProfiles(local, imported, replace = true, useImported = true)
        assertEquals(listOf("custom_old"), plan.deleteCustomIds)
        assertEquals(listOf(BuiltInProfiles.defaultFor(ProfileType.EMERGENZA)), plan.resetBuiltIns)
        assertEquals(listOf("custom_new"), plan.save.map { it.id })
        assertEquals(9, plan.save.single().sortOrder)
    }

    // --- Battery history ---------------------------------------------------------------------

    @Test
    fun historySanitizerValidatesRangesTimestampsAndDuplicates() {
        val base = now - 10 * BackupMerge.DAY_MS
        val dto = BatteryHistorySectionDto(
            listOf(
                session(base, note = BatterySessionNoteDto("  Trip  ", listOf("travel", "Bad Tag", "travel"), base)),
                session(base), // duplicate start/end
                session(base + hour).copy(startLevelPercent = 120),
                session(base + 2 * hour).copy(avgTemperatureCelsius = Float.NaN),
                session(now + 3 * BackupMerge.DAY_MS), // ends in the future
                session(1_000L), // before 2008
                session(base + 3 * hour, durationMs = -1),
                session(base + 4 * hour).copy(screenOnDurationMs = 10 * hour),
                session(base + 5 * hour).copy(avgVoltageMillivolts = -5f),
                session(base + 6 * hour, note = BatterySessionNoteDto("", emptyList(), base)), // empty note dropped
            ),
        )
        val s = BackupSanitizer.batteryHistory(dto, now)
        assertEquals(listOf(base, base + 6 * hour), s.sessions.map { it.startTimeEpochMs })
        assertEquals(BatterySessionNoteDto("Trip", listOf("travel"), base), s.sessions.first().note)
        assertNull(s.sessions.last().note)
        assertEquals(1, s.notes)
        assertEquals(1, s.duplicatesInFile)
        assertEquals(7, s.rejected)
    }

    @Test
    fun historyMergeAddsOnlyMissingSessionsAndNotes() {
        val a = session(now - 2 * BackupMerge.DAY_MS, note = BatterySessionNoteDto("a", emptyList(), 1))
        val b = session(now - 3 * BackupMerge.DAY_MS, note = BatterySessionNoteDto("b", emptyList(), 1))
        val c = session(now - 4 * BackupMerge.DAY_MS)
        val existing = mapOf(
            (a.startTimeEpochMs to a.endTimeEpochMs) to true, // has a note: untouched
            (b.startTimeEpochMs to b.endTimeEpochMs) to false, // no note: note added
        )
        val plan = BackupMerge.planHistory(listOf(a, b, c), existing, replace = false, retentionDays = 90, nowEpochMs = now)
        assertEquals(listOf(c), plan.insert)
        assertEquals(2, plan.duplicates)
        assertEquals(mapOf((b.startTimeEpochMs to b.endTimeEpochMs) to b.note!!), plan.notesForExisting)
        assertEquals(0, plan.expired)
    }

    @Test
    fun historyReplaceImportsEverythingWithinRetention() {
        val recent = session(now - 5 * BackupMerge.DAY_MS)
        val old = session(now - 40 * BackupMerge.DAY_MS)
        val existing = mapOf((recent.startTimeEpochMs to recent.endTimeEpochMs) to false)

        val replace30 = BackupMerge.planHistory(listOf(recent, old), existing, replace = true, retentionDays = 30, nowEpochMs = now)
        assertEquals(listOf(recent), replace30.insert)
        assertEquals(1, replace30.expired)
        assertEquals(0, replace30.duplicates)

        // 0 = keep until deleted: nothing expires.
        val keepAll = BackupMerge.planHistory(listOf(recent, old), emptyMap(), replace = false, retentionDays = 0, nowEpochMs = now)
        assertEquals(2, keepAll.insert.size)
        assertEquals(0, keepAll.expired)
    }

    @Test
    fun historyIsOptInAndRemovedFromExcludedListOnlyWhenIncluded() {
        val payload = BackupPayload(batteryHistory = BatteryHistorySectionDto(listOf(session(now - hour))))
        val bytes = ByteArrayOutputStream().also { BackupArchive.write(it, payload, "1", "x") }.toByteArray()
        val backup = (read(bytes) as BackupReadResult.Valid).backup
        assertEquals(setOf(BackupSection.BATTERY_HISTORY), backup.payload.sections)
        assertFalse(backup.manifest.excluded.contains(BackupExclusion.BATTERY_HISTORY.key))
        assertEquals(payload.batteryHistory, backup.payload.batteryHistory)
        assertTrue(BackupSection.BATTERY_HISTORY.optIn)
        assertFalse(BackupSection.BATTERY_HISTORY in BackupSection.standard)
        assertTrue(BackupArchive.encodedSize(payload.batteryHistory!!) > 0)
    }

    @Test
    fun triggerJsonRoundTrips() {
        val triggers = listOf(
            ProfileTrigger.BatteryBelow(20, 30, onlyUnplugged = false, enabled = true),
            ProfileTrigger.Schedule(60, 120, setOf(6, 7), enabled = true),
            ProfileTrigger.ScreenOffFor(15, enabled = false),
            ProfileTrigger.Charging(true),
            ProfileTrigger.Manual(false),
        )
        assertEquals(triggers, triggers.map { BackupSanitizer.decodeTrigger(BackupSanitizer.encodeTrigger(it)) })
    }
}
