package com.dragolabs.able.feature.tools.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageFilesTest {

    private fun file(name: String, size: Long, modified: Long = 1L, kind: FileKind = FileKind.OTHER) =
        ReviewFile(uriString = "content://x/$name", displayName = name, sizeBytes = size, lastModifiedEpochMs = modified, mimeType = null, kind = kind)

    @Test
    fun classifiesByMimeThenExtension() {
        assertEquals(FileKind.IMAGE, classifyFile("image/jpeg", "a.bin"))
        assertEquals(FileKind.VIDEO, classifyFile("video/mp4", "clip"))
        assertEquals(FileKind.AUDIO, classifyFile("audio/mpeg", "song"))
        assertEquals(FileKind.ARCHIVE, classifyFile("application/vnd.android.package-archive", "app"))
        assertEquals(FileKind.ARCHIVE, classifyFile(null, "Backup.ZIP"))
        assertEquals(FileKind.ARCHIVE, classifyFile("application/octet-stream", "game.xapk"))
        assertEquals(FileKind.DOCUMENT, classifyFile("application/pdf", "doc"))
        assertEquals(FileKind.DOCUMENT, classifyFile("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "x"))
        assertEquals(FileKind.DOCUMENT, classifyFile(null, "notes.txt"))
        assertEquals(FileKind.OTHER, classifyFile("application/octet-stream", "test_big.bin"))
        assertEquals(FileKind.OTHER, classifyFile(null, "noextension"))
    }

    @Test
    fun filtersByKindAndNameCaseInsensitively() {
        val items = listOf(
            file("Holiday.mp4", 300, kind = FileKind.VIDEO),
            file("holiday.jpg", 200, kind = FileKind.IMAGE),
            file("setup.apk", 100, kind = FileKind.ARCHIVE),
        )
        val videos = applyFileFilters(items, FileFilters(kinds = setOf(FileKind.VIDEO)))
        assertEquals(listOf("Holiday.mp4"), videos.map { it.displayName })

        val named = applyFileFilters(items, FileFilters(query = " HOLIDAY "))
        assertEquals(listOf("Holiday.mp4", "holiday.jpg"), named.map { it.displayName })

        val all = applyFileFilters(items, FileFilters())
        assertEquals(3, all.size)
    }

    @Test
    fun sortsBySizeDateAndName() {
        val items = listOf(
            file("b", size = 10, modified = 3_000),
            file("a", size = 30, modified = 0), // unknown date sorts last
            file("C", size = 20, modified = 1_000),
        )
        assertEquals(listOf("a", "C", "b"), applyFileFilters(items, FileFilters(sort = FileSort.SIZE)).map { it.displayName })
        assertEquals(listOf("C", "b", "a"), applyFileFilters(items, FileFilters(sort = FileSort.DATE)).map { it.displayName })
        assertEquals(listOf("a", "b", "C"), applyFileFilters(items, FileFilters(sort = FileSort.NAME)).map { it.displayName })
    }

    @Test
    fun customSizeAndScanEquality() {
        val custom = FileFilters(size = SizeThreshold.CUSTOM, customSizeMb = 3)
        assertEquals(3 * MB, custom.minSizeBytes)
        assertEquals(0L, FileFilters(size = SizeThreshold.ANY).minSizeBytes)
        // Type, name and sort changes never need a new query; size/age/location do.
        val base = FileFilters()
        assertTrue(base.sameScan(base.copy(kinds = setOf(FileKind.IMAGE), query = "x", sort = FileSort.NAME)))
        assertFalse(base.sameScan(base.copy(age = AgeThreshold.D30)))
        assertFalse(base.sameScan(base.copy(location = FileLocation.DOWNLOADS)))
        assertFalse(base.sameScan(base.copy(size = SizeThreshold.MB_500)))
        // Custom equal to a preset is the same query.
        assertTrue(FileFilters(size = SizeThreshold.MB_100).sameScan(FileFilters(size = SizeThreshold.CUSTOM, customSizeMb = 100)))
    }
}
