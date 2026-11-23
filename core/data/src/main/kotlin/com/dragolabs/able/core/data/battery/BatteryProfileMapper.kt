package com.dragolabs.able.core.data.battery

import com.dragolabs.able.core.database.entity.BatteryProfileEntity
import com.dragolabs.able.core.model.AppInterventionLevel
import com.dragolabs.able.core.model.BatteryProfile
import com.dragolabs.able.core.model.ProfileTrigger
import com.dragolabs.able.core.model.ProfileType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Converts between Room rows and the domain model; unreadable JSON degrades to "no triggers / no rules". */
object BatteryProfileMapper {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }
    private val triggersSerializer = ListSerializer(ProfileTrigger.serializer())
    private val rulesSerializer = MapSerializer(String.serializer(), AppInterventionLevel.serializer())

    fun toModel(entity: BatteryProfileEntity): BatteryProfile = BatteryProfile(
        id = entity.id,
        name = entity.name,
        iconKey = entity.iconKey,
        colorKey = entity.colorKey,
        builtInType = entity.builtInType?.let { name -> ProfileType.entries.firstOrNull { it.name == name } },
        enabled = entity.enabled,
        manualActive = entity.manualActive,
        triggers = runCatching { json.decodeFromString(triggersSerializer, entity.triggersJson) }.getOrDefault(emptyList()),
        appRules = runCatching { json.decodeFromString(rulesSerializer, entity.appRulesJson) }.getOrDefault(emptyMap()),
        offerBatterySaver = entity.offerBatterySaver,
        sortOrder = entity.sortOrder,
    )

    fun toEntity(profile: BatteryProfile, updatedAtEpochMs: Long): BatteryProfileEntity = BatteryProfileEntity(
        id = profile.id,
        name = profile.name.trim(),
        iconKey = profile.iconKey,
        colorKey = profile.colorKey,
        builtInType = profile.builtInType?.name,
        enabled = profile.enabled,
        manualActive = profile.manualActive,
        triggersJson = json.encodeToString(triggersSerializer, profile.triggers),
        appRulesJson = json.encodeToString(rulesSerializer, profile.restrictiveRules),
        offerBatterySaver = profile.offerBatterySaver,
        sortOrder = profile.sortOrder,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}
