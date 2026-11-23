package com.dragolabs.able.feature.apps.data

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** UAD list a package belongs to (APP-006). */
enum class UadList { OEM, MISC, AOSP, CARRIER, GOOGLE }

/** UAD removal recommendation, from safest to most dangerous (APP-006). */
enum class RemovalLevel { RECOMMENDED, ADVANCED, EXPERT, UNSAFE }

/** Read-only description and removal-safety label for one package (APP-006). */
data class PackageKnowledge(
    val packageName: String,
    val list: UadList?,
    val removal: RemovalLevel?,
    val description: String,
    val dependencies: List<String>,
    val neededBy: List<String>,
    val labels: List<String>,
)

@Serializable
internal data class UadEntryDto(
    val list: String? = null,
    val description: String? = null,
    val dependencies: List<String> = emptyList(),
    val neededBy: List<String> = emptyList(),
    val labels: List<String> = emptyList(),
    val removal: String? = null,
)

/** Parses the bundled Universal Android Debloater snapshot (object keyed by package name). */
object UadParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /**
     * Parses [text]. Unknown or extra keys are ignored; a malformed entry is skipped instead of
     * failing the whole list. Throws only when the document is not a JSON object.
     */
    fun parse(text: String): Map<String, PackageKnowledge> {
        val root: JsonObject = json.parseToJsonElement(text).jsonObject
        val result = HashMap<String, PackageKnowledge>(root.size * 4 / 3 + 1)
        for ((packageName, element) in root) {
            val name = packageName.trim()
            if (name.isEmpty()) continue
            val dto = runCatching { json.decodeFromJsonElement(UadEntryDto.serializer(), element) }.getOrNull() ?: continue
            result[name] = PackageKnowledge(
                packageName = name,
                list = parseList(dto.list),
                removal = parseRemoval(dto.removal),
                description = dto.description.orEmpty().trim(),
                dependencies = dto.dependencies.map(String::trim).filter(String::isNotEmpty),
                neededBy = dto.neededBy.map(String::trim).filter(String::isNotEmpty),
                labels = dto.labels.map(String::trim).filter(String::isNotEmpty),
            )
        }
        return result
    }

    /** Parses a `{ "package": "translated description" }` object; bad entries are skipped. */
    fun parseTranslations(text: String): Map<String, String> {
        val root: JsonObject = json.parseToJsonElement(text).jsonObject
        return buildMap {
            for ((name, element) in root) {
                val value = (element as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
                if (name.isNotBlank() && !value.isNullOrEmpty()) put(name.trim(), value)
            }
        }
    }

    /** Replaces descriptions with [translations] where one exists. */
    fun applyTranslations(
        base: Map<String, PackageKnowledge>,
        translations: Map<String, String>,
    ): Map<String, PackageKnowledge> = if (translations.isEmpty()) base else base.mapValues { (name, entry) ->
        translations[name]?.let { entry.copy(description = it) } ?: entry
    }

    fun parseList(value: String?): UadList? = when (value?.trim()?.lowercase()) {
        "oem" -> UadList.OEM
        "misc" -> UadList.MISC
        "aosp" -> UadList.AOSP
        "carrier" -> UadList.CARRIER
        "google" -> UadList.GOOGLE
        else -> null
    }

    fun parseRemoval(value: String?): RemovalLevel? = when (value?.trim()?.lowercase()) {
        "recommended" -> RemovalLevel.RECOMMENDED
        "advanced" -> RemovalLevel.ADVANCED
        "expert" -> RemovalLevel.EXPERT
        "unsafe" -> RemovalLevel.UNSAFE
        else -> null
    }
}

/**
 * Attribution for the bundled package knowledge base. The About screen can show
 * [NOTICE] and offer [LICENSE_ASSET] (read with `context.assets.open(...)`).
 */
object PackageKnowledgeAttribution {
    const val NOTICE = "Package info: Universal Android Debloater (GPL-3.0)"
    const val SOURCE_NAME = "Universal Android Debloater Next Generation"
    const val LICENSE = "GPL-3.0"
    const val SOURCE_URL = "https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation"
    const val SNAPSHOT_COMMIT = "31e3dea4546c9b05eb975474d30ea7c88fd0b930"
    const val NOTICE_ASSET = "uad/NOTICE.md"
    const val LICENSE_ASSET = "uad/LICENSE-GPL-3.0.txt"
}

/**
 * Lazily parses `assets/uad/uad_lists.json` on the IO dispatcher the first time it is needed and
 * keeps it in memory for the process lifetime (it is read-only and refreshed per release).
 *
 * When Able runs in Italian, descriptions come from `assets/uad/uad_descriptions_it.json`
 * (package name → Italian text, a translation of the same snapshot made for Able, GPL-3.0 like the
 * source); packages without a translation keep the English text.
 */
class PackageKnowledgeRepository(private val context: Context) {

    /** Returns all entries, or an empty map if the bundled snapshot cannot be read. */
    suspend fun all(): Map<String, PackageKnowledge> {
        val language = currentLanguage()
        cache?.takeIf { cacheLanguage == language }?.let { return it }
        return mutex.withLock {
            cache?.takeIf { cacheLanguage == language } ?: withContext(Dispatchers.IO) {
                val base = runCatching {
                    context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { UadParser.parse(it.readText()) }
                }.getOrDefault(emptyMap())
                if (language != "it") return@withContext base
                val italian = runCatching {
                    context.assets.open(ASSET_PATH_IT).bufferedReader(Charsets.UTF_8).use { UadParser.parseTranslations(it.readText()) }
                }.getOrDefault(emptyMap())
                UadParser.applyTranslations(base, italian)
            }.also {
                cache = it
                cacheLanguage = language
            }
        }
    }

    /**
     * The language Able's UI is shown in: the per-app language chosen in Settings (Android 13+
     * `LocaleManager`, which the application context's configuration does not always reflect),
     * else the device language.
     */
    private fun currentLanguage(): String {
        val appLocales = runCatching { context.getSystemService(LocaleManager::class.java)?.applicationLocales }.getOrNull()
        val locales = appLocales?.takeIf { !it.isEmpty } ?: LocaleList.getDefault()
        return locales.takeIf { !it.isEmpty }?.get(0)?.language.orEmpty()
    }

    private companion object {
        const val ASSET_PATH = "uad/uad_lists.json"
        const val ASSET_PATH_IT = "uad/uad_descriptions_it.json"
        val mutex = Mutex()

        @Volatile
        var cache: Map<String, PackageKnowledge>? = null

        @Volatile
        var cacheLanguage: String? = null
    }
}
