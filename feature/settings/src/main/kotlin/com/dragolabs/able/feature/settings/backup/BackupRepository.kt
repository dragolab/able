package com.dragolabs.able.feature.settings.backup

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.dragolabs.able.core.data.ActionLogRepository
import com.dragolabs.able.core.data.battery.BatteryProfileRepository
import com.dragolabs.able.core.database.AbleDatabase
import com.dragolabs.able.core.database.UserPreferencesRepository
import com.dragolabs.able.core.database.entity.BatteryNoteEntity
import com.dragolabs.able.core.database.entity.BatterySessionEntity
import com.dragolabs.able.core.database.entity.DnsProfileEntity
import com.dragolabs.able.core.database.entity.GpsPointEntity
import com.dragolabs.able.core.database.entity.HomeCardConfigEntity
import com.dragolabs.able.core.database.entity.ShortcutCollectionEntity
import com.dragolabs.able.core.database.entity.ShortcutDefinitionEntity
import com.dragolabs.able.core.model.ActionExecutionStatus
import com.dragolabs.able.core.model.GpsPoint
import com.dragolabs.able.core.model.HomeCardConfig
import com.dragolabs.able.core.model.ShortcutTargetType
import com.dragolabs.able.feature.settings.AppLanguageController
import com.dragolabs.able.feature.settings.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ImportMode { MERGE, REPLACE }

/** How Merge resolves an item that exists both locally and in the archive (CFG-005). */
enum class ConflictPolicy { KEEP_EXISTING, USE_IMPORTED }

data class SectionPreview(
    val section: BackupSection,
    val items: Int,
    val invalid: Int,
    val conflicts: Int,
    /** Items that reference apps/files not found on this device (CFG-006). */
    val unresolved: Int,
    val needRepin: Int = 0,
    val iconsReset: Int = 0,
    /** Battery history: sessions already on this device (same start/end); never overwritten. */
    val duplicates: Int = 0,
    /** Battery history: sessions older than [retentionDays]; not imported. */
    val expiring: Int = 0,
    val retentionDays: Int = 0,
    /** Battery history: notes/tags attached to the sessions. */
    val notes: Int = 0,
    /** Battery profiles came from a schema-1 archive and are converted to built-in profiles. */
    val legacy: Boolean = false,
)

data class ImportPreview(
    val appVersion: String,
    val createdAt: String,
    val sections: List<SectionPreview>,
    val unsupported: List<String>,
) {
    val totalConflicts: Int get() = sections.sumOf { it.conflicts }
}

data class SectionOutcome(
    val section: BackupSection,
    val applied: Int,
    val skipped: Int,
    val unresolved: Int,
    val failed: Boolean = false,
    /** Battery history: sessions already present and left untouched. */
    val duplicates: Int = 0,
    /** Battery history: sessions not imported because retention would delete them. */
    val expired: Int = 0,
)

data class ImportReport(
    val mode: ImportMode,
    val sections: List<SectionOutcome>,
    val rollbackSaved: Boolean,
    val needRepin: Int,
    val iconsReset: Int,
) {
    val anyFailed: Boolean get() = sections.any { it.failed }
}

/** Size of the optional battery-history section, shown in the export dialog (CFG-003). */
data class HistorySummary(
    val sessions: Int,
    val notes: Int,
    /** Uncompressed JSON size; the archive is compressed and therefore smaller. */
    val approxBytes: Long,
    val oldestEpochMs: Long?,
    val newestEpochMs: Long?,
)

sealed interface ExportResult {
    data class Success(val fileName: String, val sections: Int) : ExportResult
    data object WriteFailed : ExportResult
}

sealed interface ImportReadResult {
    data class Ready(val backup: ParsedBackup, val preview: ImportPreview) : ImportReadResult
    data class Invalid(val error: BackupError) : ImportReadResult
}

/**
 * Configuration export/import for Settings → Backup & transfer (CFG-001..CFG-007).
 *
 * Archive parsing and value validation are delegated to the pure [BackupArchive] and
 * [BackupSanitizer]; this class reads the current configuration, computes previews/conflicts,
 * stores the rollback snapshot and applies sanitized data.
 */
class BackupRepository(
    private val context: Context,
    private val preferences: UserPreferencesRepository,
    private val database: AbleDatabase,
    private val batteryProfiles: BatteryProfileRepository,
    private val actionLog: ActionLogRepository,
    private val language: AppLanguageController,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val rollbackFile: File get() = File(File(context.filesDir, "backup"), "rollback.$EXT")
    private val _rollbackAvailable = MutableStateFlow(rollbackFile.exists())
    val rollbackAvailable: StateFlow<Boolean> = _rollbackAvailable.asStateFlow()

    fun suggestedFileName(now: Instant = Instant.now()): String =
        "able-backup-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ZoneId.systemDefault()).format(now) + ".$EXT"

    // --- Export ----------------------------------------------------------------------------

    suspend fun export(uri: Uri, sections: Set<BackupSection>): ExportResult = withContext(io) {
        try {
            val payload = buildPayload(sections)
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: return@withContext ExportResult.WriteFailed
            stream.use { BackupArchive.write(it, payload, appVersion(), Instant.now().toString()) }
            ExportResult.Success(displayName(uri) ?: uri.lastPathSegment.orEmpty(), sections.size)
        } catch (_: IOException) {
            ExportResult.WriteFailed
        } catch (_: SecurityException) {
            ExportResult.WriteFailed
        }
    }

    suspend fun buildPayload(sections: Set<BackupSection>): BackupPayload {
        val prefs = preferences.userPreferencesFlow.first()
        return BackupPayload(
            batteryProfiles = if (BackupSection.BATTERY_PROFILES in sections) {
                BatteryProfilesSectionDto(
                    profiles = batteryProfiles.profiles.first().map(BackupSanitizer::profileToDto),
                    protectedApps = prefs.protectedAppPackages.sorted(),
                )
            } else {
                null
            },
            homeAppearance = if (BackupSection.HOME_APPEARANCE in sections) {
                HomeAppearanceSectionDto(
                    cards = database.homeCardDao().getAllCards().first()
                        .map { HomeCardDto(it.cardType, it.isVisible, it.displayOrder) },
                    themeMode = prefs.themeMode.name,
                    languageTag = prefs.languageOverride,
                )
            } else {
                null
            },
            gps = if (BackupSection.GPS_POINTS in sections) {
                GpsSectionDto(
                    database.gpsDao().getAllPoints().first().map { it.toDomain() }.map {
                        GpsPointDto(
                            it.latitude, it.longitude, it.altitudeMeters, it.accuracyMeters, it.timestampEpochMs,
                            it.label, it.notes, it.tags, it.reverseGeocodedAddress,
                        )
                    },
                )
            } else {
                null
            },
            dns = if (BackupSection.DNS_PROFILES in sections) {
                DnsSectionDto(
                    database.dnsDao().getAllProfiles().first()
                        .map { DnsProfileDto(it.id, it.name, it.mode, it.hostname, it.isDefault) },
                )
            } else {
                null
            },
            shortcuts = if (BackupSection.SHORTCUTS in sections) {
                val dao = database.shortcutDao()
                ShortcutsSectionDto(
                    collections = dao.getAllCollections().first()
                        .map { ShortcutCollectionDto(it.id, it.name, it.sortOrder, it.createdAtEpochMs) },
                    shortcuts = dao.getAllShortcuts().first().map {
                        ShortcutDto(
                            it.id, it.label, it.targetType, it.targetData, it.targetPackage, it.collectionId,
                            it.iconType, it.iconValue, it.iconBackgroundColorHex, it.sortOrder,
                            it.isPinnedToLauncher, it.createdAtEpochMs,
                        )
                    },
                )
            } else {
                null
            },
            // Opt-in only (CFG-003): included solely when the user selected it for this export.
            batteryHistory = if (BackupSection.BATTERY_HISTORY in sections) historyDto() else null,
        )
    }

    /** Session summaries with their notes, oldest first, capped to what an import accepts. */
    private suspend fun historyDto(): BatteryHistorySectionDto {
        val dao = database.batteryDao()
        val notes = dao.getAllNotes().first().associateBy { it.sessionId }
        val sessions = dao.getAllSessions().first()
            .sortedByDescending { it.startTimeEpochMs }
            .take(BackupSanitizer.MAX_HISTORY_SESSIONS)
            .sortedBy { it.startTimeEpochMs }
        return BatteryHistorySectionDto(sessions.map { it.toDto(notes[it.id]) })
    }

    suspend fun historySummary(): HistorySummary = withContext(io) {
        val dto = historyDto()
        HistorySummary(
            sessions = dto.sessions.size,
            notes = dto.sessions.count { it.note != null },
            approxBytes = BackupArchive.encodedSize(dto),
            oldestEpochMs = dto.sessions.minOfOrNull { it.startTimeEpochMs },
            newestEpochMs = dto.sessions.maxOfOrNull { it.endTimeEpochMs },
        )
    }

    private fun BatterySessionEntity.toDto(note: BatteryNoteEntity?) = BatterySessionDto(
        startTimeEpochMs, endTimeEpochMs, isCharging, startLevelPercent, endLevelPercent,
        avgTemperatureCelsius, maxTemperatureCelsius, avgCurrentMilliAmperes, avgVoltageMillivolts,
        avgPowerMilliWatts, screenOnDurationMs, screenOffDurationMs, deepSleepDurationMs, hasThermalAlert,
        isPartial, screenOnDropPercent, screenOffDropPercent,
        note = note?.let { BatterySessionNoteDto(it.noteText, decodeTags(it.tagsJson), it.createdAtEpochMs) },
    )

    private fun BatterySessionDto.toEntity() = BatterySessionEntity(
        startTimeEpochMs = startTimeEpochMs,
        endTimeEpochMs = endTimeEpochMs,
        isCharging = isCharging,
        startLevelPercent = startLevelPercent,
        endLevelPercent = endLevelPercent,
        avgTemperatureCelsius = avgTemperatureCelsius,
        maxTemperatureCelsius = maxTemperatureCelsius,
        avgCurrentMilliAmperes = avgCurrentMilliAmperes,
        avgVoltageMillivolts = avgVoltageMillivolts,
        avgPowerMilliWatts = avgPowerMilliWatts,
        screenOnDurationMs = screenOnDurationMs,
        screenOffDurationMs = screenOffDurationMs,
        deepSleepDurationMs = deepSleepDurationMs,
        hasThermalAlert = hasThermalAlert,
        isPartial = isPartial,
        screenOnDropPercent = screenOnDropPercent,
        screenOffDropPercent = screenOffDropPercent,
    )

    private fun noteEntity(sessionId: Long, note: BatterySessionNoteDto) = BatteryNoteEntity(
        sessionId = sessionId,
        noteText = note.text,
        tagsJson = BackupArchive.json.encodeToString(tagsSerializer, note.tags),
        createdAtEpochMs = note.createdAtEpochMs,
    )

    private fun decodeTags(json: String): List<String> =
        runCatching { BackupArchive.json.decodeFromString(tagsSerializer, json) }.getOrDefault(emptyList())

    // --- Import: read, validate, preview ---------------------------------------------------

    suspend fun read(uri: Uri): ImportReadResult = withContext(io) {
        val result = try {
            context.contentResolver.openInputStream(uri)?.use { BackupArchive.read(it) }
                ?: BackupReadResult.Invalid(BackupError.ReadFailed)
        } catch (_: IOException) {
            BackupReadResult.Invalid(BackupError.ReadFailed)
        } catch (_: SecurityException) {
            BackupReadResult.Invalid(BackupError.ReadFailed)
        }
        when (result) {
            is BackupReadResult.Invalid -> ImportReadResult.Invalid(result.error)
            is BackupReadResult.Valid -> ImportReadResult.Ready(result.backup, preview(result.backup))
        }
    }

    suspend fun preview(backup: ParsedBackup): ImportPreview {
        val prefs = preferences.userPreferencesFlow.first()
        val p = backup.payload
        val sections = buildList {
            if (BackupSection.BATTERY_PROFILES in p.sections) {
                val local = batteryProfiles.profiles.first()
                val s = sanitizeProfiles(p, local)
                val plan = BackupMerge.planProfiles(local, s.profiles, replace = false, useImported = false)
                add(
                    SectionPreview(
                        BackupSection.BATTERY_PROFILES,
                        items = s.profiles.size + s.protectedApps.size,
                        invalid = s.rejected,
                        conflicts = plan.conflicts,
                        unresolved = s.packages.count { !isInstalled(it) },
                        legacy = s.legacy,
                    ),
                )
            }
            p.homeAppearance?.let { dto ->
                val s = BackupSanitizer.homeAppearance(dto)
                val currentCards = currentCards()
                var conflicts = 0
                if (s.cards.isNotEmpty() && s.cards != currentCards) conflicts++
                if (s.themeMode != null && s.themeMode != prefs.themeMode) conflicts++
                if (s.languageValid && s.languageTag != prefs.languageOverride) conflicts++
                add(
                    SectionPreview(
                        BackupSection.HOME_APPEARANCE,
                        items = s.cards.size + (if (s.themeMode != null) 1 else 0) + (if (s.languageValid) 1 else 0),
                        invalid = s.rejected,
                        conflicts = conflicts,
                        unresolved = 0,
                    ),
                )
            }
            p.gps?.let { dto ->
                val s = BackupSanitizer.gps(dto)
                val existing = database.gpsDao().getAllPoints().first().map { it.gpsKey() }.toSet()
                add(SectionPreview(BackupSection.GPS_POINTS, s.items.size, s.rejected, s.items.count { it.gpsKey() in existing }, 0))
            }
            p.dns?.let { dto ->
                val s = BackupSanitizer.dns(dto)
                val existing = database.dnsDao().getAllProfiles().first().associateBy { it.id }
                val conflicts = s.items.count { d -> existing[d.id]?.let { it.toDomain() != d } == true }
                add(SectionPreview(BackupSection.DNS_PROFILES, s.items.size, s.rejected, conflicts, 0))
            }
            p.shortcuts?.let { dto ->
                val s = BackupSanitizer.shortcuts(dto)
                val dao = database.shortcutDao()
                val existingShortcuts = dao.getAllShortcuts().first().associateBy { it.id }
                val existingCollections = dao.getAllCollections().first().associateBy { it.id }
                val conflicts = s.shortcuts.count { item ->
                    existingShortcuts[item.id]?.let { it != ShortcutDefinitionEntity.fromDomain(item) } == true
                } + s.collections.count { c ->
                    existingCollections[c.id]?.let { it != ShortcutCollectionEntity.fromDomain(c) } == true
                }
                add(
                    SectionPreview(
                        BackupSection.SHORTCUTS,
                        items = s.shortcuts.size + s.collections.size,
                        invalid = s.rejected,
                        conflicts = conflicts,
                        unresolved = s.shortcuts.count { !isResolvable(it.targetType, it.targetPackage) },
                        needRepin = s.needRepin,
                        iconsReset = s.iconsReset,
                    ),
                )
            }
            p.batteryHistory?.let { dto ->
                val now = clock()
                val s = BackupSanitizer.batteryHistory(dto, now)
                val existing = database.batteryDao().getAllSessions().first()
                    .associate { (it.startTimeEpochMs to it.endTimeEpochMs) to false }
                val plan = BackupMerge.planHistory(s.sessions, existing, replace = false, prefs.batteryHistoryRetentionDays, now)
                add(
                    SectionPreview(
                        BackupSection.BATTERY_HISTORY,
                        items = s.sessions.size,
                        invalid = s.rejected + s.duplicatesInFile,
                        // Existing sessions are never overwritten, so there is nothing to choose.
                        conflicts = 0,
                        unresolved = 0,
                        duplicates = plan.duplicates,
                        expiring = plan.expired,
                        retentionDays = prefs.batteryHistoryRetentionDays,
                        notes = s.notes,
                    ),
                )
            }
        }
        return ImportPreview(backup.manifest.appVersion, backup.manifest.createdAt, sections, backup.unsupported)
    }

    // --- Import: apply ---------------------------------------------------------------------

    /**
     * Saves a rollback snapshot of every section the archive touches, then applies it. The
     * snapshot is written before any mutation; if it cannot be written nothing is changed.
     */
    suspend fun apply(backup: ParsedBackup, mode: ImportMode, policy: ConflictPolicy): ImportReport? = withContext(io) {
        val rollbackSaved = saveRollback(backup.payload.sections)
        if (!rollbackSaved) return@withContext null
        val report = applyPayload(backup.payload, mode, policy, rollbackSaved = true)
        log(report, restore = false)
        report
    }

    /** Restores the snapshot taken before the last import, then deletes it. */
    suspend fun restoreRollback(): ImportReport? = withContext(io) {
        val file = rollbackFile
        if (!file.exists()) return@withContext null
        val result = try {
            file.inputStream().use { BackupArchive.read(it) }
        } catch (_: IOException) {
            null
        }
        val backup = (result as? BackupReadResult.Valid)?.backup ?: return@withContext null
        val report = applyPayload(backup.payload, ImportMode.REPLACE, ConflictPolicy.USE_IMPORTED, rollbackSaved = false)
        if (!report.anyFailed) {
            file.delete()
            _rollbackAvailable.value = false
        }
        log(report, restore = true)
        report
    }

    private suspend fun saveRollback(sections: Set<BackupSection>): Boolean = try {
        val dir = rollbackFile.parentFile!!
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Cannot create backup directory")
        val tmp = File(dir, "rollback.tmp")
        tmp.outputStream().use { BackupArchive.write(it, buildPayload(sections), appVersion(), Instant.now().toString()) }
        if (rollbackFile.exists()) rollbackFile.delete()
        val renamed = tmp.renameTo(rollbackFile)
        _rollbackAvailable.value = rollbackFile.exists()
        renamed
    } catch (_: IOException) {
        false
    }

    private suspend fun applyPayload(
        payload: BackupPayload,
        mode: ImportMode,
        policy: ConflictPolicy,
        rollbackSaved: Boolean,
    ): ImportReport {
        val outcomes = mutableListOf<SectionOutcome>()
        var needRepin = 0
        var iconsReset = 0
        val replace = mode == ImportMode.REPLACE
        val useImported = replace || policy == ConflictPolicy.USE_IMPORTED

        if (BackupSection.BATTERY_PROFILES in payload.sections) {
            outcomes += guarded(BackupSection.BATTERY_PROFILES) { applyProfiles(payload, replace, useImported) }
        }
        payload.homeAppearance?.let { outcomes += guarded(BackupSection.HOME_APPEARANCE) { applyHome(BackupSanitizer.homeAppearance(it), useImported) } }
        payload.gps?.let { outcomes += guarded(BackupSection.GPS_POINTS) { applyGps(BackupSanitizer.gps(it), replace, useImported) } }
        payload.dns?.let { outcomes += guarded(BackupSection.DNS_PROFILES) { applyDns(BackupSanitizer.dns(it), replace, useImported) } }
        payload.shortcuts?.let {
            val s = BackupSanitizer.shortcuts(it)
            needRepin = s.needRepin
            iconsReset = s.iconsReset
            outcomes += guarded(BackupSection.SHORTCUTS) { applyShortcuts(s, replace, useImported) }
        }
        payload.batteryHistory?.let { outcomes += guarded(BackupSection.BATTERY_HISTORY) { applyHistory(it, replace) } }
        return ImportReport(mode, outcomes, rollbackSaved, needRepin, iconsReset)
    }

    private suspend fun guarded(section: BackupSection, block: suspend () -> SectionOutcome): SectionOutcome = try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        SectionOutcome(section, applied = 0, skipped = 0, unresolved = 0, failed = true)
    }

    private suspend fun sanitizeProfiles(payload: BackupPayload, local: List<com.dragolabs.able.core.model.BatteryProfile>): SanitizedBatteryProfiles =
        payload.batteryProfiles?.let(BackupSanitizer::batteryProfiles)
            ?: BackupSanitizer.legacyBatteryProfiles(requireNotNull(payload.legacyBattery), local)

    private suspend fun applyProfiles(payload: BackupPayload, replace: Boolean, useImported: Boolean): SectionOutcome {
        val local = batteryProfiles.profiles.first()
        val s = sanitizeProfiles(payload, local)
        val plan = BackupMerge.planProfiles(local, s.profiles, replace, useImported)
        plan.deleteCustomIds.forEach { batteryProfiles.delete(it) }
        plan.resetBuiltIns.forEach { batteryProfiles.save(it) }
        // save() also mirrors built-in app rules to the legacy preferences.
        plan.save.forEach { batteryProfiles.save(it) }
        val protectedNow = preferences.userPreferencesFlow.first().protectedAppPackages
        if (replace) protectedNow.filterNot { it in s.protectedApps }.forEach { preferences.setAppProtected(it, false) }
        s.protectedApps.filterNot { it in protectedNow }.forEach { preferences.setAppProtected(it, true) }
        // Rules for apps missing on this device are kept but stay inert until the app is installed.
        val unresolved = s.packages.count { !isInstalled(it) }
        return SectionOutcome(
            BackupSection.BATTERY_PROFILES,
            applied = plan.applied + s.protectedApps.size,
            skipped = plan.skipped + s.rejected,
            unresolved = unresolved,
        )
    }

    /**
     * Merge adds missing sessions (and missing notes of existing ones); Replace clears the
     * history first. Both skip sessions the retention setting would delete. Runs in one
     * transaction, so a failure leaves the history unchanged.
     */
    private suspend fun applyHistory(dto: BatteryHistorySectionDto, replace: Boolean): SectionOutcome {
        val now = clock()
        val s = BackupSanitizer.batteryHistory(dto, now)
        val retention = preferences.userPreferencesFlow.first().batteryHistoryRetentionDays
        val dao = database.batteryDao()
        val plan = database.withTransaction {
            if (replace) {
                dao.clearAllSessions()
                dao.clearAllNotes()
            }
            val local = if (replace) emptyList() else dao.getAllSessions().first()
            val withNotes = if (replace) emptySet() else dao.getAllNotes().first().mapTo(mutableSetOf()) { it.sessionId }
            val idByKey = local.associate { (it.startTimeEpochMs to it.endTimeEpochMs) to it.id }
            val existing = local.associate { (it.startTimeEpochMs to it.endTimeEpochMs) to (it.id in withNotes) }
            val plan = BackupMerge.planHistory(s.sessions, existing, replace, retention, now)
            val ids = dao.insertSessions(plan.insert.map { it.toEntity() })
            val notes = plan.insert.zip(ids).mapNotNull { (session, id) -> session.note?.let { noteEntity(id, it) } } +
                plan.notesForExisting.mapNotNull { (key, note) -> idByKey[key]?.let { noteEntity(it, note) } }
            if (notes.isNotEmpty()) dao.insertNotes(notes)
            plan
        }
        return SectionOutcome(
            BackupSection.BATTERY_HISTORY,
            applied = plan.insert.size,
            skipped = s.rejected + s.duplicatesInFile,
            unresolved = 0,
            duplicates = plan.duplicates,
            expired = plan.expired,
        )
    }

    private suspend fun applyHome(s: SanitizedHomeAppearance, useImported: Boolean): SectionOutcome {
        val prefs = preferences.userPreferencesFlow.first()
        var applied = 0
        var skipped = s.rejected
        if (s.cards.isNotEmpty()) {
            val current = currentCards()
            if (s.cards == current) {
                applied++
            } else if (useImported) {
                val dao = database.homeCardDao()
                database.withTransaction {
                    dao.clearAll()
                    dao.insertOrUpdateCards(s.cards.map(HomeCardConfigEntity::fromDomain))
                }
                applied++
            } else {
                skipped++
            }
        }
        s.themeMode?.let { theme ->
            if (theme == prefs.themeMode) {
                applied++
            } else if (useImported) {
                preferences.setThemeMode(theme)
                applied++
            } else {
                skipped++
            }
        }
        if (s.languageValid) {
            if (s.languageTag == prefs.languageOverride) {
                applied++
            } else if (useImported) {
                preferences.setLanguageOverride(s.languageTag)
                withContext(Dispatchers.Main) { language.apply(s.languageTag) }
                applied++
            } else {
                skipped++
            }
        }
        return SectionOutcome(BackupSection.HOME_APPEARANCE, applied, skipped, 0)
    }

    private suspend fun applyGps(s: SanitizedList<GpsPoint>, replace: Boolean, useImported: Boolean): SectionOutcome {
        val dao = database.gpsDao()
        var applied = 0
        var skipped = s.rejected
        database.withTransaction {
            if (replace) dao.clearAll()
            val existing = if (replace) emptyMap() else dao.getAllPoints().first().associateBy { it.gpsKey() }
            s.items.forEach { point ->
                val match = existing[point.gpsKey()]
                when {
                    match == null -> { dao.insertPoint(GpsPointEntity.fromDomain(point)); applied++ }
                    useImported -> { dao.insertPoint(GpsPointEntity.fromDomain(point.copy(id = match.id))); applied++ }
                    else -> skipped++
                }
            }
        }
        return SectionOutcome(BackupSection.GPS_POINTS, applied, skipped, 0)
    }

    private suspend fun applyDns(s: SanitizedList<com.dragolabs.able.core.model.DnsProfile>, replace: Boolean, useImported: Boolean): SectionOutcome {
        val dao = database.dnsDao()
        var applied = 0
        var skipped = s.rejected
        database.withTransaction {
            val existing = dao.getAllProfiles().first()
            if (replace) existing.forEach { dao.deleteProfileById(it.id) }
            val byId = if (replace) emptyMap() else existing.associateBy { it.id }
            val toInsert = s.items.filter { profile ->
                val match = byId[profile.id]
                val accept = match == null || useImported || match.toDomain() == profile
                if (accept) applied++ else skipped++
                accept
            }
            dao.insertProfiles(toInsert.map(DnsProfileEntity::fromDomain))
        }
        return SectionOutcome(BackupSection.DNS_PROFILES, applied, skipped, 0)
    }

    private suspend fun applyShortcuts(s: SanitizedShortcuts, replace: Boolean, useImported: Boolean): SectionOutcome {
        val dao = database.shortcutDao()
        var applied = 0
        var skipped = s.rejected
        database.withTransaction {
            val existingCollections = dao.getAllCollections().first()
            val existingShortcuts = dao.getAllShortcuts().first()
            if (replace) {
                existingShortcuts.forEach { dao.deleteShortcutById(it.id) }
                existingCollections.forEach { dao.deleteCollectionById(it.id) }
            }
            val collectionsById = if (replace) emptyMap() else existingCollections.associateBy { it.id }
            val shortcutsById = if (replace) emptyMap() else existingShortcuts.associateBy { it.id }
            s.collections.forEach { c ->
                val match = collectionsById[c.id]
                if (match == null || useImported) {
                    dao.insertCollection(ShortcutCollectionEntity.fromDomain(c)); applied++
                } else {
                    skipped++
                }
            }
            val accepted = s.shortcuts.filter { item ->
                val match = shortcutsById[item.id]
                val accept = match == null || useImported
                if (accept) applied++ else skipped++
                accept
            }
            dao.insertShortcuts(accepted.map(ShortcutDefinitionEntity::fromDomain))
        }
        val unresolved = s.shortcuts.count { !isResolvable(it.targetType, it.targetPackage) }
        return SectionOutcome(BackupSection.SHORTCUTS, applied, skipped, unresolved)
    }

    // --- Helpers ---------------------------------------------------------------------------

    private suspend fun currentCards(): List<HomeCardConfig> {
        val stored = database.homeCardDao().getAllCards().first().mapNotNull { runCatching { it.toDomain() }.getOrNull() }
        if (stored.isEmpty()) return HomeCardConfig.defaultCards()
        return stored.sortedBy { it.displayOrder }.mapIndexed { i, c -> c.copy(displayOrder = i) }
    }

    private fun GpsPoint.gpsKey() = Triple(timestampEpochMs, latitude, longitude)
    private fun GpsPointEntity.gpsKey() = Triple(timestampEpochMs, latitude, longitude)

    private fun isResolvable(type: ShortcutTargetType, pkg: String?): Boolean = when (type) {
        ShortcutTargetType.APPLICATION, ShortcutTargetType.ACTIVITY -> pkg != null && isInstalled(pkg)
        // Document grants are bound to the source device and are not transferable.
        ShortcutTargetType.FILE_OR_FOLDER -> false
        ShortcutTargetType.URL, ShortcutTargetType.ABLE_ACTION -> true
    }

    private fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
    }.getOrNull() ?: "unknown"

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private suspend fun log(report: ImportReport, restore: Boolean) {
        val applied = report.sections.sumOf { it.applied }
        val skipped = report.sections.sumOf { it.skipped + it.duplicates + it.expired }
        val detail = context.getString(
            if (restore) R.string.settings_backup_log_restore else R.string.settings_backup_log_import,
            applied,
            skipped,
        )
        runCatching {
            actionLog.log(
                actionId = if (restore) "settings.backup.restore" else "settings.backup.import",
                status = if (report.anyFailed) ActionExecutionStatus.FAILED else ActionExecutionStatus.SUCCESS,
                detail = detail,
                reversible = !restore && report.rollbackSaved,
            )
        }
    }

    companion object {
        const val EXT = BackupArchive.FILE_EXTENSION
        private val tagsSerializer = ListSerializer(String.serializer())
    }
}
