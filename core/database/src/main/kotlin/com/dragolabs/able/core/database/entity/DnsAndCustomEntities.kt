package com.dragolabs.able.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dragolabs.able.core.model.CustomTileMapping
import com.dragolabs.able.core.model.CustomWidgetConfig
import com.dragolabs.able.core.model.DnsProfile
import com.dragolabs.able.core.model.PrivateDnsMode
import com.dragolabs.able.core.model.WidgetGridFormat

@Entity(tableName = "dns_profiles")
data class DnsProfileEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val mode: String,
    val hostname: String,
    val isDefault: Boolean
) {
    fun toDomain(): DnsProfile = DnsProfile(
        id = id,
        name = name,
        mode = PrivateDnsMode.valueOf(mode),
        hostname = hostname,
        isDefault = isDefault
    )

    companion object {
        fun fromDomain(domain: DnsProfile): DnsProfileEntity = DnsProfileEntity(
            id = domain.id,
            name = domain.name,
            mode = domain.mode.name,
            hostname = domain.hostname,
            isDefault = domain.isDefault
        )
    }
}

@Entity(tableName = "custom_tile_mappings")
data class CustomTileMappingEntity(
    @PrimaryKey
    val slotIndex: Int,
    val actionId: String,
    val customLabel: String?,
    val customIconKey: String?
) {
    fun toDomain(): CustomTileMapping = CustomTileMapping(
        slotIndex = slotIndex,
        actionId = actionId,
        customLabel = customLabel,
        customIconKey = customIconKey
    )

    companion object {
        fun fromDomain(domain: CustomTileMapping): CustomTileMappingEntity = CustomTileMappingEntity(
            slotIndex = domain.slotIndex,
            actionId = domain.actionId,
            customLabel = domain.customLabel,
            customIconKey = domain.customIconKey
        )
    }
}

@Entity(tableName = "custom_widget_configs")
data class CustomWidgetConfigEntity(
    @PrimaryKey
    val widgetId: Int,
    val gridFormat: String,
    val actionIdsJson: String
) {
    fun toDomain(): CustomWidgetConfig = CustomWidgetConfig(
        widgetId = widgetId,
        gridFormat = WidgetGridFormat.valueOf(gridFormat),
        actionIds = if (actionIdsJson.isBlank()) emptyList() else actionIdsJson.split(",")
    )

    companion object {
        fun fromDomain(domain: CustomWidgetConfig): CustomWidgetConfigEntity = CustomWidgetConfigEntity(
            widgetId = domain.widgetId,
            gridFormat = domain.gridFormat.name,
            actionIdsJson = domain.actionIds.joinToString(",")
        )
    }
}
