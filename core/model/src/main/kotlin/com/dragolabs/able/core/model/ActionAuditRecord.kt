package com.dragolabs.able.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ActionExecutionStatus {
    ATTEMPTED,
    SUCCESS,
    SKIPPED,
    FAILED
}

/**
 * Historical record of any applied Able action (BAT-005, NFR-REL-001).
 */
@Serializable
data class ActionAuditRecord(
    val id: Long = 0,
    val timestampEpochMs: Long,
    val actionId: String,
    val profileType: ProfileType? = null,
    val status: ActionExecutionStatus,
    val detailMessage: String,
    val isReversible: Boolean = false,
    val targetPackage: String? = null
)
