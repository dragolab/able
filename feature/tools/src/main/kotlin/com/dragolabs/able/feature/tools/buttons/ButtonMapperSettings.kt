package com.dragolabs.able.feature.tools.buttons

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Volume key gestures Able can recognise (BTN-001). Keys are persisted: never rename. */
enum class ButtonGesture(val key: String) {
    VOLUME_BOTH("volume_both"),
    DOUBLE_UP("double_volume_up"),
    DOUBLE_DOWN("double_volume_down"),
    LONG_UP("long_volume_up"),
    LONG_DOWN("long_volume_down"),
    ;

    companion object {
        fun fromKey(key: String?): ButtonGesture? = entries.firstOrNull { it.key == key }
    }
}

/** What a gesture runs (BTN-002). */
@Immutable
sealed interface ButtonTarget {
    /** An Able Action Catalog action, by its stable id (`com.dragolabs.able.actions.ActionIds`). */
    data class Catalog(val actionId: String) : ButtonTarget

    /** The default camera (`MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA[_SECURE]`). */
    data object Camera : ButtonTarget

    /** An app's launcher entry. */
    data class App(val packageName: String) : ButtonTarget

    /** An exported activity, as listed by the Activity launcher tool. */
    data class Activity(val packageName: String, val className: String) : ButtonTarget
}

@Immutable
data class ButtonMapperConfig(
    val enabled: Boolean = false,
    val mappings: Map<ButtonGesture, ButtonTarget> = emptyMap(),
    val doublePressMs: Int = ButtonMapperSettings.DEFAULT_DOUBLE_PRESS_MS,
    val longPressMs: Int = ButtonMapperSettings.DEFAULT_LONG_PRESS_MS,
    val undoVolumeStep: Boolean = true,
    val haptic: Boolean = true,
    val testMode: Boolean = false,
    val lastDetected: DetectedGesture? = null,
) {
    /** The service only filters key events when this is true. */
    val active: Boolean get() = enabled && mappings.isNotEmpty()
}

/** Last gesture the service recognised, written for the tool's test area. */
@Immutable
data class DetectedGesture(val gesture: ButtonGesture, val wallTimeMs: Long, val testOnly: Boolean)

/**
 * Button shortcut settings (BTN-001..BTN-003), stored locally in the SharedPreferences file
 * [FILE] ("able_button_mapper") and read by both this tool and the `:app` Accessibility service
 * (`com.dragolabs.able.buttons.*`). Nothing is synced or sent. Keys (keep stable):
 *
 * - [KEY_ENABLED] `enabled` Boolean, default false: master switch.
 * - [KEY_MAPPINGS] `mappings_json` String: JSON written by [ButtonMappingCodec], format
 *   `{"version":1,"mappings":[{"gesture":"double_volume_up","type":"catalog","action":"torch"},
 *   {"gesture":"volume_both","type":"camera"},{"gesture":"long_volume_down","type":"app",
 *   "package":"com.example"},{"gesture":"...","type":"activity","package":"...","class":"..."}]}`.
 * - [KEY_DOUBLE_PRESS_MS] `double_press_ms` Int, default 350, range 200..600.
 * - [KEY_LONG_PRESS_MS] `long_press_ms` Int, default 500, range 300..1000.
 * - [KEY_UNDO_STEP] `undo_volume_step` Boolean, default true: revert the volume step of the
 *   passed-through first press when a double press or combo is recognised.
 * - [KEY_HAPTIC] `haptic` Boolean, default true: vibrate when a gesture runs.
 * - [KEY_TEST_MODE] `test_mode` Boolean, default false: set by the tool while its test area is
 *   active; the service then only reports gestures and does not run actions.
 * - [KEY_LAST_DETECTED] `last_detected` String "gestureKey|wallTimeMs|test", written by the service.
 */
class ButtonMapperSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(): ButtonMapperConfig = ButtonMapperConfig(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        mappings = ButtonMappingCodec.decode(prefs.getString(KEY_MAPPINGS, null)),
        doublePressMs = prefs.getInt(KEY_DOUBLE_PRESS_MS, DEFAULT_DOUBLE_PRESS_MS).coerceIn(MIN_DOUBLE_PRESS_MS, MAX_DOUBLE_PRESS_MS),
        longPressMs = prefs.getInt(KEY_LONG_PRESS_MS, DEFAULT_LONG_PRESS_MS).coerceIn(MIN_LONG_PRESS_MS, MAX_LONG_PRESS_MS),
        undoVolumeStep = prefs.getBoolean(KEY_UNDO_STEP, true),
        haptic = prefs.getBoolean(KEY_HAPTIC, true),
        testMode = prefs.getBoolean(KEY_TEST_MODE, false),
        lastDetected = parseDetected(prefs.getString(KEY_LAST_DETECTED, null)),
    )

    fun setEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()

    fun setMapping(gesture: ButtonGesture, target: ButtonTarget?) {
        val current = read().mappings.toMutableMap()
        if (target == null) current.remove(gesture) else current[gesture] = target
        prefs.edit().putString(KEY_MAPPINGS, ButtonMappingCodec.encode(current)).apply()
    }

    fun setDoublePressMs(value: Int) =
        prefs.edit().putInt(KEY_DOUBLE_PRESS_MS, value.coerceIn(MIN_DOUBLE_PRESS_MS, MAX_DOUBLE_PRESS_MS)).apply()

    fun setLongPressMs(value: Int) =
        prefs.edit().putInt(KEY_LONG_PRESS_MS, value.coerceIn(MIN_LONG_PRESS_MS, MAX_LONG_PRESS_MS)).apply()

    fun setUndoVolumeStep(enabled: Boolean) = prefs.edit().putBoolean(KEY_UNDO_STEP, enabled).apply()

    fun setHaptic(enabled: Boolean) = prefs.edit().putBoolean(KEY_HAPTIC, enabled).apply()

    fun setTestMode(enabled: Boolean) {
        if (prefs.getBoolean(KEY_TEST_MODE, false) != enabled) prefs.edit().putBoolean(KEY_TEST_MODE, enabled).apply()
    }

    /** Written by the Accessibility service. */
    fun reportDetected(gesture: ButtonGesture, wallTimeMs: Long, testOnly: Boolean) =
        prefs.edit().putString(KEY_LAST_DETECTED, "${gesture.key}|$wallTimeMs|${if (testOnly) 1 else 0}").apply()

    fun changes(): Flow<ButtonMapperConfig> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    /** Registers a raw listener (used by the service); keep a strong reference to [listener]. */
    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private fun parseDetected(raw: String?): DetectedGesture? {
        val parts = raw?.split('|') ?: return null
        if (parts.size != 3) return null
        val gesture = ButtonGesture.fromKey(parts[0]) ?: return null
        val time = parts[1].toLongOrNull() ?: return null
        return DetectedGesture(gesture, time, parts[2] == "1")
    }

    companion object {
        const val FILE = "able_button_mapper"
        const val KEY_ENABLED = "enabled"
        const val KEY_MAPPINGS = "mappings_json"
        const val KEY_DOUBLE_PRESS_MS = "double_press_ms"
        const val KEY_LONG_PRESS_MS = "long_press_ms"
        const val KEY_UNDO_STEP = "undo_volume_step"
        const val KEY_HAPTIC = "haptic"
        const val KEY_TEST_MODE = "test_mode"
        const val KEY_LAST_DETECTED = "last_detected"

        const val DEFAULT_DOUBLE_PRESS_MS = 350
        const val MIN_DOUBLE_PRESS_MS = 200
        const val MAX_DOUBLE_PRESS_MS = 600
        const val DEFAULT_LONG_PRESS_MS = 500
        const val MIN_LONG_PRESS_MS = 300
        const val MAX_LONG_PRESS_MS = 1000
    }
}

/**
 * JSON codec for gesture mappings, using the kotlinx.serialization JSON element API (no
 * generated serializers, so `:feature:tools` needs no compiler plugin). Unknown gestures or
 * target types from newer versions are skipped rather than failing the whole list.
 */
object ButtonMappingCodec {
    private const val VERSION = 1
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(mappings: Map<ButtonGesture, ButtonTarget>): String = buildJsonObject {
        put("version", VERSION)
        put(
            "mappings",
            buildJsonArray {
                ButtonGesture.entries.forEach { gesture ->
                    val target = mappings[gesture] ?: return@forEach
                    add(
                        buildJsonObject {
                            put("gesture", gesture.key)
                            when (target) {
                                is ButtonTarget.Catalog -> {
                                    put("type", "catalog")
                                    put("action", target.actionId)
                                }
                                ButtonTarget.Camera -> put("type", "camera")
                                is ButtonTarget.App -> {
                                    put("type", "app")
                                    put("package", target.packageName)
                                }
                                is ButtonTarget.Activity -> {
                                    put("type", "activity")
                                    put("package", target.packageName)
                                    put("class", target.className)
                                }
                            }
                        },
                    )
                }
            },
        )
    }.toString()

    fun decode(raw: String?): Map<ButtonGesture, ButtonTarget> {
        if (raw.isNullOrBlank()) return emptyMap()
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyMap()
        val list = runCatching { root["mappings"]?.jsonArray }.getOrNull() ?: JsonArray(emptyList())
        val result = LinkedHashMap<ButtonGesture, ButtonTarget>()
        for (element in list) {
            val obj = element as? JsonObject ?: continue
            val gesture = ButtonGesture.fromKey(obj.string("gesture")) ?: continue
            val target = when (obj.string("type")) {
                "catalog" -> obj.string("action")?.takeIf { it.isNotBlank() }?.let(ButtonTarget::Catalog)
                "camera" -> ButtonTarget.Camera
                "app" -> obj.string("package")?.takeIf { it.isNotBlank() }?.let(ButtonTarget::App)
                "activity" -> {
                    val pkg = obj.string("package")
                    val cls = obj.string("class")
                    if (!pkg.isNullOrBlank() && !cls.isNullOrBlank()) ButtonTarget.Activity(pkg, cls) else null
                }
                else -> null
            } ?: continue
            result[gesture] = target
        }
        return result
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.jsonPrimitive?.contentOrNull
}
