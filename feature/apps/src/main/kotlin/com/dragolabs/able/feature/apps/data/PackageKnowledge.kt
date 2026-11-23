package com.dragolabs.able.feature.apps.data

import android.content.Context
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
 */
class PackageKnowledgeRepository(private val context: Context) {

    /** Returns all entries, or an empty map if the bundled snapshot cannot be read. */
    suspend fun all(): Map<String, PackageKnowledge> {
        cache?.let { return it }
        return mutex.withLock {
            cache ?: withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { UadParser.parse(it.readText()) }
                }.getOrDefault(emptyMap())
            }.also { cache = it }
        }
    }

    private companion object {
        const val ASSET_PATH = "uad/uad_lists.json"
        val mutex = Mutex()

        @Volatile
        var cache: Map<String, PackageKnowledge>? = null
    }
}
