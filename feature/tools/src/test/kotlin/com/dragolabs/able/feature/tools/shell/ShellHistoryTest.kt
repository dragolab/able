package com.dragolabs.able.feature.tools.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellHistoryTest {

    @Test
    fun recordAppendsTrimmedAndIgnoresBlank() {
        val h = ShellHistory.record(emptyList(), "  ls -l  ")
        assertEquals(listOf("ls -l"), h)
        assertEquals(h, ShellHistory.record(h, "   "))
    }

    @Test
    fun recordMovesDuplicateToNewest() {
        val h = ShellHistory.record(listOf("a", "b", "c"), "a")
        assertEquals(listOf("b", "c", "a"), h)
    }

    @Test
    fun recordKeepsLastHundred() {
        var h = emptyList<String>()
        repeat(150) { h = ShellHistory.record(h, "cmd $it") }
        assertEquals(ShellHistory.MAX_ENTRIES, h.size)
        assertEquals("cmd 50", h.first())
        assertEquals("cmd 149", h.last())
    }

    @Test
    fun navigatorWalksBackAndRestoresDraft() {
        val entries = listOf("one", "two", "three")
        val nav = HistoryNavigator()
        assertTrue(nav.canGoPrevious(entries))
        assertFalse(nav.canGoNext())
        assertNull(nav.next(entries))

        assertEquals("three", nav.previous(entries, "draft"))
        assertEquals("two", nav.previous(entries, "three"))
        assertEquals("one", nav.previous(entries, "two"))
        assertFalse(nav.canGoPrevious(entries))
        assertNull(nav.previous(entries, "one"))

        assertEquals("two", nav.next(entries))
        assertEquals("three", nav.next(entries))
        assertEquals("draft", nav.next(entries))
        assertFalse(nav.canGoNext())
        assertNull(nav.next(entries))
    }

    @Test
    fun navigatorHandlesEmptyHistoryAndReset() {
        val nav = HistoryNavigator()
        assertFalse(nav.canGoPrevious(emptyList()))
        assertNull(nav.previous(emptyList(), "x"))
        nav.previous(listOf("a"), "x")
        nav.reset()
        assertNull(nav.position)
        assertFalse(nav.canGoNext())
    }

    @Test
    fun navigatorSurvivesHistoryShrinking() {
        val nav = HistoryNavigator()
        val long = listOf("a", "b", "c", "d")
        nav.previous(long, "")
        nav.previous(long, "")
        // History cleared to one entry while navigating.
        assertEquals("a", nav.previous(listOf("a"), ""))
    }

    @Test
    fun snippetUpsertAndRemove() {
        val a = ShellSnippet("1", "A", "ls")
        val b = ShellSnippet("2", "B", "df")
        var list = ShellSnippets.upsert(emptyList(), a)
        list = ShellSnippets.upsert(list, b)
        list = ShellSnippets.upsert(list, a.copy(label = "A2"))
        assertEquals(listOf("A2", "B"), list.map { it.label })
        assertEquals(listOf(b), ShellSnippets.remove(list, "1"))
    }

    @Test
    fun outputBufferKeepsNewestLines() {
        val buffer = ShellOutputBuffer(maxLines = 3)
        repeat(5) { buffer.append(ShellLineKind.STDOUT, "l$it") }
        assertEquals(listOf("l2", "l3", "l4"), buffer.snapshot().map { it.text })
        assertEquals(2L, buffer.droppedLines)
        buffer.clear()
        assertEquals(0, buffer.size)
        assertEquals(0L, buffer.droppedLines)
    }

    @Test
    fun exportLabelsStreamsAndTruncatesOldest() {
        val buffer = ShellOutputBuffer()
        buffer.append(ShellLineKind.COMMAND, "ls")
        buffer.append(ShellLineKind.STDOUT, "file")
        buffer.append(ShellLineKind.STDERR, "denied")
        buffer.append(ShellLineKind.EXIT, "", exitCode = 1, end = ShellEnd.EXITED)
        val (text, truncated) = ShellOutputBuffer.export(buffer.snapshot())
        assertEquals("$ ls\nfile\n[stderr] denied\n# exit 1", text)
        assertFalse(truncated)

        val (short, cut) = ShellOutputBuffer.export(buffer.snapshot(), maxChars = 20)
        assertTrue(cut)
        assertEquals("# exit 1", short)
    }
}
