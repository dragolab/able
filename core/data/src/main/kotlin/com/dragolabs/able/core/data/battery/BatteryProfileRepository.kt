package com.dragolabs.able.core.data.battery

import com.dragolabs.able.core.data.BatteryStatusRepository
import com.dragolabs.able.core.database.UserPreferences
import com.dragolabs.able.core.database.UserPreferencesRepository
import com.dragolabs.able.core.database.dao.BatteryProfileDao
import com.dragolabs.able.core.model.AppInterventionLevel
import com.dragolabs.able.core.model.BatteryProfile
import com.dragolabs.able.core.model.BatteryStatus
import com.dragolabs.able.core.model.ProfileType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * User-editable battery profiles (owner decision 2026-11-22) and their evaluation. Shared by the
 * Battery screen, the live monitor service and the Apps detail pane.
 *
 * Evaluation only decides which profiles are active and which per-app action would apply; no app
 * is changed yet (the automation engine for app actions is not implemented, BAT-008).
 */
class BatteryProfileRepository(
    private val dao: BatteryProfileDao,
    private val preferences: UserPreferencesRepository,
    private val battery: BatteryStatusRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val seedMutex = Mutex()

    @Volatile
    private var seeded = false

    /** Battery-trigger hysteresis shared by every evaluator in this process (screen and monitor). */
    private val latches = AtomicReference<Set<String>>(emptySet())

    /** All profiles in the user's order; the built-ins are seeded on first access. */
    val profiles: Flow<List<BatteryProfile>> = flow {
        ensureSeeded()
        emitAll(dao.observeAll().map { rows -> rows.map(BatteryProfileMapper::toModel) })
    }.distinctUntilChanged()

    /** Local time, re-emitted at each minute boundary while collected (schedule triggers). */
    private val clockTicks: Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now().withSecond(0).withNano(0))
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }.distinctUntilChanged()

    /**
     * Foreground evaluation for screens: the screen is on while they are visible, so screen-off
     * triggers are not met here (the live monitor evaluates them). Keeps the legacy
     * `selectedBatteryProfile` preference in sync for older readers (Home card, widget).
     */
    val evaluation: Flow<ProfileEvaluation> = combine(
        profiles,
        battery.status,
        preferences.userPreferencesFlow,
        clockTicks,
    ) { list, status, prefs, now ->
        evaluate(list, status, prefs, now, screenOffForMs = null)
    }.distinctUntilChanged().onEach { syncLegacySelection(it) }

    /** Evaluates [profiles] now and advances the shared hysteresis state. */
    fun evaluate(
        profiles: List<BatteryProfile>,
        status: BatteryStatus?,
        prefs: UserPreferences,
        now: LocalDateTime = LocalDateTime.now(),
        screenOffForMs: Long?,
    ): ProfileEvaluation {
        val inputs = ProfileInputs(
            levelPercent = status?.levelPercent,
            onExternalPower = status != null && (status.isPluggedIn || status.isCharging),
            minuteOfDay = now.hour * 60 + now.minute,
            dayOfWeek = now.dayOfWeek.value,
            screenOffForMs = screenOffForMs,
            automationPaused = prefs.batteryAutomationPaused,
        )
        val result = ProfileEngine.evaluate(profiles, inputs, prefs.protectedAppPackages, latches.get())
        latches.set(result.latches)
        return result
    }

    /**
     * Compatibility: Home and the widget still read `selectedBatteryProfile`. It mirrors the first
     * active built-in profile, or Normale when none (or only custom profiles) are active.
     */
    suspend fun syncLegacySelection(evaluation: ProfileEvaluation) {
        val type = evaluation.active.firstNotNullOfOrNull { it.profile.builtInType } ?: ProfileType.NORMALE
        if (preferences.userPreferencesFlow.first().selectedBatteryProfile != type) {
            preferences.setSelectedBatteryProfile(type)
        }
    }

    suspend fun get(id: String): BatteryProfile? {
        ensureSeeded()
        return dao.get(id)?.let(BatteryProfileMapper::toModel)
    }

    /**
     * Inserts or replaces [profile]. A blank id creates a new custom profile at the end of the
     * list. Returns the stored profile. Built-in rules are mirrored to the legacy preferences so
     * the settings backup keeps working.
     */
    suspend fun save(profile: BatteryProfile): BatteryProfile {
        ensureSeeded()
        val stored = if (profile.id.isBlank()) {
            profile.copy(id = newId(), builtInType = null, sortOrder = dao.maxSortOrder() + 1)
        } else {
            profile
        }
        dao.upsert(BatteryProfileMapper.toEntity(stored, clock()))
        stored.builtInType?.let { type -> mirrorLegacyRules(type, previous = null, updated = stored) }
        return BatteryProfileMapper.toModel(BatteryProfileMapper.toEntity(stored, 0))
    }

    /** Copies a profile as a new custom profile named [name]. Its triggers start switched off (ADR-005). */
    suspend fun duplicate(id: String, name: String): BatteryProfile? {
        val source = get(id) ?: return null
        return save(
            source.copy(
                id = "",
                name = name,
                builtInType = null,
                manualActive = false,
                triggers = source.triggers.map { it.withEnabled(false) },
            ),
        )
    }

    /** Deletes a custom profile. Built-in profiles cannot be deleted; returns false then. */
    suspend fun delete(id: String): Boolean = dao.deleteCustom(id) > 0

    /** Restores a built-in profile to its factory default (all triggers off, no app rules). */
    suspend fun resetToDefault(id: String): BatteryProfile? {
        val current = get(id) ?: return null
        val type = current.builtInType ?: return null
        return save(BuiltInProfiles.defaultFor(type).copy(sortOrder = current.sortOrder))
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        ensureSeeded()
        dao.setEnabled(id, enabled, clock())
    }

    suspend fun setManualActive(id: String, active: Boolean) {
        ensureSeeded()
        dao.setManualActive(id, active, clock())
    }

    /** Sets one app's level in one profile; Osserva removes the rule. */
    suspend fun setAppRule(profileId: String, packageName: String, level: AppInterventionLevel) {
        val profile = get(profileId) ?: return
        val rules = profile.appRules.toMutableMap()
        if (level == AppInterventionLevel.OSSERVA) rules.remove(packageName) else rules[packageName] = level
        save(profile.copy(appRules = rules))
    }

    suspend fun setAutomationPaused(paused: Boolean) = preferences.setBatteryAutomationPaused(paused)

    private suspend fun mirrorLegacyRules(type: ProfileType, previous: BatteryProfile?, updated: BatteryProfile) {
        val legacy = preferences.userPreferencesFlow.first().appBatteryRules
        val packages = legacy.filterValues { type in it }.keys + updated.appRules.keys + previous?.appRules?.keys.orEmpty()
        packages.forEach { pkg ->
            val level = updated.appRules[pkg] ?: AppInterventionLevel.OSSERVA
            val old = legacy[pkg]?.get(type) ?: AppInterventionLevel.OSSERVA
            if (level != old) preferences.setAppBatteryRule(pkg, type, level)
        }
    }

    /** Seeds the built-in profiles from the pre-v6 preferences once (idempotent, never overwrites). */
    suspend fun ensureSeeded() {
        if (seeded) return
        seedMutex.withLock {
            if (seeded) return
            val existing = dao.getAll().mapTo(mutableSetOf()) { it.id }
            val missing = if (existing.isEmpty()) {
                BuiltInProfiles.fromLegacy(preferences.userPreferencesFlow.first())
            } else {
                // A built-in row can only be missing after a failed write; restore its default.
                BuiltInProfiles.defaults().filter { it.id !in existing }
            }
            if (missing.isNotEmpty()) dao.insertIfAbsent(missing.map { BatteryProfileMapper.toEntity(it, clock()) })
            seeded = true
        }
    }

    private fun newId(): String = "custom_" + UUID.randomUUID().toString()
}
