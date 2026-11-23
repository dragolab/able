package com.dragolabs.able.feature.settings.backup

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Why an archive was rejected before any change was made (CFG-004). */
sealed interface BackupError {
    data object NotAnArchive : BackupError
    data object TooLarge : BackupError
    data object TooManyEntries : BackupError
    data class UnsafePath(val path: String) : BackupError
    data class DuplicateEntry(val path: String) : BackupError
    data object MissingManifest : BackupError
    data object MalformedManifest : BackupError
    data class UnsupportedSchema(val version: Int) : BackupError
    data class MissingEntry(val path: String) : BackupError
    data class ChecksumMismatch(val path: String) : BackupError
    data class MalformedSection(val section: BackupSection) : BackupError
    data object NoSupportedSections : BackupError
    data object ReadFailed : BackupError
}

/** A validated archive: manifest, decoded sections and ignored content. */
data class ParsedBackup(
    val manifest: BackupManifest,
    val payload: BackupPayload,
    /** Section keys or entry paths this version does not understand; never applied. */
    val unsupported: List<String>,
)

sealed interface BackupReadResult {
    data class Valid(val backup: ParsedBackup) : BackupReadResult
    data class Invalid(val error: BackupError) : BackupReadResult
}

/**
 * Pure-JVM reader/writer for `.ablebackup` archives. It never extracts entries to disk, never
 * executes content, and only decodes the fixed entry paths declared by [BackupSection].
 */
object BackupArchive {
    const val FORMAT_ID = "ablebackup"
    /** Written by this version; schema 1 archives are still read (legacy battery section). */
    const val SCHEMA_VERSION = 2
    const val MANIFEST_PATH = "manifest.json"
    const val FILE_EXTENSION = "ablebackup"
    const val MIME_TYPE = "application/octet-stream"

    const val MAX_ARCHIVE_BYTES: Long = 8L * 1024 * 1024
    /** Uncompressed cap per entry; the optional battery history is the largest section. */
    const val MAX_ENTRY_BYTES: Long = 16L * 1024 * 1024
    /** Uncompressed cap for all entries together, so many large entries cannot exhaust memory. */
    const val MAX_TOTAL_BYTES: Long = 32L * 1024 * 1024
    const val MAX_ENTRIES = 32

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = false
    }

    // --- Writing ---------------------------------------------------------------------------

    fun write(
        output: OutputStream,
        payload: BackupPayload,
        appVersion: String,
        createdAtIso: String,
    ): BackupManifest {
        val sectionBytes = linkedMapOf<BackupSection, ByteArray>()
        // The legacy schema-1 battery section is never written.
        payload.batteryProfiles?.let { sectionBytes[BackupSection.BATTERY_PROFILES] = encode(BatteryProfilesSectionDto.serializer(), it) }
        payload.homeAppearance?.let { sectionBytes[BackupSection.HOME_APPEARANCE] = encode(HomeAppearanceSectionDto.serializer(), it) }
        payload.gps?.let { sectionBytes[BackupSection.GPS_POINTS] = encode(GpsSectionDto.serializer(), it) }
        payload.dns?.let { sectionBytes[BackupSection.DNS_PROFILES] = encode(DnsSectionDto.serializer(), it) }
        payload.shortcuts?.let { sectionBytes[BackupSection.SHORTCUTS] = encode(ShortcutsSectionDto.serializer(), it) }
        payload.batteryHistory?.let { sectionBytes[BackupSection.BATTERY_HISTORY] = encode(BatteryHistorySectionDto.serializer(), it) }

        val manifest = BackupManifest(
            schemaVersion = SCHEMA_VERSION,
            appVersion = appVersion,
            createdAt = createdAtIso,
            sections = sectionBytes.keys.map { it.key },
            entries = sectionBytes.map { (section, bytes) ->
                ManifestEntry(section.entryPath, section.key, bytes.size.toLong(), sha256Hex(bytes))
            },
            // Battery history is excluded unless the user selected its opt-in section.
            excluded = BackupExclusion.entries
                .filterNot { it == BackupExclusion.BATTERY_HISTORY && BackupSection.BATTERY_HISTORY in sectionBytes }
                .map { it.key },
        )

        ZipOutputStream(output).use { zip ->
            zip.putEntry(MANIFEST_PATH, encode(BackupManifest.serializer(), manifest))
            sectionBytes.forEach { (section, bytes) -> zip.putEntry(section.entryPath, bytes) }
        }
        return manifest
    }

    /** Uncompressed JSON size of a history section, shown before export (the file is smaller). */
    fun encodedSize(history: BatteryHistorySectionDto): Long =
        encode(BatteryHistorySectionDto.serializer(), history).size.toLong()

    private fun ZipOutputStream.putEntry(path: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(path))
        write(bytes)
        closeEntry()
    }

    private fun <T> encode(serializer: KSerializer<T>, value: T): ByteArray =
        json.encodeToString(serializer, value).toByteArray(Charsets.UTF_8)

    // --- Reading ---------------------------------------------------------------------------

    fun read(input: InputStream): BackupReadResult {
        val archive = try {
            readCapped(input, MAX_ARCHIVE_BYTES) ?: return BackupReadResult.Invalid(BackupError.TooLarge)
        } catch (_: IOException) {
            return BackupReadResult.Invalid(BackupError.ReadFailed)
        }
        val entries = when (val result = readEntries(archive)) {
            is EntriesResult.Ok -> result.entries
            is EntriesResult.Error -> return BackupReadResult.Invalid(result.error)
        }
        val manifestBytes = entries[MANIFEST_PATH] ?: return BackupReadResult.Invalid(BackupError.MissingManifest)
        val manifest = try {
            json.decodeFromString(BackupManifest.serializer(), manifestBytes.toString(Charsets.UTF_8))
        } catch (_: SerializationException) {
            return BackupReadResult.Invalid(BackupError.MalformedManifest)
        } catch (_: IllegalArgumentException) {
            return BackupReadResult.Invalid(BackupError.MalformedManifest)
        }
        return validate(manifest, entries)
    }

    /**
     * Checks a decoded manifest against the archive entries: schema version, declared sections,
     * sizes and checksums, then decodes each supported section.
     */
    fun validate(manifest: BackupManifest, entries: Map<String, ByteArray>): BackupReadResult {
        if (manifest.format != FORMAT_ID) return BackupReadResult.Invalid(BackupError.MalformedManifest)
        if (manifest.schemaVersion < 1) return BackupReadResult.Invalid(BackupError.MalformedManifest)
        if (manifest.schemaVersion > SCHEMA_VERSION) {
            return BackupReadResult.Invalid(BackupError.UnsupportedSchema(manifest.schemaVersion))
        }
        if (manifest.entries.map { it.path }.toSet().size != manifest.entries.size) {
            return BackupReadResult.Invalid(BackupError.MalformedManifest)
        }

        val unsupported = mutableListOf<String>()
        // Section key -> (section it is imported as, fixed entry path).
        val sections = linkedMapOf<String, Pair<BackupSection, String>>()
        val declared = manifest.sections.distinct()
        for (key in declared) {
            val section = BackupSection.fromKey(key)
            when {
                section != null -> sections[key] = section to section.entryPath
                key == BackupSection.LEGACY_BATTERY_KEY ->
                    // A schema-2 profiles section supersedes the legacy one; never apply both.
                    if (BackupSection.BATTERY_PROFILES.key in declared) {
                        unsupported += key
                    } else {
                        sections[key] = BackupSection.BATTERY_PROFILES to BackupSection.LEGACY_BATTERY_PATH
                    }
                else -> unsupported += key
            }
        }

        val verified = mutableMapOf<String, ByteArray>()
        for ((key, target) in sections) {
            val fixedPath = target.second
            val entry = manifest.entries.firstOrNull { it.section == key }
                ?: return BackupReadResult.Invalid(BackupError.MissingEntry(fixedPath))
            // Never trust a path from the archive: the declared path must match the fixed one.
            if (entry.path != fixedPath) return BackupReadResult.Invalid(BackupError.UnsafePath(entry.path))
            val bytes = entries[entry.path] ?: return BackupReadResult.Invalid(BackupError.MissingEntry(entry.path))
            if (bytes.size.toLong() != entry.sizeBytes || !sha256Hex(bytes).equals(entry.sha256, ignoreCase = true)) {
                return BackupReadResult.Invalid(BackupError.ChecksumMismatch(entry.path))
            }
            verified[key] = bytes
        }

        val known = sections.values.map { it.second }.toSet() + MANIFEST_PATH
        entries.keys.filterNot { it in known }.forEach { unsupported += it }

        if (verified.isEmpty()) return BackupReadResult.Invalid(BackupError.NoSupportedSections)

        val payload = try {
            BackupPayload(
                batteryProfiles = verified.decode(BackupSection.BATTERY_PROFILES, BatteryProfilesSectionDto.serializer()),
                homeAppearance = verified.decode(BackupSection.HOME_APPEARANCE, HomeAppearanceSectionDto.serializer()),
                gps = verified.decode(BackupSection.GPS_POINTS, GpsSectionDto.serializer()),
                dns = verified.decode(BackupSection.DNS_PROFILES, DnsSectionDto.serializer()),
                shortcuts = verified.decode(BackupSection.SHORTCUTS, ShortcutsSectionDto.serializer()),
                batteryHistory = verified.decode(BackupSection.BATTERY_HISTORY, BatteryHistorySectionDto.serializer()),
                legacyBattery = verified.decode(
                    BackupSection.LEGACY_BATTERY_KEY,
                    BackupSection.BATTERY_PROFILES,
                    BatterySectionDto.serializer(),
                ),
            )
        } catch (e: SectionDecodeException) {
            return BackupReadResult.Invalid(BackupError.MalformedSection(e.section))
        }
        return BackupReadResult.Valid(ParsedBackup(manifest, payload, unsupported))
    }

    private class SectionDecodeException(val section: BackupSection) : Exception()

    private fun <T> Map<String, ByteArray>.decode(section: BackupSection, serializer: KSerializer<T>): T? =
        decode(section.key, section, serializer)

    private fun <T> Map<String, ByteArray>.decode(key: String, section: BackupSection, serializer: KSerializer<T>): T? {
        val bytes = this[key] ?: return null
        return try {
            json.decodeFromString(serializer, bytes.toString(Charsets.UTF_8))
        } catch (_: SerializationException) {
            throw SectionDecodeException(section)
        } catch (_: IllegalArgumentException) {
            throw SectionDecodeException(section)
        }
    }

    private sealed interface EntriesResult {
        data class Ok(val entries: Map<String, ByteArray>) : EntriesResult
        data class Error(val error: BackupError) : EntriesResult
    }

    private fun readEntries(archive: ByteArray): EntriesResult {
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
                var entry = zip.nextEntry ?: return EntriesResult.Error(BackupError.NotAnArchive)
                while (true) {
                    val name = entry.name
                    if (!isSafePath(name)) return EntriesResult.Error(BackupError.UnsafePath(name))
                    if (entries.size >= MAX_ENTRIES) return EntriesResult.Error(BackupError.TooManyEntries)
                    if (!entry.isDirectory) {
                        if (name in entries) return EntriesResult.Error(BackupError.DuplicateEntry(name))
                        // Uncompressed size is capped while reading, so a zip bomb cannot exhaust memory.
                        val bytes = readCapped(zip, MAX_ENTRY_BYTES) ?: return EntriesResult.Error(BackupError.TooLarge)
                        total += bytes.size
                        if (total > MAX_TOTAL_BYTES) return EntriesResult.Error(BackupError.TooLarge)
                        entries[name] = bytes
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry ?: break
                }
            }
        } catch (_: ZipException) {
            return EntriesResult.Error(BackupError.NotAnArchive)
        } catch (_: IOException) {
            return EntriesResult.Error(BackupError.ReadFailed)
        } catch (_: IllegalArgumentException) {
            return EntriesResult.Error(BackupError.NotAnArchive)
        }
        return EntriesResult.Ok(entries)
    }

    /** Rejects absolute paths, traversal segments, backslashes, drive letters and control characters. */
    fun isSafePath(path: String): Boolean {
        if (path.isEmpty() || path.length > 128) return false
        if (path.startsWith("/") || path.contains('\\') || path.contains(':')) return false
        if (path.any { it.isISOControl() }) return false
        return path.split('/').none { it == ".." || it == "." }
    }

    /** Reads at most [limit] bytes; returns null when the stream is longer. */
    private fun readCapped(input: InputStream, limit: Long): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
