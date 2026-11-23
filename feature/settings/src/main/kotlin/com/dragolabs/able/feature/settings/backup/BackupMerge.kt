package com.dragolabs.able.feature.settings.backup

import com.dragolabs.able.core.data.battery.BuiltInProfiles
import com.dragolabs.able.core.model.BatteryProfile

/** What applying the battery-profiles section changes (pure, JVM-testable). */
data class ProfilePlan(
    /** Profiles to store, already carrying the local id and sort order. */
    val save: List<BatteryProfile>,
    /** Custom profiles removed by Replace because the file does not contain them. */
    val deleteCustomIds: List<String>,
    /** Built-ins missing from the file; Replace resets them to their factory default. */
    val resetBuiltIns: List<BatteryProfile>,
    val applied: Int,
    val skipped: Int,
    /** Profiles present on both sides with different settings. */
    val conflicts: Int,
)

/** What applying the battery-history section changes (pure, JVM-testable). */
data class HistoryPlan(
    val insert: List<BatterySessionDto>,
    /** Notes for sessions that already exist locally without a note ("add missing"). */
    val notesForExisting: Map<SessionKey, BatterySessionNoteDto>,
    /** Sessions already present locally (same start and end); never overwritten. */
    val duplicates: Int,
    /** Sessions older than the retention setting; not imported because retention would delete them. */
    val expired: Int,
)

typealias SessionKey = Pair<Long, Long>

object BackupMerge {
    const val DAY_MS = 24L * 60 * 60 * 1000

    /** Built-ins are matched by type, custom profiles by id. */
    fun key(profile: BatteryProfile): String = profile.builtInType?.let { "builtin:" + it.name } ?: "custom:" + profile.id

    /** Settings that matter for a conflict: manual activation (state) and order are ignored. */
    private fun comparable(profile: BatteryProfile) =
        profile.copy(manualActive = false, sortOrder = 0, appRules = profile.restrictiveRules)

    fun differs(a: BatteryProfile, b: BatteryProfile): Boolean = comparable(a) != comparable(b)

    /**
     * Merge: new profiles are added after the local ones (keeping the file order), identical
     * ones count as applied, conflicting ones follow [useImported]. Replace: the file content
     * wins; local custom profiles missing from it are deleted and missing built-ins are reset
     * (built-ins can never be deleted). Manual activation always stays as it is locally.
     */
    fun planProfiles(
        local: List<BatteryProfile>,
        imported: List<BatteryProfile>,
        replace: Boolean,
        useImported: Boolean,
    ): ProfilePlan {
        val localByKey = local.associateBy(::key)
        val importedKeys = imported.map(::key).toSet()
        val save = mutableListOf<BatteryProfile>()
        var applied = 0
        var skipped = 0
        var conflicts = 0
        var nextOrder = (local.maxOfOrNull { it.sortOrder } ?: 0) + 1
        imported.sortedBy { it.sortOrder }.forEach { incoming ->
            val match = localByKey[key(incoming)]
            when {
                match == null -> {
                    val order = if (replace || incoming.builtIn) incoming.sortOrder else nextOrder++
                    save += incoming.copy(sortOrder = order, manualActive = false)
                    applied++
                }
                !differs(match, incoming) -> {
                    if (replace && match.sortOrder != incoming.sortOrder) {
                        save += incoming.copy(id = match.id, manualActive = match.manualActive)
                    }
                    applied++
                }
                else -> {
                    conflicts++
                    if (replace || useImported) {
                        save += incoming.copy(
                            id = match.id,
                            manualActive = match.manualActive,
                            sortOrder = if (replace) incoming.sortOrder else match.sortOrder,
                        )
                        applied++
                    } else {
                        skipped++
                    }
                }
            }
        }
        val missing = if (replace) local.filter { key(it) !in importedKeys } else emptyList()
        return ProfilePlan(
            save = save,
            deleteCustomIds = missing.filter { !it.builtIn }.map { it.id },
            resetBuiltIns = missing.mapNotNull { p ->
                p.builtInType?.let { BuiltInProfiles.defaultFor(it).copy(sortOrder = p.sortOrder, manualActive = p.manualActive) }
            },
            applied = applied,
            skipped = skipped,
            conflicts = conflicts,
        )
    }

    /**
     * Merge adds missing sessions only: a session that exists locally (same start and end) is
     * never overwritten; only its missing note is added. Replace starts from an empty history.
     * Sessions that the retention setting ([retentionDays], 0 = keep until deleted) would delete
     * are not imported.
     *
     * @param existing local sessions by key, with whether they already have a note.
     */
    fun planHistory(
        sessions: List<BatterySessionDto>,
        existing: Map<SessionKey, Boolean>,
        replace: Boolean,
        retentionDays: Int,
        nowEpochMs: Long,
    ): HistoryPlan {
        val cutoff = retentionCutoff(retentionDays, nowEpochMs)
        val insert = mutableListOf<BatterySessionDto>()
        val notes = linkedMapOf<SessionKey, BatterySessionNoteDto>()
        var duplicates = 0
        var expired = 0
        sessions.forEach { s ->
            val k = s.startTimeEpochMs to s.endTimeEpochMs
            val hasNote = if (replace) null else existing[k]
            when {
                cutoff != null && s.endTimeEpochMs < cutoff -> expired++
                hasNote == null -> insert += s
                else -> {
                    duplicates++
                    if (!hasNote && s.note != null) notes[k] = s.note
                }
            }
        }
        return HistoryPlan(insert, notes, duplicates, expired)
    }

    /** Same rule as the Battery history retention: sessions ending before the cutoff are deleted. */
    fun retentionCutoff(retentionDays: Int, nowEpochMs: Long): Long? =
        if (retentionDays > 0) nowEpochMs - retentionDays * DAY_MS else null
}
