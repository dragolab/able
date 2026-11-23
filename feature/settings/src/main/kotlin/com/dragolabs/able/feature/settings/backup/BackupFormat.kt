package com.dragolabs.able.feature.settings.backup

import kotlinx.serialization.Serializable

/**
 * `.ablebackup` schema (CFG-002, ADR-006).
 *
 * The archive is a ZIP file with a fixed layout:
 *
 * ```
 * manifest.json                      BackupManifest
 * sections/battery.json              BatterySectionDto
 * sections/home_appearance.json      HomeAppearanceSectionDto
 * sections/gps_points.json           GpsSectionDto
 * sections/dns_profiles.json         DnsSectionDto
 * sections/shortcuts.json            ShortcutsSectionDto
 * ```
 *
 * Only the paths above are ever read; every other entry is reported as unsupported and ignored.
 * Enum values are stored as strings so a newer or foreign value is reported as an unsupported
 * item instead of failing the whole section.
 */
enum class BackupSection(val key: String, val entryPath: String) {
    BATTERY("battery", "sections/battery.json"),
    HOME_APPEARANCE("home_appearance", "sections/home_appearance.json"),
    GPS_POINTS("gps_points", "sections/gps_points.json"),
    DNS_PROFILES("dns_profiles", "sections/dns_profiles.json"),
    SHORTCUTS("shortcuts", "sections/shortcuts.json"),
    ;

    companion object {
        fun fromKey(key: String): BackupSection? = entries.firstOrNull { it.key == key }
        fun fromPath(path: String): BackupSection? = entries.firstOrNull { it.entryPath == path }
    }
}

/** Data categories that the standard export never contains (CFG-003, ADR-015). */
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

// --- Section payloads (schema version 1) ---------------------------------------------------

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
    val battery: BatterySectionDto? = null,
    val homeAppearance: HomeAppearanceSectionDto? = null,
    val gps: GpsSectionDto? = null,
    val dns: DnsSectionDto? = null,
    val shortcuts: ShortcutsSectionDto? = null,
) {
    val sections: Set<BackupSection>
        get() = buildSet {
            if (battery != null) add(BackupSection.BATTERY)
            if (homeAppearance != null) add(BackupSection.HOME_APPEARANCE)
            if (gps != null) add(BackupSection.GPS_POINTS)
            if (dns != null) add(BackupSection.DNS_PROFILES)
            if (shortcuts != null) add(BackupSection.SHORTCUTS)
        }
}
