package com.dragolabs.able.feature.settings.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {

    private val dns = DnsSectionDto(listOf(DnsProfileDto("quad9", "Quad9", "HOSTNAME", "dns.quad9.net")))
    private val gps = GpsSectionDto(listOf(GpsPointDto(45.0, 9.0, null, 5f, 1_700_000_000_000, "Home")))

    private fun archive(payload: BackupPayload = BackupPayload(dns = dns, gps = gps)): ByteArray =
        ByteArrayOutputStream().also { BackupArchive.write(it, payload, "1.0.0", "2026-11-22T10:00:00Z") }.toByteArray()

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, bytes) ->
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
    }.toByteArray()

    private fun manifestJson(manifest: BackupManifest) =
        BackupArchive.json.encodeToString(BackupManifest.serializer(), manifest).toByteArray()

    private fun read(bytes: ByteArray) = BackupArchive.read(ByteArrayInputStream(bytes))

    @Test
    fun roundTripPreservesSectionsAndManifest() {
        val result = read(archive())
        assertTrue(result is BackupReadResult.Valid)
        val backup = (result as BackupReadResult.Valid).backup
        assertEquals(BackupArchive.SCHEMA_VERSION, backup.manifest.schemaVersion)
        assertEquals(setOf(BackupSection.DNS_PROFILES, BackupSection.GPS_POINTS), backup.payload.sections)
        assertEquals(dns, backup.payload.dns)
        assertEquals(gps, backup.payload.gps)
        assertNull(backup.payload.battery)
        assertTrue(backup.manifest.excluded.contains(BackupExclusion.HOTSPOT_PASSWORDS.key))
        assertTrue(backup.unsupported.isEmpty())
    }

    @Test
    fun rejectsNonZipInput() {
        assertEquals(BackupReadResult.Invalid(BackupError.NotAnArchive), read("not a zip".toByteArray()))
    }

    @Test
    fun rejectsMissingManifest() {
        val bytes = zip("sections/dns_profiles.json" to "{}".toByteArray())
        assertEquals(BackupReadResult.Invalid(BackupError.MissingManifest), read(bytes))
    }

    @Test
    fun rejectsTraversalPaths() {
        val bytes = zip("../evil.json" to "{}".toByteArray())
        assertEquals(BackupReadResult.Invalid(BackupError.UnsafePath("../evil.json")), read(bytes))
        assertFalse(BackupArchive.isSafePath("/abs/path"))
        assertFalse(BackupArchive.isSafePath("a\\b"))
        assertFalse(BackupArchive.isSafePath("C:/x"))
        assertTrue(BackupArchive.isSafePath("sections/dns_profiles.json"))
    }

    @Test
    fun rejectsNewerSchemaVersion() {
        val manifest = BackupManifest(schemaVersion = 99, appVersion = "9", createdAt = "x", sections = emptyList(), entries = emptyList())
        val result = BackupArchive.validate(manifest, emptyMap())
        assertEquals(BackupReadResult.Invalid(BackupError.UnsupportedSchema(99)), result)
    }

    @Test
    fun rejectsChecksumMismatch() {
        val section = BackupSection.DNS_PROFILES
        val content = """{"profiles":[]}""".toByteArray()
        val manifest = BackupManifest(
            schemaVersion = 1, appVersion = "1", createdAt = "x",
            sections = listOf(section.key),
            entries = listOf(ManifestEntry(section.entryPath, section.key, content.size.toLong(), "00".repeat(32))),
        )
        val bytes = zip(BackupArchive.MANIFEST_PATH to manifestJson(manifest), section.entryPath to content)
        assertEquals(BackupReadResult.Invalid(BackupError.ChecksumMismatch(section.entryPath)), read(bytes))
    }

    @Test
    fun rejectsManifestPathThatDiffersFromFixedPath() {
        val section = BackupSection.DNS_PROFILES
        val content = """{"profiles":[]}""".toByteArray()
        val manifest = BackupManifest(
            schemaVersion = 1, appVersion = "1", createdAt = "x",
            sections = listOf(section.key),
            entries = listOf(ManifestEntry("other/dns.json", section.key, content.size.toLong(), BackupArchive.sha256Hex(content))),
        )
        val result = BackupArchive.validate(manifest, mapOf("other/dns.json" to content))
        assertEquals(BackupReadResult.Invalid(BackupError.UnsafePath("other/dns.json")), result)
    }

    @Test
    fun reportsUnknownSectionsAsUnsupported() {
        val section = BackupSection.DNS_PROFILES
        val content = """{"profiles":[]}""".toByteArray()
        val manifest = BackupManifest(
            schemaVersion = 1, appVersion = "1", createdAt = "x",
            sections = listOf(section.key, "future_section"),
            entries = listOf(ManifestEntry(section.entryPath, section.key, content.size.toLong(), BackupArchive.sha256Hex(content))),
        )
        val result = BackupArchive.validate(manifest, mapOf(section.entryPath to content, "extra.bin" to byteArrayOf(1)))
        assertTrue(result is BackupReadResult.Valid)
        assertEquals(listOf("future_section", "extra.bin"), (result as BackupReadResult.Valid).backup.unsupported)
    }

    @Test
    fun rejectsArchiveWithOnlyUnsupportedSections() {
        val manifest = BackupManifest(schemaVersion = 1, appVersion = "1", createdAt = "x", sections = listOf("future"), entries = emptyList())
        assertEquals(BackupReadResult.Invalid(BackupError.NoSupportedSections), BackupArchive.validate(manifest, emptyMap()))
    }

    @Test
    fun rejectsMalformedSectionJson() {
        val section = BackupSection.DNS_PROFILES
        val content = "{not json".toByteArray()
        val manifest = BackupManifest(
            schemaVersion = 1, appVersion = "1", createdAt = "x",
            sections = listOf(section.key),
            entries = listOf(ManifestEntry(section.entryPath, section.key, content.size.toLong(), BackupArchive.sha256Hex(content))),
        )
        assertEquals(
            BackupReadResult.Invalid(BackupError.MalformedSection(section)),
            BackupArchive.validate(manifest, mapOf(section.entryPath to content)),
        )
    }

    @Test
    fun sanitizerDropsOutOfRangeValues() {
        val points = BackupSanitizer.gps(
            GpsSectionDto(
                listOf(
                    GpsPointDto(45.0, 9.0, null, 3f, 1L, "ok"),
                    GpsPointDto(91.0, 9.0, null, 3f, 1L, "bad lat"),
                    GpsPointDto(45.0, 9.0, null, -1f, 1L, "bad accuracy"),
                ),
            ),
        )
        assertEquals(1, points.items.size)
        assertEquals(2, points.rejected)

        val dnsResult = BackupSanitizer.dns(
            DnsSectionDto(
                listOf(
                    DnsProfileDto("a", "A", "HOSTNAME", "dns.example.com"),
                    DnsProfileDto("b", "B", "HOSTNAME", "not a host"),
                    DnsProfileDto("c", "C", "UNKNOWN_MODE"),
                ),
            ),
        )
        assertEquals(listOf("a"), dnsResult.items.map { it.id })
        assertEquals(2, dnsResult.rejected)
    }

    @Test
    fun sanitizerRejectsInvalidBatteryThresholdsButKeepsRules() {
        val result = BackupSanitizer.battery(
            BatterySectionDto(
                savingsTriggerPercent = 5, savingsResetPercent = 10, emergencyTriggerPercent = 10, emergencyResetPercent = 20,
                notteScheduleEnabled = true, notteScreenOffEnabled = true, notteStartTime = "00:30", notteEndTime = "07:30",
                screenOffDelayMinutes = 20,
                appRules = listOf(AppRuleDto("com.example.app", "NOTTE", "OSSERVA"), AppRuleDto("bad pkg", "NOTTE", "OSSERVA")),
            ),
        )
        assertNull(result.scalars)
        assertEquals(1, result.rules.size)
        assertEquals(2, result.rejected)
    }
}
