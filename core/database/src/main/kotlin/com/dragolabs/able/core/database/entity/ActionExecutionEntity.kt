package com.dragolabs.able.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dragolabs.able.core.model.ActionAuditRecord
import com.dragolabs.able.core.model.ActionExecutionStatus
import com.dragolabs.able.core.model.ProfileType

@Entity(tableName = "action_audit_log")
data class ActionExecutionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampEpochMs: Long,
    val actionId: String,
    val profileType: String?,
    val status: String,
    val detailMessage: String,
    val isReversible: Boolean,
    val targetPackage: String?
) {
    fun toDomain(): ActionAuditRecord = ActionAuditRecord(
        id = id,
        timestampEpochMs = timestampEpochMs,
        actionId = actionId,
        profileType = profileType?.let { ProfileType.valueOf(it) },
        status = ActionExecutionStatus.valueOf(status),
        detailMessage = detailMessage,
        isReversible = isReversible,
        targetPackage = targetPackage
    )

    companion object {
        fun fromDomain(domain: ActionAuditRecord): ActionExecutionEntity = ActionExecutionEntity(
            id = domain.id,
            timestampEpochMs = domain.timestampEpochMs,
            actionId = domain.actionId,
            profileType = domain.profileType?.name,
            status = domain.status.name,
            detailMessage = domain.detailMessage,
            isReversible = domain.isReversible,
            targetPackage = domain.targetPackage
        )
    }
}
