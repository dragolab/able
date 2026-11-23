package com.dragolabs.able.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ActionCategory {
    BATTERY,
    CONNECTIVITY,
    TOOLS,
    SHORTCUTS,
    SYSTEM
}

@Serializable
enum class ActionExecutionType {
    IMMEDIATE_TOGGLE,
    LAUNCH_SCREEN,
    TRIGGER_AUTOMATION
}

/**
 * Universal action descriptor from the Able Action Catalog (CUST-001, CUST-002, ADR-024).
 */
@Serializable
data class AbleAction(
    val id: String,
    val category: ActionCategory,
    val titleResKey: String,
    val descriptionResKey: String,
    val iconKey: String,
    val requiresPrivilege: Boolean = false,
    val executionType: ActionExecutionType = ActionExecutionType.IMMEDIATE_TOGGLE
)

/**
 * User mapping for customizable Quick Settings tile slots (CUST-001).
 */
@Serializable
data class CustomTileMapping(
    val slotIndex: Int, // 1..4
    val actionId: String,
    val customLabel: String? = null,
    val customIconKey: String? = null
)

@Serializable
enum class WidgetGridFormat(val rows: Int, val columns: Int, val maxSlots: Int) {
    GRID_2X1(1, 2, 2),
    GRID_4X1(1, 4, 4),
    GRID_4X2(2, 4, 8)
}

/**
 * Multi-action AppWidget configuration (CUST-002, ADR-024).
 */
@Serializable
data class CustomWidgetConfig(
    val widgetId: Int,
    val gridFormat: WidgetGridFormat,
    val actionIds: List<String>
)
