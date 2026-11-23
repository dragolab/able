package com.dragolabs.able.feature.settings.backup

import com.dragolabs.able.core.model.AppInterventionLevel
import com.dragolabs.able.core.model.DnsProfile
import com.dragolabs.able.core.model.GpsPoint
import com.dragolabs.able.core.model.HomeCardConfig
import com.dragolabs.able.core.model.HomeCardType
import com.dragolabs.able.core.model.PrivateDnsMode
import com.dragolabs.able.core.model.ProfileType
import com.dragolabs.able.core.model.ShortcutCollection
import com.dragolabs.able.core.model.ShortcutIconConfig
import com.dragolabs.able.core.model.ShortcutIconType
import com.dragolabs.able.core.model.ShortcutItem
import com.dragolabs.able.core.model.ShortcutTargetType
import com.dragolabs.able.core.model.ThemeMode

data class AppRule(val packageName: String, val profile: ProfileType, val level: AppInterventionLevel)

data class BatteryScalars(
    val savingsTrigger: Int,
    val savingsReset: Int,
    val emergencyTrigger: Int,
    val emergencyReset: Int,
    val notteScheduleEnabled: Boolean,
    val notteScreenOffEnabled: Boolean,
    val notteStart: String,
    val notteEnd: String,
    val screenOffDelayMinutes: Int,
)

data class SanitizedBattery(
    /** Null when thresholds or schedule values are out of range; those values are then skipped. */
    val scalars: BatteryScalars?,
    val protectedApps: Set<String>,
    val rules: List<AppRule>,
    val rejected: Int,
)

data class SanitizedHomeAppearance(
    val cards: List<HomeCardConfig>,
    val themeMode: ThemeMode?,
    val languageValid: Boolean,
    val languageTag: String?,
    val rejected: Int,
)

data class SanitizedList<T>(val items: List<T>, val rejected: Int)

data class SanitizedShortcuts(
    val collections: List<ShortcutCollection>,
    val shortcuts: List<ShortcutItem>,
    /** Shortcuts that were pinned on the source launcher and must be pinned again (CFG-007). */
    val needRepin: Int,
    /** Custom gallery icons replaced by the default icon because icon files are not exported. */
    val iconsReset: Int,
    val rejected: Int,
)

/**
 * Value-range validation for imported sections (CFG-004). Every function is pure: invalid items
 * are dropped and counted, never coerced into a different meaning.
 */
object BackupSanitizer {
    const val MAX_ITEMS = 5_000
    const val MAX_LABEL = 200
    const val MAX_TEXT = 4_000
    const val MAX_TARGET = 2_048
    val SUPPORTED_LANGUAGES = setOf("it", "en")

    private val packageRegex = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    private val timeRegex = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$")
    private val idRegex = Regex("^[A-Za-z0-9_.:-]{1,128}$")
    private val colorRegex = Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")
    private val hostnameRegex =
        Regex("^(?=.{1,253}$)[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$")

    fun isValidPackage(name: String): Boolean = name.length <= 255 && packageRegex.matches(name)

    fun battery(dto: BatterySectionDto): SanitizedBattery {
        var rejected = 0
        val scalars = with(dto) {
            val thresholdsValid = emergencyTriggerPercent in 1..24 &&
                savingsTriggerPercent in (emergencyTriggerPercent + 1)..98 &&
                emergencyResetPercent in (emergencyTriggerPercent + 1)..savingsTriggerPercent &&
                savingsResetPercent in (savingsTriggerPercent + 1)..100
            val scheduleValid = timeRegex.matches(notteStartTime) && timeRegex.matches(notteEndTime) &&
                notteStartTime != notteEndTime
            val delayValid = screenOffDelayMinutes in 5..120
            if (thresholdsValid && scheduleValid && delayValid) {
                BatteryScalars(
                    savingsTriggerPercent, savingsResetPercent, emergencyTriggerPercent, emergencyResetPercent,
                    notteScheduleEnabled, notteScreenOffEnabled, notteStartTime, notteEndTime, screenOffDelayMinutes,
                )
            } else {
                rejected++
                null
            }
        }
        val protectedApps = dto.protectedApps.take(MAX_ITEMS).filter { valid ->
            isValidPackage(valid).also { if (!it) rejected++ }
        }.toSet()
        val seen = mutableSetOf<Pair<String, ProfileType>>()
        val rules = dto.appRules.take(MAX_ITEMS).mapNotNull { rule ->
            val profile = ProfileType.entries.firstOrNull { it.name == rule.profile }
            val level = AppInterventionLevel.entries.firstOrNull { it.name == rule.level }
            if (profile == null || level == null || !isValidPackage(rule.packageName) || !seen.add(rule.packageName to profile)) {
                rejected++
                null
            } else {
                AppRule(rule.packageName, profile, level)
            }
        }
        rejected += (dto.protectedApps.size - MAX_ITEMS).coerceAtLeast(0) + (dto.appRules.size - MAX_ITEMS).coerceAtLeast(0)
        return SanitizedBattery(scalars, protectedApps, rules, rejected)
    }

    fun homeAppearance(dto: HomeAppearanceSectionDto): SanitizedHomeAppearance {
        var rejected = 0
        val seen = mutableSetOf<HomeCardType>()
        val cards = dto.cards.take(64).mapNotNull { card ->
            val type = HomeCardType.entries.firstOrNull { it.name == card.type }
            if (type == null || !seen.add(type)) {
                rejected++
                null
            } else {
                card to type
            }
        }.sortedBy { it.first.order }
            .mapIndexed { index, (card, type) -> HomeCardConfig(type, card.visible, index) }
        val theme = ThemeMode.entries.firstOrNull { it.name == dto.themeMode }
        if (theme == null) rejected++
        val languageValid = dto.languageTag == null || dto.languageTag in SUPPORTED_LANGUAGES
        if (!languageValid) rejected++
        return SanitizedHomeAppearance(cards, theme, languageValid, dto.languageTag.takeIf { languageValid }, rejected)
    }

    fun gps(dto: GpsSectionDto): SanitizedList<GpsPoint> {
        var rejected = (dto.points.size - MAX_ITEMS).coerceAtLeast(0)
        val points = dto.points.take(MAX_ITEMS).mapNotNull { p ->
            val valid = p.latitude.isFinite() && p.latitude in -90.0..90.0 &&
                p.longitude.isFinite() && p.longitude in -180.0..180.0 &&
                (p.altitudeMeters == null || p.altitudeMeters.isFinite()) &&
                p.accuracyMeters.isFinite() && p.accuracyMeters >= 0f &&
                p.timestampEpochMs > 0 &&
                p.label.length <= MAX_LABEL && p.notes.length <= MAX_TEXT &&
                (p.address?.length ?: 0) <= MAX_TEXT
            if (!valid) {
                rejected++
                null
            } else {
                GpsPoint(
                    id = 0,
                    latitude = p.latitude,
                    longitude = p.longitude,
                    altitudeMeters = p.altitudeMeters,
                    accuracyMeters = p.accuracyMeters,
                    timestampEpochMs = p.timestampEpochMs,
                    label = p.label,
                    notes = p.notes,
                    // Tags are stored comma-separated; commas inside a tag would split it.
                    tags = p.tags.take(32).map { it.replace(",", " ").trim().take(MAX_LABEL) }.filter { it.isNotEmpty() },
                    reverseGeocodedAddress = p.address,
                )
            }
        }
        return SanitizedList(points, rejected)
    }

    fun dns(dto: DnsSectionDto): SanitizedList<DnsProfile> {
        var rejected = (dto.profiles.size - MAX_ITEMS).coerceAtLeast(0)
        val seen = mutableSetOf<String>()
        val profiles = dto.profiles.take(MAX_ITEMS).mapNotNull { p ->
            val mode = PrivateDnsMode.entries.firstOrNull { it.name == p.mode }
            val hostnameValid = when (mode) {
                PrivateDnsMode.HOSTNAME -> hostnameRegex.matches(p.hostname)
                null -> false
                else -> true
            }
            if (mode == null || !hostnameValid || !idRegex.matches(p.id) || p.name.isBlank() ||
                p.name.length > MAX_LABEL || !seen.add(p.id)
            ) {
                rejected++
                null
            } else {
                DnsProfile(p.id, p.name, mode, if (mode == PrivateDnsMode.HOSTNAME) p.hostname else "", p.isDefault)
            }
        }
        return SanitizedList(profiles, rejected)
    }

    fun shortcuts(dto: ShortcutsSectionDto): SanitizedShortcuts {
        var rejected = (dto.collections.size - MAX_ITEMS).coerceAtLeast(0) +
            (dto.shortcuts.size - MAX_ITEMS).coerceAtLeast(0)
        val collectionIds = mutableSetOf<String>()
        val collections = dto.collections.take(MAX_ITEMS).mapNotNull { c ->
            if (!idRegex.matches(c.id) || c.name.isBlank() || c.name.length > MAX_LABEL || !collectionIds.add(c.id)) {
                rejected++
                null
            } else {
                ShortcutCollection(c.id, c.name, c.sortOrder, c.createdAtEpochMs)
            }
        }
        var needRepin = 0
        var iconsReset = 0
        val shortcutIds = mutableSetOf<String>()
        val shortcuts = dto.shortcuts.take(MAX_ITEMS).mapNotNull { s ->
            val target = ShortcutTargetType.entries.firstOrNull { it.name == s.targetType }
            val iconType = ShortcutIconType.entries.firstOrNull { it.name == s.iconType } ?: ShortcutIconType.DEFAULT
            val packageValid = s.targetPackage == null || isValidPackage(s.targetPackage)
            val targetValid = when (target) {
                ShortcutTargetType.APPLICATION -> s.targetPackage != null
                ShortcutTargetType.ACTIVITY -> s.targetPackage != null && s.targetData.isNotBlank()
                null -> false
                else -> s.targetData.isNotBlank()
            }
            if (target == null || !targetValid || !packageValid || !idRegex.matches(s.id) || s.label.isBlank() ||
                s.label.length > MAX_LABEL || s.targetData.length > MAX_TARGET || !shortcutIds.add(s.id)
            ) {
                rejected++
                return@mapNotNull null
            }
            if (s.pinnedToLauncher) needRepin++
            val icon = when (iconType) {
                ShortcutIconType.CUSTOM_GALLERY -> {
                    iconsReset++
                    ShortcutIconConfig()
                }
                ShortcutIconType.TEXT_OR_EMOJI -> ShortcutIconConfig(
                    iconType = iconType,
                    iconValue = s.iconValue?.take(16),
                    backgroundColorHex = s.iconBackgroundColorHex?.takeIf { colorRegex.matches(it) },
                )
                ShortcutIconType.DEFAULT -> ShortcutIconConfig(
                    backgroundColorHex = s.iconBackgroundColorHex?.takeIf { colorRegex.matches(it) },
                )
            }
            ShortcutItem(
                id = s.id,
                label = s.label,
                targetType = target,
                targetData = s.targetData,
                targetPackage = s.targetPackage,
                collectionId = s.collectionId?.takeIf { it in collectionIds },
                iconConfig = icon,
                sortOrder = s.sortOrder,
                // Launcher pins are never recreated silently; the user pins them again.
                isPinnedToLauncher = false,
                createdAtEpochMs = s.createdAtEpochMs,
            )
        }
        return SanitizedShortcuts(collections, shortcuts, needRepin, iconsReset, rejected)
    }
}
