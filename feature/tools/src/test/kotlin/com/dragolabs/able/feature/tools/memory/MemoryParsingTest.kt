package com.dragolabs.able.feature.tools.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Samples captured from an Android 16 (API 36) emulator on 2026-11-22. */
class MemoryParsingTest {

    @Test
    fun parsesPsRowsAndSkipsHeaderAndKernelThreads() {
        val output = """
              PID   UID    RSS NAME
                2     0      0 [kthreadd]
             1062 10194 237180 com.android.systemui
             2375 10153 131524 com.android.vending:background
        """.trimIndent()
        val rows = parsePs(output)
        assertEquals(2, rows.size)
        assertEquals(PsRow(1062, 10194, 237180, "com.android.systemui"), rows[0])
        assertEquals("com.android.vending", basePackageOf(rows[1].name))
    }

    @Test
    fun parsesLruImportance() {
        val output = """
            ACTIVITY MANAGER LRU PROCESSES (dumpsys activity lru)
              #24: fg       TOP  LCMNFUATI 1215:com.google.android.apps.nexuslauncher/u0a191 act:activities|recents
              #15: prcp + 1 BFGS ---NFU-TI 1681:com.google.android.apps.messaging:rcs/u0a154
              #45: cch  + 5 CEM  --------- 2371:com.google.android.gms.unstable/u0a155
              # 1: pers     PER  LCMNFUATI 1062:com.android.systemui/u0a194
        """.trimIndent()
        val lru = parseLru(output)
        assertEquals(ProcessImportance.FOREGROUND, lru[1215])
        assertEquals(ProcessImportance.VISIBLE, lru[1681])
        assertEquals(ProcessImportance.CACHED, lru[2371])
        assertEquals(ProcessImportance.PERSISTENT, lru[1062])
    }

    @Test
    fun parsesMeminfo() {
        val values = parseMeminfo("MemTotal:        2012756 kB\nSwapTotal:       1509560 kB\nSwapFree:        1467744 kB\n")
        assertEquals(1509560L, values["SwapTotal"])
        assertEquals(1467744L, values["SwapFree"])
    }

    @Test
    fun rejectsUnsafePackageNames() {
        assertTrue(isSafePackageName("com.example.app_2"))
        assertFalse(isSafePackageName("com.example; reboot"))
        assertFalse(isSafePackageName("com.example\$(id)"))
        assertFalse(isSafePackageName(""))
    }
}
