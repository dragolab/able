package com.dragolabs.able.core.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dragolabs.able.core.model.HapticsIntensity
import com.dragolabs.able.core.model.ThemeMode
import com.dragolabs.able.core.model.ProfileType
import com.dragolabs.able.core.model.AppInterventionLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Unit used to display battery temperatures (live monitor settings). */
enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

/** Layout of the live-monitor notification (MON-004). */
enum class MonitorNotificationLayout { COMPACT, DETAILED }

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "able_user_preferences")

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val languageOverride: String? = null,
    val batteryHistoryRetentionDays: Int = 90,
    val telemetryOptIn: Boolean = false,
    val baselineAlertEnabled: Boolean = false,
    val baselineAlertThresholdPercentPerHour: Float = 3.0f,
    val screenOffDelayMinutes: Int = 20,
    val isEmergencyActive: Boolean = false,
    val selectedBatteryProfile: ProfileType = ProfileType.NORMALE,
    // Every automatic trigger starts disabled; the user enables each one (ADR-005, 2026-11-22).
    val notteScheduleEnabled: Boolean = false,
    val notteScreenOffEnabled: Boolean = false,
    val notteStartTime: String = "00:30",
    val notteEndTime: String = "07:30",
    val protectedAppPackages: Set<String> = emptySet(),
    val batteryMonitorEnabled: Boolean = false,
    val batteryResetOnPlug: Boolean = false,
    val batteryResetOnBoot: Boolean = false,
    val batteryResetThresholdPercent: Int? = null,
    val batteryThermalAlertThresholdCelsius: Float = 45f,
    val appBatteryRules: Map<String, Map<ProfileType, AppInterventionLevel>> = emptyMap(),
    val savingsTriggerPercent: Int = 25,
    val savingsResetPercent: Int = 35,
    val emergencyTriggerPercent: Int = 10,
    val emergencyResetPercent: Int = 20,
    /** First-run privacy/capability introduction completed or skipped (primary flow 1). */
    val onboardingCompleted: Boolean = false,
    /** True once the user answered the telemetry consent question, so it is never re-asked (PRIV-001). */
    val telemetryPromptAnswered: Boolean = false,
    /** Display unit for battery temperatures. */
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    /** Compact or detailed live-monitor notification. */
    val monitorNotificationLayout: MonitorNotificationLayout = MonitorNotificationLayout.DETAILED,
    /** Include current/power estimates in the ongoing monitor notification. */
    val monitorNotificationShowPower: Boolean = true,
    /** MON-007: alert when a completed valid idle session exceeds [idleDrainAlertThresholdPercentPerHour]. */
    val idleDrainAlertEnabled: Boolean = false,
    val idleDrainAlertThresholdPercentPerHour: Float = 2.0f,
    /** BASE-001: the one-time baseline-alert offer was answered or dismissed. */
    val baselineAlertOfferDismissed: Boolean = false,
    /** BASE-001: minimum difference above the personal range before an alert is sent. */
    val baselineAlertMinDifferencePercentPerHour: Float = 0.5f,
    /** BAT-001: automatic profile triggers are paused; only the manual profile applies. */
    val batteryAutomationPaused: Boolean = false,
    val savingsTriggerEnabled: Boolean = false,
    val emergencyTriggerEnabled: Boolean = false,
    /** Haptic feedback for actions (UX-003); also respects the system haptics setting. */
    val hapticsEnabled: Boolean = true,
    /** Haptic strength (UX-003). [HapticsIntensity.OFF] exactly when [hapticsEnabled] is false. */
    val hapticsIntensity: HapticsIntensity = HapticsIntensity.NORMAL,
    /** Use Android wallpaper (dynamic) colors; false uses the Able brand palette (BRAND-001). */
    val useDynamicColor: Boolean = true,
    /** Guided tour after onboarding completed or skipped (UX-001). */
    val tourCompleted: Boolean = false
)

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE_OVERRIDE = stringPreferencesKey("language_override")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val TELEMETRY_OPT_IN = booleanPreferencesKey("telemetry_opt_in")
        val BASELINE_ALERT_ENABLED = booleanPreferencesKey("baseline_alert_enabled")
        val BASELINE_THRESHOLD = floatPreferencesKey("baseline_threshold")
        val SCREEN_OFF_DELAY_MINUTES = intPreferencesKey("screen_off_delay_minutes")
        val EMERGENCY_ACTIVE = booleanPreferencesKey("emergency_active")
        val SELECTED_BATTERY_PROFILE = stringPreferencesKey("selected_battery_profile")
        val NOTTE_SCHEDULE_ENABLED = booleanPreferencesKey("notte_schedule_enabled")
        val NOTTE_SCREEN_OFF_ENABLED = booleanPreferencesKey("notte_screen_off_enabled")
        val NOTTE_START_TIME = stringPreferencesKey("notte_start_time")
        val NOTTE_END_TIME = stringPreferencesKey("notte_end_time")
        val PROTECTED_APP_PACKAGES = stringSetPreferencesKey("protected_app_packages")
        val BATTERY_MONITOR_ENABLED = booleanPreferencesKey("battery_monitor_enabled")
        val BATTERY_RESET_ON_PLUG = booleanPreferencesKey("battery_reset_on_plug")
        val BATTERY_RESET_ON_BOOT = booleanPreferencesKey("battery_reset_on_boot")
        val BATTERY_RESET_THRESHOLD = intPreferencesKey("battery_reset_threshold")
        val BATTERY_THERMAL_ALERT_THRESHOLD = floatPreferencesKey("battery_thermal_alert_threshold")
        val APP_BATTERY_RULES = stringSetPreferencesKey("app_battery_rules")
        val SAVINGS_TRIGGER_PERCENT = intPreferencesKey("savings_trigger_percent")
        val SAVINGS_RESET_PERCENT = intPreferencesKey("savings_reset_percent")
        val EMERGENCY_TRIGGER_PERCENT = intPreferencesKey("emergency_trigger_percent")
        val EMERGENCY_RESET_PERCENT = intPreferencesKey("emergency_reset_percent")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val SAVINGS_TRIGGER_ENABLED = booleanPreferencesKey("savings_trigger_enabled")
        val EMERGENCY_TRIGGER_ENABLED = booleanPreferencesKey("emergency_trigger_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        /** Last non-off haptic strength; "off" is stored in [HAPTICS_ENABLED] for compatibility. */
        val HAPTICS_INTENSITY = stringPreferencesKey("haptics_intensity")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val TOUR_COMPLETED = booleanPreferencesKey("tour_completed")
        val TELEMETRY_PROMPT_ANSWERED = booleanPreferencesKey("telemetry_prompt_answered")
        val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
        val MONITOR_NOTIFICATION_LAYOUT = stringPreferencesKey("monitor_notification_layout")
        val MONITOR_NOTIFICATION_SHOW_POWER = booleanPreferencesKey("monitor_notification_show_power")
        val IDLE_DRAIN_ALERT_ENABLED = booleanPreferencesKey("idle_drain_alert_enabled")
        val IDLE_DRAIN_ALERT_THRESHOLD = floatPreferencesKey("idle_drain_alert_threshold")
        val BASELINE_ALERT_OFFER_DISMISSED = booleanPreferencesKey("baseline_alert_offer_dismissed")
        val BASELINE_ALERT_MIN_DIFFERENCE = floatPreferencesKey("baseline_alert_min_difference")
        val BATTERY_AUTOMATION_PAUSED = booleanPreferencesKey("battery_automation_paused")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { preferences ->
        UserPreferences(
            themeMode = preferences[PreferencesKeys.THEME_MODE]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM,
            languageOverride = preferences[PreferencesKeys.LANGUAGE_OVERRIDE],
            batteryHistoryRetentionDays = preferences[PreferencesKeys.RETENTION_DAYS] ?: 90,
            telemetryOptIn = preferences[PreferencesKeys.TELEMETRY_OPT_IN] ?: false,
            baselineAlertEnabled = preferences[PreferencesKeys.BASELINE_ALERT_ENABLED] ?: false,
            baselineAlertThresholdPercentPerHour = preferences[PreferencesKeys.BASELINE_THRESHOLD] ?: 3.0f,
            screenOffDelayMinutes = preferences[PreferencesKeys.SCREEN_OFF_DELAY_MINUTES] ?: 20,
            isEmergencyActive = preferences[PreferencesKeys.EMERGENCY_ACTIVE] ?: false,
            selectedBatteryProfile = preferences[PreferencesKeys.SELECTED_BATTERY_PROFILE]
                ?.let { runCatching { ProfileType.valueOf(it) }.getOrNull() } ?: ProfileType.NORMALE,
            notteScheduleEnabled = preferences[PreferencesKeys.NOTTE_SCHEDULE_ENABLED] ?: false,
            notteScreenOffEnabled = preferences[PreferencesKeys.NOTTE_SCREEN_OFF_ENABLED] ?: false,
            notteStartTime = preferences[PreferencesKeys.NOTTE_START_TIME] ?: "00:30",
            notteEndTime = preferences[PreferencesKeys.NOTTE_END_TIME] ?: "07:30",
            protectedAppPackages = preferences[PreferencesKeys.PROTECTED_APP_PACKAGES] ?: emptySet(),
            batteryMonitorEnabled = preferences[PreferencesKeys.BATTERY_MONITOR_ENABLED] ?: false,
            batteryResetOnPlug = preferences[PreferencesKeys.BATTERY_RESET_ON_PLUG] ?: false,
            batteryResetOnBoot = preferences[PreferencesKeys.BATTERY_RESET_ON_BOOT] ?: false,
            batteryResetThresholdPercent = preferences[PreferencesKeys.BATTERY_RESET_THRESHOLD],
            batteryThermalAlertThresholdCelsius = preferences[PreferencesKeys.BATTERY_THERMAL_ALERT_THRESHOLD] ?: 45f,
            appBatteryRules = preferences[PreferencesKeys.APP_BATTERY_RULES].orEmpty().mapNotNull { encoded ->
                val parts = encoded.split('|')
                if (parts.size != 3) return@mapNotNull null
                val profile = runCatching { ProfileType.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null
                val level = runCatching { AppInterventionLevel.valueOf(parts[2]) }.getOrNull() ?: return@mapNotNull null
                Triple(parts[0], profile, level)
            }.groupBy({ it.first }, { it.second to it.third })
                .mapValues { (_, rules) -> rules.toMap() },
            savingsTriggerPercent = preferences[PreferencesKeys.SAVINGS_TRIGGER_PERCENT] ?: 25,
            savingsResetPercent = preferences[PreferencesKeys.SAVINGS_RESET_PERCENT] ?: 35,
            emergencyTriggerPercent = preferences[PreferencesKeys.EMERGENCY_TRIGGER_PERCENT] ?: 10,
            emergencyResetPercent = preferences[PreferencesKeys.EMERGENCY_RESET_PERCENT] ?: 20,
            onboardingCompleted = preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false,
            savingsTriggerEnabled = preferences[PreferencesKeys.SAVINGS_TRIGGER_ENABLED] ?: false,
            emergencyTriggerEnabled = preferences[PreferencesKeys.EMERGENCY_TRIGGER_ENABLED] ?: false,
            hapticsEnabled = preferences[PreferencesKeys.HAPTICS_ENABLED] ?: true,
            hapticsIntensity = if (preferences[PreferencesKeys.HAPTICS_ENABLED] == false) {
                HapticsIntensity.OFF
            } else {
                preferences[PreferencesKeys.HAPTICS_INTENSITY]
                    ?.let { runCatching { HapticsIntensity.valueOf(it) }.getOrNull() }
                    ?.takeIf { it != HapticsIntensity.OFF }
                    ?: HapticsIntensity.NORMAL
            },
            useDynamicColor = preferences[PreferencesKeys.USE_DYNAMIC_COLOR] ?: true,
            tourCompleted = preferences[PreferencesKeys.TOUR_COMPLETED] ?: false,
            telemetryPromptAnswered = preferences[PreferencesKeys.TELEMETRY_PROMPT_ANSWERED] ?: false,
            temperatureUnit = preferences[PreferencesKeys.TEMPERATURE_UNIT]
                ?.let { runCatching { TemperatureUnit.valueOf(it) }.getOrNull() } ?: TemperatureUnit.CELSIUS,
            monitorNotificationLayout = preferences[PreferencesKeys.MONITOR_NOTIFICATION_LAYOUT]
                ?.let { runCatching { MonitorNotificationLayout.valueOf(it) }.getOrNull() } ?: MonitorNotificationLayout.DETAILED,
            monitorNotificationShowPower = preferences[PreferencesKeys.MONITOR_NOTIFICATION_SHOW_POWER] ?: true,
            idleDrainAlertEnabled = preferences[PreferencesKeys.IDLE_DRAIN_ALERT_ENABLED] ?: false,
            idleDrainAlertThresholdPercentPerHour = preferences[PreferencesKeys.IDLE_DRAIN_ALERT_THRESHOLD] ?: 2.0f,
            baselineAlertOfferDismissed = preferences[PreferencesKeys.BASELINE_ALERT_OFFER_DISMISSED] ?: false,
            baselineAlertMinDifferencePercentPerHour = preferences[PreferencesKeys.BASELINE_ALERT_MIN_DIFFERENCE] ?: 0.5f,
            batteryAutomationPaused = preferences[PreferencesKeys.BATTERY_AUTOMATION_PAUSED] ?: false
        )
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = themeMode.name
        }
    }

    suspend fun setLanguageOverride(localeCode: String?) {
        context.dataStore.edit { preferences ->
            if (localeCode != null) {
                preferences[PreferencesKeys.LANGUAGE_OVERRIDE] = localeCode
            } else {
                preferences.remove(PreferencesKeys.LANGUAGE_OVERRIDE)
            }
        }
    }

    suspend fun setRetentionDays(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RETENTION_DAYS] = days
        }
    }

    suspend fun setTelemetryOptIn(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TELEMETRY_OPT_IN] = enabled
            preferences[PreferencesKeys.TELEMETRY_PROMPT_ANSWERED] = true
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setBaselineAlert(enabled: Boolean, threshold: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BASELINE_ALERT_ENABLED] = enabled
            preferences[PreferencesKeys.BASELINE_THRESHOLD] = threshold
        }
    }

    suspend fun setEmergencyActive(active: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.EMERGENCY_ACTIVE] = active
        }
    }

    suspend fun setSelectedBatteryProfile(profile: ProfileType) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.SELECTED_BATTERY_PROFILE] = profile.name }
    }

    suspend fun setNotteTriggers(scheduleEnabled: Boolean, screenOffEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTTE_SCHEDULE_ENABLED] = scheduleEnabled
            preferences[PreferencesKeys.NOTTE_SCREEN_OFF_ENABLED] = screenOffEnabled
        }
    }

    suspend fun setNotteScheduleTimes(start: String, end: String): Boolean {
        val timePattern = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$")
        if (!timePattern.matches(start) || !timePattern.matches(end) || start == end) return false
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTTE_START_TIME] = start
            preferences[PreferencesKeys.NOTTE_END_TIME] = end
        }
        return true
    }

    suspend fun setScreenOffDelayMinutes(minutes: Int) {
        context.dataStore.edit { it[PreferencesKeys.SCREEN_OFF_DELAY_MINUTES] = minutes.coerceIn(5, 120) }
    }

    suspend fun setBatteryProfileThresholds(
        savingsTrigger: Int,
        savingsReset: Int,
        emergencyTrigger: Int,
        emergencyReset: Int
    ) {
        val emergencyOn = emergencyTrigger.coerceIn(1, 24)
        val savingsOn = savingsTrigger.coerceIn(emergencyOn + 1, 98)
        val emergencyOff = emergencyReset.coerceIn(emergencyOn + 1, savingsOn)
        val savingsOff = savingsReset.coerceIn(savingsOn + 1, 100)
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SAVINGS_TRIGGER_PERCENT] = savingsOn
            preferences[PreferencesKeys.SAVINGS_RESET_PERCENT] = savingsOff
            preferences[PreferencesKeys.EMERGENCY_TRIGGER_PERCENT] = emergencyOn
            preferences[PreferencesKeys.EMERGENCY_RESET_PERCENT] = emergencyOff
        }
    }

    suspend fun setAppProtected(packageName: String, protected: Boolean) {
        context.dataStore.edit { preferences ->
            val packages = preferences[PreferencesKeys.PROTECTED_APP_PACKAGES].orEmpty().toMutableSet()
            if (protected) packages.add(packageName) else packages.remove(packageName)
            preferences[PreferencesKeys.PROTECTED_APP_PACKAGES] = packages
        }
    }

    suspend fun setBatteryMonitorEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.BATTERY_MONITOR_ENABLED] = enabled }
    }

    suspend fun setBatteryResetRules(onPlug: Boolean, onBoot: Boolean, thresholdPercent: Int?) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BATTERY_RESET_ON_PLUG] = onPlug
            preferences[PreferencesKeys.BATTERY_RESET_ON_BOOT] = onBoot
            if (thresholdPercent == null) preferences.remove(PreferencesKeys.BATTERY_RESET_THRESHOLD)
            else preferences[PreferencesKeys.BATTERY_RESET_THRESHOLD] = thresholdPercent.coerceIn(1, 100)
        }
    }

    suspend fun setBatteryResetThresholdPercent(thresholdPercent: Int?) {
        context.dataStore.edit { preferences ->
            if (thresholdPercent == null) preferences.remove(PreferencesKeys.BATTERY_RESET_THRESHOLD)
            else preferences[PreferencesKeys.BATTERY_RESET_THRESHOLD] = thresholdPercent.coerceIn(1, 100)
        }
    }

    suspend fun setBatteryThermalAlertThreshold(celsius: Float) {
        context.dataStore.edit { it[PreferencesKeys.BATTERY_THERMAL_ALERT_THRESHOLD] = celsius.coerceIn(40f, 60f) }
    }

    suspend fun setAppBatteryRule(packageName: String, profile: ProfileType, level: AppInterventionLevel) {
        if (!packageName.matches(Regex("[A-Za-z0-9_.]+"))) return
        context.dataStore.edit { preferences ->
            val prefix = "$packageName|${profile.name}|"
            val rules = preferences[PreferencesKeys.APP_BATTERY_RULES].orEmpty().filterNot { it.startsWith(prefix) }.toMutableSet()
            if (level != AppInterventionLevel.OSSERVA) rules.add("$packageName|${profile.name}|${level.name}")
            preferences[PreferencesKeys.APP_BATTERY_RULES] = rules
        }
    }

    suspend fun setBatteryResetOnPlug(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.BATTERY_RESET_ON_PLUG] = enabled }
    }

    suspend fun setBatteryResetOnBoot(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.BATTERY_RESET_ON_BOOT] = enabled }
    }

    suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        context.dataStore.edit { it[PreferencesKeys.TEMPERATURE_UNIT] = unit.name }
    }

    suspend fun setMonitorNotificationLayout(layout: MonitorNotificationLayout) {
        context.dataStore.edit { it[PreferencesKeys.MONITOR_NOTIFICATION_LAYOUT] = layout.name }
    }

    suspend fun setMonitorNotificationShowPower(show: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.MONITOR_NOTIFICATION_SHOW_POWER] = show }
    }

    suspend fun setIdleDrainAlert(enabled: Boolean, thresholdPercentPerHour: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IDLE_DRAIN_ALERT_ENABLED] = enabled
            preferences[PreferencesKeys.IDLE_DRAIN_ALERT_THRESHOLD] = thresholdPercentPerHour.coerceIn(0.5f, 10f)
        }
    }

    /** BASE-001: answers the one-time offer. Enabling also marks the offer as answered. */
    suspend fun setBaselineAlertEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BASELINE_ALERT_ENABLED] = enabled
            preferences[PreferencesKeys.BASELINE_ALERT_OFFER_DISMISSED] = true
        }
    }

    suspend fun dismissBaselineAlertOffer() {
        context.dataStore.edit { it[PreferencesKeys.BASELINE_ALERT_OFFER_DISMISSED] = true }
    }

    suspend fun setBaselineAlertMinDifference(percentPerHour: Float) {
        context.dataStore.edit { it[PreferencesKeys.BASELINE_ALERT_MIN_DIFFERENCE] = percentPerHour.coerceIn(0.1f, 5f) }
    }

    suspend fun setBatteryAutomationPaused(paused: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.BATTERY_AUTOMATION_PAUSED] = paused }
    }

    suspend fun setSavingsTriggerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SAVINGS_TRIGGER_ENABLED] = enabled }
    }

    suspend fun setEmergencyTriggerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.EMERGENCY_TRIGGER_ENABLED] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.HAPTICS_ENABLED] = enabled }
    }

    /**
     * Sets the haptic strength. OFF only clears [PreferencesKeys.HAPTICS_ENABLED], so turning
     * haptics back on (here or with [setHapticsEnabled]) restores the last chosen strength.
     */
    suspend fun setHapticsIntensity(intensity: HapticsIntensity) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAPTICS_ENABLED] = intensity != HapticsIntensity.OFF
            if (intensity != HapticsIntensity.OFF) preferences[PreferencesKeys.HAPTICS_INTENSITY] = intensity.name
        }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.USE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setTourCompleted(completed: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.TOUR_COMPLETED] = completed }
    }
}
