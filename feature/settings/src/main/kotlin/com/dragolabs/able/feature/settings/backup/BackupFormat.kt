package com.dragolabs.able.feature.settings.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * `.ablebackup` schema (CFG-002, ADR-006).
 *
 * The archive is a ZIP file with a fixed layout:
 *
 * ```
 * manifest.json                      BackupManifest
 * sections/battery_profiles.json     BatteryProfilesSectionDto  (schema 2)
 * sections/home_appearance.json      HomeAppearanceSectionDto
 * sections/gps_points.json           GpsSectionDto
 * sections/dns_profiles.json         DnsSectionDto
 * sections/shortcuts.json            ShortcutsSectionDto
 * sections/battery_history.json      BatteryHistorySectionDto   (schema 2, opt-in only)
 * sections/battery.json              BatterySectionDto          (schema 1 legacy, read only)
 * ```
 *
 * Only the paths above are ever read; every other entry is reported as unsupported and ignored.
 * Enum values are stored as strings so a newer or foreign value is reported as an unsupported
 * item instead of failing the whole section.
 *
 * Schema history:
 * - 1: legacy battery preferences (`battery`), Home/appearance, GPS points, DNS profiles, shortcuts.
 * - 2: `battery_profiles` (built-in and custom profiles from the `battery_profiles` table) replaces
 *   `battery`, which is still imported and converted to built-in profiles; optional
 *   `battery_history` (session summaries and notes), never preselected.
 */
enum class BackupSection(
    val key: String,
    val entryPath: String,
    /** Personal data excluded by default (CFG-003): shown separately, never preselected. */
    val optIn: Boolean = false,
) {
    BATTERY_PROFILES("battery_profiles", "sections/battery_profiles.json"),
    HOME_APPEARANCE("home_appearance", "sections/home_appearance.json"),
    GPS_POINTS("gps_points", "sections/gps_points.json"),
    DNS_PROFILES("dns_profiles", "sections/dns_profiles.json"),
    SHORTCUTS("shortcuts", "sections/shortcuts.json"),
    BATTERY_HISTORY("battery_history", "sections/battery_history.json", optIn = true),
    ;

    companion object {
        /** Schema-1 battery section (legacy preferences); imported as [BATTERY_PROFILES]. */
        const val LEGACY_BATTERY_KEY = "battery"
        const val LEGACY_BATTERY_PATH = "sections/battery.json"

        /** Sections offered in the standard part of the export checklist. */
        val standard: List<BackupSection> get() = entries.filterNot { it.optIn }
        val optional: List<BackupSection> get() = entries.filter { it.optIn }

        fun fromKey(key: String): BackupSection? = entries.firstOrNull { it.key == key }
        fun fromPath(path: String): BackupSection? = entries.firstOrNull { it.entryPath == path }
    }
}

/**
 * Data categories that the standard export does not contain (CFG-003, ADR-015).
 * [BATTERY_HISTORY] can be added on purpose through the opt-in [BackupSection.BATTERY_HISTORY].
 */
enum class BackupExclusion(val key: String) {
    BATTERY_HISTORY("battery_history"),
    USAGE_DATA("usage_data"),
    PERMISSION_GRANTS("permission_grants"),
    SHIZUKU_STATE("shizuku_state"),
    INSTALLED_APP_DATA("installed_app_data"),
    HOTSPOT_PASSWORDS("hotspot_passwords"),
    TELEMETRY("telemetry"),
    CUSTOM_ICON_FILES("custom_icon_files"),
}

@Serializable
data class BackupManifest(
    val format: String = BackupArchive.FORMAT_ID,
    val schemaVersion: Int,
    val appVersion: String,
    /** ISO-8601 UTC instant. */
    val createdAt: String,
    val sections: List<String>,
    val entries: List<ManifestEntry>,
    val excluded: List<String> = emptyList(),
)

@Serializable
data class ManifestEntry(
    val path: String,
    val section: String,
    val sizeBytes: Long,
    /** Lower-case hex SHA-256 of the entry bytes. */
    val sha256: String,
)

// --- Section payloads -----------------------------------------------------------------------

/** Schema-1 battery section: the pre-v6 preferences. Read only; converted to built-in profiles. */
@Serializable
data class BatterySectionDto(
    val savingsTriggerPercent: Int,
    val savingsResetPercent: Int,
    val emergencyTriggerPercent: Int,
    val emergencyResetPercent: Int,
    val notteScheduleEnabled: Boolean,
    val notteScreenOffEnabled: Boolean,
    val notteStartTime: String,
    val notteEndTime: String,
    val screenOffDelayMinutes: Int,
    val protectedApps: List<String> = emptyList(),
    val appRules: List<AppRuleDto> = emptyList(),
)

@Serializable
data class AppRuleDto(
    val packageName: String,
    val profile: String,
    val level: String,
)

/** Schema 2: every battery profile plus the global protected-app list. */
@Serializable
data class BatteryProfilesSectionDto(
    val profiles: List<BatteryProfileDto> = emptyList(),
    val protectedApps: List<String> = emptyList(),
)

@Serializable
data class BatteryProfileDto(
    /** Local id; built-in profiles are matched by [builtInType] instead. */
    val id: String,
    /** Blank for a built-in profile that keeps its localized label. */
    val name: String = "",
    val iconKey: String = "",
    val colorKey: String = "",
    /** `ProfileType` name of a built-in profile, null for a custom profile. */
    val builtInType: String? = null,
    val enabled: Boolean = true,
    /**
     * Each trigger as the JSON object written by `ProfileTrigger`'s serializer (`type`
     * discriminator), decoded one by one so an unknown trigger type is skipped, not fatal.
     */
    val triggers: List<JsonObject> = emptyList(),
    val appRules: List<ProfileAppRuleDto> = emptyList(),
    val offerBatterySaver: Boolean = false,
    val sortOrder: Int = 0,
)

@Serializable
data class ProfileAppRuleDto(
    val packageName: String,
    val level: String,
)

/**
 * Schema 2, opt-in: battery session summaries with their notes/tags. Health snapshots are not
 * stored by this version of Able, so there is nothing else to export.
 */
@Serializable
data class BatteryHistorySectionDto(
    val sessions: List<BatterySessionDto> = emptyList(),
)

@Serializable
data class BatterySessionDto(
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val isCharging: Boolean,
    val startLevelPercent: Int,
    val endLevelPercent: Int,
    val avgTemperatureCelsius: Float,
    val maxTemperatureCelsius: Float,
    val avgCurrentMilliAmperes: Float? = null,
    val avgVoltageMillivolts: Float? = null,
    val avgPowerMilliWatts: Float? = null,
    val screenOnDurationMs: Long = 0,
    val screenOffDurationMs: Long = 0,
    val deepSleepDurationMs: Long = 0,
    val hasThermalAlert: Boolean = false,
    val isPartial: Boolean = false,
    val screenOnDropPercent: Int? = null,
    val screenOffDropPercent: Int? = null,
    val note: BatterySessionNoteDto? = null,
)

@Serializable
data class BatterySessionNoteDto(
    val text: String = "",
    /** Session tag keys (`travel`, `gaming`, …); unknown keys are ignored by the Battery screen. */
    val tags: List<String> = emptyList(),
    val createdAtEpochMs: Long,
)

@Serializable
data class HomeAppearanceSectionDto(
    val cards: List<HomeCardDto> = emptyList(),
    val themeMode: String,
    /** BCP-47 tag (`it`, `en`) or null for "System default". */
    val languageTag: String? = null,
)

@Serializable
data class HomeCardDto(
    val type: String,
    val visible: Boolean,
    val order: Int,
)

@Serializable
data class GpsSectionDto(val points: List<GpsPointDto> = emptyList())

@Serializable
data class GpsPointDto(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float,
    val timestampEpochMs: Long,
    val label: String,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val address: String? = null,
)

@Serializable
data class DnsSectionDto(val profiles: List<DnsProfileDto> = emptyList())

@Serializable
data class DnsProfileDto(
    val id: String,
    val name: String,
    val mode: String,
    val hostname: String = "",
    val isDefault: Boolean = false,
)

@Serializable
data class ShortcutsSectionDto(
    val collections: List<ShortcutCollectionDto> = emptyList(),
    val shortcuts: List<ShortcutDto> = emptyList(),
)

@Serializable
data class ShortcutCollectionDto(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val createdAtEpochMs: Long,
)

@Serializable
data class ShortcutDto(
    val id: String,
    val label: String,
    val targetType: String,
    val targetData: String,
    val targetPackage: String? = null,
    val collectionId: String? = null,
    val iconType: String = "DEFAULT",
    val iconValue: String? = null,
    val iconBackgroundColorHex: String? = null,
    val sortOrder: Int = 0,
    val pinnedToLauncher: Boolean = false,
    val createdAtEpochMs: Long,
)

/** Decoded content of an archive; a null section was not included. */
data class BackupPayload(
    val batteryProfiles: BatteryProfilesSectionDto? = null,
    val homeAppearance: HomeAppearanceSectionDto? = null,
    val gps: GpsSectionDto? = null,
    val dns: DnsSectionDto? = null,
    val shortcuts: ShortcutsSectionDto? = null,
    val batteryHistory: BatteryHistorySectionDto? = null,
    /**
     * Schema-1 `battery` section, read only when [batteryProfiles] is absent. It is imported as
     * [BackupSection.BATTERY_PROFILES] (converted to built-in profiles) and never written.
     */
    val legacyBattery: BatterySectionDto? = null,
) {
    val sections: Set<BackupSection>
        get() = buildSet {
            if (batteryProfiles != null || legacyBattery != null) add(BackupSection.BATTERY_PROFILES)
            if (homeAppearance != null) add(BackupSection.HOME_APPEARANCE)
            if (gps != null) add(BackupSection.GPS_POINTS)
            if (dns != null) add(BackupSection.DNS_PROFILES)
            if (shortcuts != null) add(BackupSection.SHORTCUTS)
            if (batteryHistory != null) add(BackupSection.BATTERY_HISTORY)
        }
}
