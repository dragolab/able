package com.dragolabs.able.feature.battery.data

import com.dragolabs.able.core.database.UserPreferencesRepository
import com.dragolabs.able.core.database.dao.BatteryDao
import com.dragolabs.able.core.database.entity.BatteryNoteEntity
import com.dragolabs.able.core.database.entity.BatterySessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray

/** Stable tag keys stored with notes (BAT-NOTE-001); labels are localized at display time. */
enum class SessionTag(val key: String) {
    TRAVEL("travel"),
    GAMING("gaming"),
    HOTSPOT("hotspot"),
    NAVIGATION("navigation");

    companion object {
        fun fromKey(key: String): SessionTag? = entries.firstOrNull { it.key == key }
    }
}

data class SessionNote(val text: String, val tags: Set<SessionTag>)

/**
 * Local battery session history (MON-009, BAT-CHG-001, BAT-NOTE-001). Retention deletes session
 * summaries and their notes; the idle baseline is always derived from the remaining summaries,
 * so it disappears together with them.
 */
class BatteryHistoryRepository(
    private val dao: BatteryDao,
    private val preferences: UserPreferencesRepository,
) {
    val sessions: Flow<List<BatterySessionEntity>> = dao.getAllSessions()
    val notes: Flow<List<BatteryNoteEntity>> = dao.getAllNotes()

    suspend fun insert(session: BatterySessionEntity): BatterySessionEntity {
        val id = dao.insertSession(session)
        applyRetention()
        return session.copy(id = id)
    }

    suspend fun applyRetention() {
        val days = preferences.userPreferencesFlow.first().batteryHistoryRetentionDays
        if (days > 0) dao.deleteSessionsOlderThan(System.currentTimeMillis() - days * DAY_MS)
        dao.deleteOrphanedNotes()
    }

    suspend fun deleteAll() {
        dao.clearAllSessions()
        dao.clearAllNotes()
    }

    /** Sessions that can contribute to the 14-day idle baseline. */
    suspend fun baselineCandidates(nowEpochMs: Long): List<BatterySessionEntity> =
        dao.getSessionsSince(nowEpochMs - 15 * DAY_MS)

    suspend fun saveNote(sessionId: Long, text: String, tags: Set<SessionTag>) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && tags.isEmpty()) {
            dao.deleteNote(sessionId)
        } else {
            dao.insertOrUpdateNote(
                BatteryNoteEntity(
                    sessionId = sessionId,
                    noteText = trimmed,
                    tagsJson = JSONArray(tags.map { it.key }).toString(),
                    createdAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L

        fun decode(note: BatteryNoteEntity): SessionNote {
            val tags = runCatching {
                val array = JSONArray(note.tagsJson)
                (0 until array.length()).mapNotNull { SessionTag.fromKey(array.optString(it)) }.toSet()
            }.getOrDefault(emptySet())
            return SessionNote(note.noteText, tags)
        }
    }
}
