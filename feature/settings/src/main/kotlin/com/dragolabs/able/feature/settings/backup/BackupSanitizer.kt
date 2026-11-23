package com.dragolabs.able.feature.settings.backup

import com.dragolabs.able.core.data.battery.BuiltInProfiles
import com.dragolabs.able.core.data.battery.ProfileValidation
import com.dragolabs.able.core.database.UserPreferences
import com.dragolabs.able.core.model.AppInterventionLevel
import com.dragolabs.able.core.model.BatteryProfile
import com.dragolabs.able.core.model.DnsProfile
import com.dragolabs.able.core.model.GpsPoint
import com.dragolabs.able.core.model.HomeCardConfig
import com.dragolabs.able.core.model.HomeCardType
import com.dragolabs.able.core.model.PrivateDnsMode
import com.dragolabs.able.core.model.ProfileColor
import com.dragolabs.able.core.model.ProfileIcon
import com.dragolabs.able.core.model.ProfileTrigger
import com.dragolabs.able.core.model.ProfileType
import com.dragolabs.able.core.model.ShortcutCollection
import com.dragolabs.able.core.model.ShortcutIconConfig
import com.dragolabs.able.core.model.ShortcutIconType
import com.dragolabs.able.core.model.ShortcutItem
import com.dragolabs.able.core.model.ShortcutTargetType
import com.dragolabs.able.core.model.ThemeMode
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

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

/** Validated battery profiles; [legacy] when converted from a schema-1 `battery` section. */
data class SanitizedBatteryProfiles(
    /** Built-ins carry their local id ([BuiltInProfiles.idFor]); manual activation is never imported. */
    val profiles: List<BatteryProfile>,
    val protectedApps: Set<String>,
    val rejected: Int,
    val legacy: Boolean = false,
) {
    val packages: Set<String> get() = profiles.flatMap { it.appRules.keys }.toSet() + protectedApps
}

/** Validated battery history; sessions are unique by start/end. */
data class SanitizedHistory(
    val sessions: List<BatterySessionDto>,
    val notes: Int,
    val rejected: Int,
    /** Sessions repeated inside the file itself (same start and end). */
    val duplicatesInFile: Int,
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

    const val MAX_PROFILES = 200
    const val MAX_TRIGGERS = 16
    const val MAX_HISTORY_SESSIONS = 20_000
    const val MAX_SESSION_TAGS = 16

    /** Earliest accepted session timestamp (2008-01-01T00:00:00Z, before any Android device). */
    const val MIN_SESSION_EPOCH_MS = 1_199_145_600_000L
    /** A session may not last longer than this. */
    const val MAX_SESSION_DURATION_MS = 31L * 24 * 60 * 60 * 1000
    /** Clock-skew tolerance for sessions that end "in the future". */
    const val FUTURE_TOLERANCE_MS = 24L * 60 * 60 * 1000

    private val tagRegex = Regex("^[a-z][a-z0-9_]{0,31}$")

    /** Same JSON shape as `BatteryProfileMapper` (`type` discriminator). */
    private val triggerJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun isValidPackage(name: String): Boolean = name.length <= 255 && packageRegex.matches(name)

    fun encodeTrigger(trigger: ProfileTrigger): JsonObject =
        triggerJson.encodeToJsonElement(ProfileTrigger.serializer(), trigger).jsonObject

    /** Null for an unknown trigger type or malformed fields. */
    fun decodeTrigger(obj: JsonObject): ProfileTrigger? = try {
        triggerJson.decodeFromJsonElement(ProfileTrigger.serializer(), obj)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    fun profileToDto(profile: BatteryProfile): BatteryProfileDto = BatteryProfileDto(
        id = profile.id,
        name = profile.name,
        iconKey = profile.iconKey,
        colorKey = profile.colorKey,
        builtInType = profile.builtInType?.name,
        enabled = profile.enabled,
        triggers = profile.triggers.map(::encodeTrigger),
        appRules = profile.restrictiveRules.toSortedMap().map { (pkg, level) -> ProfileAppRuleDto(pkg, level.name) },
        offerBatterySaver = profile.offerBatterySaver,
        sortOrder = profile.sortOrder,
    )

    /**
     * Schema-2 profiles. Built-ins are matched by `builtInType` (one per type) and get their local
     * id; custom ids must be valid and unique. Invalid triggers and app rules are dropped and
     * counted; a profile that fails profile-level validation is dropped entirely.
     */
    fun batteryProfiles(dto: BatteryProfilesSectionDto): SanitizedBatteryProfiles {
        var rejected = (dto.profiles.size - MAX_PROFILES).coerceAtLeast(0) +
            (dto.protectedApps.size - MAX_ITEMS).coerceAtLeast(0)
        val ids = mutableSetOf<String>()
        val types = mutableSetOf<ProfileType>()
        val profiles = dto.profiles.take(MAX_PROFILES).mapNotNull { p ->
            val type = p.builtInType?.let { name ->
                ProfileType.entries.firstOrNull { it.name == name } ?: run {
                    rejected++
                    return@mapNotNull null
                }
            }
            val id = if (type != null) BuiltInProfiles.idFor(type) else p.id
            val idValid = if (type != null) {
                types.add(type)
            } else {
                idRegex.matches(id) && !id.startsWith(BUILT_IN_ID_PREFIX) && ids.add(id)
            }
            if (!idValid || p.name.length > MAX_LABEL) {
                rejected++
                return@mapNotNull null
            }
            rejected += (p.triggers.size - MAX_TRIGGERS).coerceAtLeast(0) + (p.appRules.size - MAX_ITEMS).coerceAtLeast(0)
            val triggers = p.triggers.take(MAX_TRIGGERS).mapNotNull { obj -> decodeTrigger(obj).also { if (it == null) rejected++ } }
            val rules = linkedMapOf<String, AppInterventionLevel>()
            p.appRules.take(MAX_ITEMS).forEach { r ->
                val level = AppInterventionLevel.entries.firstOrNull { it.name == r.level }
                if (level == null || !isValidPackage(r.packageName) || r.packageName in rules) {
                    rejected++
                } else if (level != AppInterventionLevel.OSSERVA) {
                    // Osserva is the no-change default and is never stored.
                    rules[r.packageName] = level
                }
            }
            val candidate = BatteryProfile(
                id = id,
                name = p.name.trim(),
                iconKey = ProfileIcon.fromKey(p.iconKey).key,
                colorKey = ProfileColor.fromKey(p.colorKey).key,
                builtInType = type,
                enabled = p.enabled,
                manualActive = false,
                triggers = triggers,
                appRules = rules,
                offerBatterySaver = p.offerBatterySaver,
                sortOrder = p.sortOrder,
            )
            val issues = ProfileValidation.validate(candidate)
            if (issues.any { it.triggerIndex == null }) {
                rejected++
                return@mapNotNull null
            }
            val badTriggers = issues.mapNotNull { it.triggerIndex }.toSet()
            rejected += badTriggers.size
            candidate.copy(triggers = triggers.filterIndexed { index, _ -> index !in badTriggers })
        }
        val protectedApps = dto.protectedApps.take(MAX_ITEMS).filter { pkg ->
            isValidPackage(pkg).also { if (!it) rejected++ }
        }.toSet()
        return SanitizedBatteryProfiles(profiles, protectedApps, rejected)
    }

    /**
     * Converts a schema-1 `battery` section to the five built-in profiles with
     * [BuiltInProfiles.fromLegacy]. Values the legacy section does not carry (the Risparmio and
     * Emergenza trigger switches), or carries with invalid values, keep the [current] built-in's
     * value so the conversion never changes them silently.
     */
    fun legacyBatteryProfiles(dto: BatterySectionDto, current: List<BatteryProfile>): SanitizedBatteryProfiles {
        val s = battery(dto)
        val currentByType = current.filter { it.builtInType != null }.associateBy { it.builtInType!! }
        fun batteryTriggerEnabled(type: ProfileType) =
            currentByType[type]?.triggers?.filterIsInstance<ProfileTrigger.BatteryBelow>()?.firstOrNull()?.enabled ?: false
        val sc = s.scalars
        val base = UserPreferences(
            savingsTriggerEnabled = batteryTriggerEnabled(ProfileType.RISPARMIO),
            emergencyTriggerEnabled = batteryTriggerEnabled(ProfileType.EMERGENZA),
        )
        val prefs = if (sc == null) {
            base
        } else {
            base.copy(
                savingsTriggerPercent = sc.savingsTrigger,
                savingsResetPercent = sc.savingsReset,
                emergencyTriggerPercent = sc.emergencyTrigger,
                emergencyResetPercent = sc.emergencyReset,
                notteScheduleEnabled = sc.notteScheduleEnabled,
                notteScreenOffEnabled = sc.notteScreenOffEnabled,
                notteStartTime = sc.notteStart,
                notteEndTime = sc.notteEnd,
                screenOffDelayMinutes = sc.screenOffDelayMinutes,
            )
        }.copy(
            appBatteryRules = s.rules.groupBy { it.packageName }
                .mapValues { (_, rules) -> rules.associate { it.profile to it.level } },
            selectedBatteryProfile = ProfileType.NORMALE,
        )
        val profiles = BuiltInProfiles.fromLegacy(prefs).map { converted ->
            val local = converted.builtInType?.let { currentByType[it] }
            converted.copy(
                manualActive = false,
                // Invalid legacy thresholds/schedule: keep the current triggers instead of defaults.
                triggers = if (sc == null && local != null) local.triggers else converted.triggers,
                name = local?.name ?: converted.name,
                iconKey = local?.iconKey ?: converted.iconKey,
                colorKey = local?.colorKey ?: converted.colorKey,
                enabled = local?.enabled ?: converted.enabled,
                sortOrder = local?.sortOrder ?: converted.sortOrder,
            )
        }
        return SanitizedBatteryProfiles(profiles, s.protectedApps, s.rejected, legacy = true)
    }

    /**
     * Battery history (CFG-004): timestamps, levels, temperatures, electrical values and
     * durations must be plausible; sessions are unique by start/end. Notes keep only trimmed text
     * and well-formed tag keys.
     */
    fun batteryHistory(dto: BatteryHistorySectionDto, nowEpochMs: Long): SanitizedHistory {
        var rejected = (dto.sessions.size - MAX_HISTORY_SESSIONS).coerceAtLeast(0)
        var duplicates = 0
        var notes = 0
        val seen = mutableSetOf<Pair<Long, Long>>()
        val sessions = dto.sessions.take(MAX_HISTORY_SESSIONS).mapNotNull { s ->
            if (!isValidSession(s, nowEpochMs)) {
                rejected++
                return@mapNotNull null
            }
            if (!seen.add(s.startTimeEpochMs to s.endTimeEpochMs)) {
                duplicates++
                return@mapNotNull null
            }
            val note = s.note?.let { n ->
                val text = n.text.trim()
                val tags = n.tags.filter { tagRegex.matches(it) }.distinct().take(MAX_SESSION_TAGS)
                when {
                    text.length > MAX_TEXT || n.createdAtEpochMs <= 0 -> { rejected++; null }
                    text.isEmpty() && tags.isEmpty() -> null
                    else -> BatterySessionNoteDto(text, tags, n.createdAtEpochMs)
                }
            }
            if (note != null) notes++
            s.copy(note = note)
        }
        return SanitizedHistory(sessions, notes, rejected, duplicates)
    }

    private fun isValidSession(s: BatterySessionDto, nowEpochMs: Long): Boolean {
        val duration = s.endTimeEpochMs - s.startTimeEpochMs
        fun Float?.inRange(range: ClosedFloatingPointRange<Float>) = this == null || (isFinite() && this in range)
        fun Long.durationValid() = this in 0..(duration + 60_000)
        return s.startTimeEpochMs >= MIN_SESSION_EPOCH_MS &&
            s.endTimeEpochMs <= nowEpochMs + FUTURE_TOLERANCE_MS &&
            duration in 0..MAX_SESSION_DURATION_MS &&
            s.startLevelPercent in 0..100 && s.endLevelPercent in 0..100 &&
            s.avgTemperatureCelsius.isFinite() && s.avgTemperatureCelsius in -40f..100f &&
            s.maxTemperatureCelsius.isFinite() && s.maxTemperatureCelsius in -40f..100f &&
            s.avgCurrentMilliAmperes.inRange(-100_000f..100_000f) &&
            s.avgVoltageMillivolts.inRange(0f..30_000f) &&
            s.avgPowerMilliWatts.inRange(-1_000_000f..1_000_000f) &&
            s.screenOnDurationMs.durationValid() && s.screenOffDurationMs.durationValid() &&
            s.deepSleepDurationMs.durationValid() &&
            (s.screenOnDropPercent == null || s.screenOnDropPercent in 0..100) &&
            (s.screenOffDropPercent == null || s.screenOffDropPercent in 0..100)
    }

    private const val BUILT_IN_ID_PREFIX = "builtin_"

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
