package com.dragolabs.able.feature.tools.fountains

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder
import java.util.Locale

/** Builds the Overpass QL request for drinking-water points in a box (pure, unit-tested). */
object OverpassQuery {
    /** Upper bound on returned elements, so a dense city centre cannot produce a huge response. */
    const val MAX_ELEMENTS = 1000

    /** Tag filters for drinking-water points; each is queried on nodes and ways. */
    val FILTERS: List<String> = listOf(
        """["amenity"="drinking_water"]""",
        """["man_made"="water_tap"]["drinking_water"="yes"]""",
        """["natural"="spring"]["drinking_water"="yes"]""",
        """["amenity"="fountain"]["drinking_water"="yes"]""",
        """["fountain"="drinking"]""",
    )

    fun build(box: BoundingBox): String {
        val bbox = listOf(box.south, box.west, box.north, box.east)
            .joinToString(",") { String.format(Locale.ROOT, "%.6f", it) }
        val statements = FILTERS.flatMap { filter -> listOf("node$filter($bbox);", "way$filter($bbox);") }
        return buildString {
            append("[out:json][timeout:25];\n(\n")
            statements.forEach { append("  ").append(it).append('\n') }
            append(");\nout center tags ").append(MAX_ELEMENTS).append(';')
        }
    }

    /** `application/x-www-form-urlencoded` body for the POST. */
    fun formBody(query: String): String = "data=" + URLEncoder.encode(query, Charsets.UTF_8.name())
}

/** Parses an Overpass JSON response into [Fountain]s (pure, unit-tested). */
object OverpassParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** @throws IllegalArgumentException when the text is not an Overpass JSON document. */
    fun parse(text: String): List<Fountain> {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: throw IllegalArgumentException("Not an Overpass JSON response")
        val elements = root["elements"] as? JsonArray ?: throw IllegalArgumentException("Missing elements")
        return elements.mapNotNull { (it as? JsonObject)?.let(::parseElement) }
            .distinctBy { it.key }
    }

    internal fun parseElement(element: JsonObject): Fountain? {
        val type = when (element.string("type")) {
            "node" -> OsmType.NODE
            "way" -> OsmType.WAY
            else -> return null
        }
        val id = (element["id"] as? JsonPrimitive)?.longOrNull ?: return null
        val center = element["center"] as? JsonObject
        val lat = element.double("lat") ?: center?.double("lat") ?: return null
        val lon = element.double("lon") ?: center?.double("lon") ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val tags = (element["tags"] as? JsonObject)
            ?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it.trim() } }
            ?.toMap()
            ?: emptyMap()
        val kind = kindOf(tags) ?: return null
        // An explicit "not drinkable" overrides the feature type: never list it as a fountain.
        if (tags["drinking_water"]?.lowercase(Locale.ROOT) == "no") return null
        return Fountain(
            osmType = type,
            osmId = id,
            position = GeoPos(lat, lon),
            kind = kind,
            name = tags.nonBlank("name"),
            bottle = triState(tags["bottle"]),
            wheelchair = triState(tags["wheelchair"]),
            fee = triState(tags["fee"]),
            seasonal = tags.nonBlank("seasonal"),
            openingHours = tags.nonBlank("opening_hours"),
            lastChecked = tags.nonBlank("check_date:drinking_water")
                ?: tags.nonBlank("check_date")
                ?: tags.nonBlank("survey:date")
                ?: tags.nonBlank("survey_date"),
            operator = tags.nonBlank("operator"),
            description = tags.nonBlank("description:${Locale.getDefault().language}") ?: tags.nonBlank("description"),
        )
    }

    internal fun kindOf(tags: Map<String, String>): FountainKind? {
        val drinkable = tags["drinking_water"]?.lowercase(Locale.ROOT) == "yes"
        return when {
            tags["amenity"] == "drinking_water" -> FountainKind.DRINKING_WATER
            tags["fountain"] == "drinking" -> FountainKind.DRINKING_WATER
            tags["man_made"] == "water_tap" && drinkable -> FountainKind.WATER_TAP
            tags["natural"] == "spring" && drinkable -> FountainKind.SPRING
            tags["amenity"] == "fountain" && drinkable -> FountainKind.FOUNTAIN
            else -> null
        }
    }

    internal fun triState(value: String?): TriState = when (value?.lowercase(Locale.ROOT)?.trim()) {
        null, "" -> TriState.UNKNOWN
        "yes", "designated", "permissive" -> TriState.YES
        "limited", "partial" -> TriState.LIMITED
        "no" -> TriState.NO
        else -> TriState.UNKNOWN
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
    private fun Map<String, String>.nonBlank(key: String): String? = this[key]?.takeIf { it.isNotBlank() }
}
