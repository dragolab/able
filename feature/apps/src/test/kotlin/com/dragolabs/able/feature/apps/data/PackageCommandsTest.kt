package com.dragolabs.able.feature.apps.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageCommandsTest {

    @Test
    fun acceptsRealPackageNames() {
        listOf(
            "com.google.android.youtube",
            "org.lineageos.jelly",
            "android.ext.shared",
            "com.samsung.android.app_x2",
            "a.b",
        ).forEach { assertTrue(it, PackageCommands.isValidPackageName(it)) }
    }

    @Test
    fun rejectsInvalidOrDangerousNames() {
        listOf(
            "",
            "android",
            "com.example;reboot",
            "com.example && rm -rf /",
            "com.example\nreboot",
            "com.example \$(id)",
            "com.example`id`",
            "com..example",
            ".com.example",
            "com.example.",
            "com.1example",
            "1com.example",
            "com.exa-mple",
            "com.example'",
            "com.example|sh",
            "c." + "a".repeat(300),
        ).forEach { assertFalse(it, PackageCommands.isValidPackageName(it)) }
    }

    @Test
    fun buildsExpectedCommands() {
        val pkg = "com.example.app"
        assertEquals("pm disable-user --user 0 $pkg", PackageCommands.command(PackageAction.DISABLE, 0, pkg))
        assertEquals("pm enable --user 10 $pkg", PackageCommands.command(PackageAction.ENABLE, 10, pkg))
        assertEquals("pm uninstall -k --user 0 $pkg", PackageCommands.command(PackageAction.UNINSTALL_FOR_USER, 0, pkg))
        assertEquals(
            "cmd package install-existing --user 0 $pkg",
            PackageCommands.command(PackageAction.REINSTALL_FOR_USER, 0, pkg),
        )
        assertEquals("am force-stop --user 0 $pkg", PackageCommands.forceStop(0, pkg))
        assertEquals("pidof $pkg || true", PackageCommands.pidOf(pkg))
    }

    @Test
    fun refusesToBuildWithInvalidInput() {
        assertNull(PackageCommands.command(PackageAction.DISABLE, 0, "com.example;reboot"))
        assertNull(PackageCommands.command(PackageAction.DISABLE, -1, "com.example.app"))
        assertNull(PackageCommands.forceStop(0, "x"))
        assertNull(PackageCommands.pidOf("com.example app"))
    }

    @Test
    fun confirmationFollowsReadBackState() {
        val enabled = PackageStateSnapshot(installedForUser = true, enabled = true, archivedBySystem = false)
        val disabled = enabled.copy(enabled = false)
        val uninstalled = enabled.copy(installedForUser = false)

        assertTrue(PackageCommands.isConfirmed(PackageAction.DISABLE, disabled))
        assertFalse(PackageCommands.isConfirmed(PackageAction.DISABLE, enabled))
        assertFalse(PackageCommands.isConfirmed(PackageAction.DISABLE, null))

        assertTrue(PackageCommands.isConfirmed(PackageAction.ENABLE, enabled))
        assertFalse(PackageCommands.isConfirmed(PackageAction.ENABLE, disabled))

        assertTrue(PackageCommands.isConfirmed(PackageAction.UNINSTALL_FOR_USER, uninstalled))
        assertTrue(PackageCommands.isConfirmed(PackageAction.UNINSTALL_FOR_USER, null))
        assertFalse(PackageCommands.isConfirmed(PackageAction.UNINSTALL_FOR_USER, enabled))

        assertTrue(PackageCommands.isConfirmed(PackageAction.REINSTALL_FOR_USER, disabled))
        assertFalse(PackageCommands.isConfirmed(PackageAction.REINSTALL_FOR_USER, uninstalled))
        assertFalse(PackageCommands.isConfirmed(PackageAction.REINSTALL_FOR_USER, null))
    }
}
