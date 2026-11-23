package com.dragolabs.able.feature.tools.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DangerousCommandMatcherTest {

    private fun risks(command: String) = DangerousCommandMatcher.match(command)

    @Test
    fun starterSnippetsAreSafe() {
        ShellSnippets.STARTER_COMMANDS.forEach { assertFalse(it, DangerousCommandMatcher.isDangerous(it)) }
    }

    @Test
    fun readOnlyCommandsAreSafe() {
        listOf(
            "ls -la /sdcard",
            "settings get secure enabled_accessibility_services",
            "settings list global",
            "pm list packages | grep format",
            "echo rm -rf /",
            "dumpsys window | grep mCurrentFocus",
            "getprop | grep reboot",
            "cmd package list packages -d",
            "cat /proc/meminfo",
            "",
        ).forEach { assertTrue(it, risks(it).isEmpty()) }
    }

    @Test
    fun detectsRequiredPatterns() {
        assertEquals(setOf(ShellRisk.DELETE_FILES), risks("rm -rf /sdcard/Download/tmp"))
        assertEquals(setOf(ShellRisk.UNINSTALL_APP), risks("pm uninstall --user 0 com.example"))
        assertEquals(setOf(ShellRisk.CLEAR_APP_DATA), risks("pm clear com.example"))
        assertEquals(setOf(ShellRisk.REBOOT), risks("reboot"))
        assertEquals(setOf(ShellRisk.REBOOT), risks("reboot recovery"))
        assertEquals(setOf(ShellRisk.CHANGE_SETTINGS), risks("settings put global adb_enabled 0"))
        assertEquals(setOf(ShellRisk.WINDOW_MANAGER), risks("wm density 300"))
        assertEquals(setOf(ShellRisk.RAW_DISK_WRITE), risks("dd if=/dev/zero of=/sdcard/x bs=1M count=1"))
        assertEquals(setOf(ShellRisk.FORMAT), risks("format /data"))
    }

    @Test
    fun isCaseInsensitiveAndToleratesWhitespace() {
        assertTrue(DangerousCommandMatcher.isDangerous("  PM   Uninstall com.example "))
        assertTrue(DangerousCommandMatcher.isDangerous("Settings PUT system screen_brightness 10"))
    }

    @Test
    fun detectsCommandsInChainsPipesAndSubshells() {
        assertEquals(setOf(ShellRisk.DELETE_FILES), risks("ls /sdcard && rm /sdcard/a.txt"))
        assertEquals(setOf(ShellRisk.DELETE_FILES), risks("ls;rm x"))
        assertEquals(setOf(ShellRisk.DELETE_FILES), risks("find /sdcard -name '*.tmp' | xargs rm"))
        assertEquals(setOf(ShellRisk.REBOOT), risks("echo \$(reboot)"))
        assertEquals(setOf(ShellRisk.REBOOT), risks("echo `reboot`"))
        assertEquals(
            setOf(ShellRisk.CLEAR_APP_DATA, ShellRisk.UNINSTALL_APP),
            risks("pm clear a.b || pm uninstall a.b"),
        )
    }

    @Test
    fun seesThroughWrappersAndPaths() {
        assertTrue(DangerousCommandMatcher.isDangerous("/system/bin/rm -r /sdcard/x"))
        assertTrue(DangerousCommandMatcher.isDangerous("sh -c \"rm -rf /sdcard/x\""))
        assertTrue(DangerousCommandMatcher.isDangerous("busybox rm x"))
        assertTrue(DangerousCommandMatcher.isDangerous("timeout 5 reboot"))
        assertTrue(DangerousCommandMatcher.isDangerous("FOO=1 settings put global x 1"))
        assertTrue(DangerousCommandMatcher.isDangerous("su -c pm clear com.example"))
    }

    @Test
    fun detectsCmdPackageAndOtherVariants() {
        assertEquals(setOf(ShellRisk.UNINSTALL_APP), risks("cmd package uninstall -k com.example"))
        assertEquals(setOf(ShellRisk.CLEAR_APP_DATA), risks("cmd package clear com.example"))
        assertEquals(setOf(ShellRisk.UNINSTALL_APP), risks("pm uninstall-system-updates com.example"))
        assertEquals(setOf(ShellRisk.CHANGE_SETTINGS), risks("settings delete secure foo"))
        assertEquals(setOf(ShellRisk.REBOOT), risks("svc power reboot"))
        assertEquals(setOf(ShellRisk.FORMAT), risks("mkfs.ext4 /dev/block/x"))
        assertEquals(setOf(ShellRisk.WINDOW_MANAGER), risks("wm size reset"))
    }

    @Test
    fun doesNotMatchSubstringsOfOtherCommands() {
        assertFalse(DangerousCommandMatcher.isDangerous("rmmod x"))
        assertFalse(DangerousCommandMatcher.isDangerous("warm"))
        assertFalse(DangerousCommandMatcher.isDangerous("formatter --help"))
        assertFalse(DangerousCommandMatcher.isDangerous("add x"))
        assertFalse(DangerousCommandMatcher.isDangerous("pm path com.example"))
    }
}
