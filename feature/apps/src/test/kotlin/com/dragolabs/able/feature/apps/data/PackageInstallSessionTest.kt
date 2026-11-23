package com.dragolabs.able.feature.apps.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Install-session commands used to restore a user app from Able's APK backup. */
class PackageInstallSessionTest {

    @Test
    fun buildsInstallCreateWithAndWithoutInstaller() {
        assertEquals("pm install-create -r -i com.android.vending --user 0", PackageCommands.installCreate(0, "com.android.vending"))
        assertEquals("pm install-create -r --user 10", PackageCommands.installCreate(10, null))
        // An unsafe installer name is dropped rather than interpolated.
        assertEquals("pm install-create -r --user 0", PackageCommands.installCreate(0, "com.x;reboot"))
        assertNull(PackageCommands.installCreate(-1, null))
    }

    @Test
    fun buildsWriteCommitAndAbandon() {
        assertEquals("pm install-write -S 86129810 1373719752 base.apk -", PackageCommands.installWrite(1373719752, 86129810, "base.apk"))
        assertEquals(
            "pm install-write -S 42 7 split_config.arm64_v8a.apk -",
            PackageCommands.installWrite(7, 42, "split_config.arm64_v8a.apk"),
        )
        assertEquals("pm install-commit 7", PackageCommands.installCommit(7))
        assertEquals("pm install-abandon 7", PackageCommands.installAbandon(7))
        assertNull(PackageCommands.installWrite(0, 42, "base.apk"))
        assertNull(PackageCommands.installWrite(7, 0, "base.apk"))
        assertNull(PackageCommands.installCommit(0))
        assertNull(PackageCommands.installAbandon(-3))
    }

    @Test
    fun rejectsUnsafeApkNames() {
        listOf("base.apk", "split_config.xxhdpi.apk", "split_0.apk", "feature-camera.apk").forEach {
            assertTrue(it, PackageCommands.isValidApkName(it))
        }
        listOf("", "base", "base.apk;reboot", "../base.apk", "a b.apk", ".apk", "base.apk -", "x\$(id).apk", "-S.apk").forEach {
            assertFalse(it, PackageCommands.isValidApkName(it))
        }
        assertNull(PackageCommands.installWrite(7, 42, "../evil.apk"))
    }

    @Test
    fun parsesSessionIdFromPmOutput() {
        // Real output from the API 36.1 emulator, 2026-11-22.
        assertEquals(1373719752, PackageCommands.parseSessionId("Success: created install session [1373719752]"))
        assertEquals(5, PackageCommands.parseSessionId("noise\nSuccess: created install session [5]\n"))
        assertNull(PackageCommands.parseSessionId("Failure [INSTALL_FAILED_INTERNAL_ERROR]"))
        assertNull(PackageCommands.parseSessionId("Success: created install session []"))
        assertNull(PackageCommands.parseSessionId("Success: created install session [0]"))
        assertNull(PackageCommands.parseSessionId(""))
    }

    @Test
    fun detectsShellFailures() {
        assertFalse(PackageCommands.isShellFailure(0, "Success: streamed 86129810 bytes", ""))
        assertFalse(PackageCommands.isShellFailure(0, "Success", ""))
        // `pm` can print a failure while exiting 0, or throw.
        assertTrue(
            PackageCommands.isShellFailure(
                0,
                "Failure [INSTALL_PARSE_FAILED_NOT_APK: Failed to parse /data/app/vmdl1.tmp/base.apk]",
                "",
            ),
        )
        assertTrue(PackageCommands.isShellFailure(1, "", ""))
        assertTrue(
            PackageCommands.isShellFailure(
                0,
                "",
                "Exception occurred while executing 'install-commit':\njava.lang.SecurityException: Files still open",
            ),
        )
        assertTrue(
            PackageCommands.isShellFailure(
                1,
                "",
                "android.content.pm.PackageManager\$NameNotFoundException: Package com.example.app doesn't exist",
            ),
        )
    }

    @Test
    fun extractsFailureReason() {
        assertEquals(
            "INSTALL_FAILED_VERSION_DOWNGRADE",
            PackageCommands.failureReason("Failure [INSTALL_FAILED_VERSION_DOWNGRADE]", ""),
        )
        assertEquals(
            "java.lang.SecurityException: Caller has no access to session 999999",
            PackageCommands.failureReason(
                "",
                "\nException occurred while executing 'install-commit':\n" +
                    "java.lang.SecurityException: Caller has no access to session 999999\n" +
                    "\tat com.android.server.pm.PackageInstallerService.openSessionInternal(PackageInstallerService.java:1328)",
            ),
        )
        assertNull(PackageCommands.failureReason("", ""))
    }

    @Test
    fun buildsStoreUris() {
        assertEquals(
            "market://details?id=me.linshen.abattery" to "https://play.google.com/store/apps/details?id=me.linshen.abattery",
            PackageCommands.storeUris("me.linshen.abattery"),
        )
        assertNull(PackageCommands.storeUris("bad name"))
    }

    @Test
    fun shellResultUsesFailureDetection() {
        assertTrue(ShellResult(0, "Failure [X]", "").failed)
        assertFalse(ShellResult(0, "Success", "").failed)
    }
}
