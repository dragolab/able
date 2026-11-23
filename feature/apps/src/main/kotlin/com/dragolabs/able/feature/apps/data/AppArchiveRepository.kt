package com.dragolabs.able.feature.apps.data

import com.dragolabs.able.core.database.dao.AppArchiveDao
import com.dragolabs.able.core.database.entity.AppArchiveEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ArchivedAction { DISABLED, UNINSTALLED }

/** A package Able disabled or uninstalled for the current user (APP-004 archive). */
data class AppArchiveRecord(
    val packageName: String,
    val label: String,
    val action: ArchivedAction,
    val timestampEpochMs: Long,
    val priorEnabled: Boolean,
    val priorInstalled: Boolean,
    val isSystem: Boolean,
)

/**
 * Persistent Able archive, so archived packages stay restorable even without a PackageManager label.
 * APK backups of uninstalled user apps ([ApkBackupStore]) belong to their record: deleting a record
 * deletes its backup.
 */
class AppArchiveRepository(
    private val dao: AppArchiveDao,
    private val backups: ApkBackupStore? = null,
) {

    val records: Flow<List<AppArchiveRecord>> = dao.observeAll().map { list -> list.mapNotNull { it.toRecord() } }

    /**
     * Records that Able applied [action]. The prior state of an existing record is kept, so a
     * package disabled and later uninstalled still restores to its original state.
     */
    suspend fun record(
        packageName: String,
        label: String,
        action: ArchivedAction,
        isSystem: Boolean,
        priorState: PackageStateSnapshot?,
    ) {
        val existing = dao.get(packageName)
        dao.upsert(
            AppArchiveEntity(
                packageName = packageName,
                label = label,
                action = action.name,
                timestampEpochMs = System.currentTimeMillis(),
                priorEnabled = existing?.priorEnabled ?: (priorState?.enabled ?: true),
                priorInstalled = existing?.priorInstalled ?: (priorState?.installedForUser ?: true),
                isSystem = isSystem,
            ),
        )
    }

    suspend fun remove(packageName: String) {
        dao.delete(packageName)
        runCatching { backups?.delete(packageName) }
    }

    suspend fun removeAll(packageNames: List<String>) {
        if (packageNames.isEmpty()) return
        dao.deleteAll(packageNames)
        packageNames.forEach { runCatching { backups?.delete(it) } }
    }

    private fun AppArchiveEntity.toRecord(): AppArchiveRecord? {
        val parsed = ArchivedAction.entries.firstOrNull { it.name == action } ?: return null
        return AppArchiveRecord(packageName, label, parsed, timestampEpochMs, priorEnabled, priorInstalled, isSystem)
    }
}
