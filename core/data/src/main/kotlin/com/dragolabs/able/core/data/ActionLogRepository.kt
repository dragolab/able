package com.dragolabs.able.core.data

import com.dragolabs.able.core.database.dao.ActionExecutionDao
import com.dragolabs.able.core.database.entity.ActionExecutionEntity
import com.dragolabs.able.core.model.ActionAuditRecord
import com.dragolabs.able.core.model.ActionExecutionStatus
import com.dragolabs.able.core.model.ProfileType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Able action history (BAT-005). Every Able-applied change records its real outcome. */
class ActionLogRepository(private val dao: ActionExecutionDao) {

    fun recent(limit: Int = 50): Flow<List<ActionAuditRecord>> =
        dao.getRecentActions(limit).map { list -> list.mapNotNull { runCatching { it.toDomain() }.getOrNull() } }

    fun recentFailures(limit: Int = 5): Flow<List<ActionAuditRecord>> =
        dao.getRecentFailures(limit).map { list -> list.mapNotNull { runCatching { it.toDomain() }.getOrNull() } }

    suspend fun log(
        actionId: String,
        status: ActionExecutionStatus,
        detail: String,
        targetPackage: String? = null,
        profile: ProfileType? = null,
        reversible: Boolean = false,
    ) {
        dao.insert(
            ActionExecutionEntity.fromDomain(
                ActionAuditRecord(
                    timestampEpochMs = System.currentTimeMillis(),
                    actionId = actionId,
                    profileType = profile,
                    status = status,
                    detailMessage = detail,
                    isReversible = reversible,
                    targetPackage = targetPackage,
                ),
            ),
        )
    }

    suspend fun clear() = dao.clearAll()
}
