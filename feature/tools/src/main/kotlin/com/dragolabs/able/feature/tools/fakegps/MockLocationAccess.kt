package com.dragolabs.able.feature.tools.fakegps

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.dragolabs.able.core.capability.ShizukuAdapter
import com.dragolabs.able.core.common.AbleResult

/** Snapshot of everything Fake GPS needs before it can start. */
data class MockSetupState(
    /** App-op `android:mock_location` is `allow` for Able (Developer options "Select mock location app"). */
    val mockAppSelected: Boolean = false,
    val developerOptionsEnabled: Boolean = false,
    val shizukuReady: Boolean = false,
    val fineLocationGranted: Boolean = false,
    val locationServicesOn: Boolean = true,
)

/** Result of the one-tap Shizuku setup, verified by reading the app-op back. */
enum class ShizukuMockResult { APPLIED, REJECTED, NOT_CONFIRMED, UNAVAILABLE }

/**
 * Mock location access checks (Android): an app may feed test providers only when it declares
 * `ACCESS_MOCK_LOCATION` and the user selected it as "mock location app" in Developer options,
 * which sets the app-op `android:mock_location` (AppOpsManager.OPSTR_MOCK_LOCATION) to allow.
 * The shell user (ADB / Shizuku) can set the same app-op with `appops set`.
 */
internal class MockLocationAccess(
    private val context: Context,
    private val shizuku: ShizukuAdapter,
) {
    private val packageName = context.packageName

    fun read(): MockSetupState = MockSetupState(
        mockAppSelected = isMockAppSelected(context),
        developerOptionsEnabled = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0,
        ) == 1,
        shizukuReady = shizuku.isReady(),
        fineLocationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED,
        locationServicesOn = context.getSystemService(LocationManager::class.java)?.isLocationEnabled ?: false,
    )

    /** Sets `android:mock_location allow` through Shizuku, then reads it back (shell and AppOpsManager). */
    suspend fun allowWithShizuku(): ShizukuMockResult {
        if (!shizuku.isReady()) return ShizukuMockResult.UNAVAILABLE
        val set = shizuku.executeCommand("appops set $packageName $OP_NAME allow")
        if (set is AbleResult.Failure) return ShizukuMockResult.REJECTED
        val readBack = shizuku.executeCommand("appops get $packageName $OP_NAME")
        val shellSaysAllow = (readBack as? AbleResult.Success)?.data?.contains("allow", ignoreCase = true) == true
        return when {
            isMockAppSelected(context) -> ShizukuMockResult.APPLIED
            shellSaysAllow -> ShizukuMockResult.NOT_CONFIRMED
            else -> ShizukuMockResult.REJECTED
        }
    }

    companion object {
        /** Public app-op name (AppOpsManager.OPSTR_MOCK_LOCATION). */
        const val OP_NAME = AppOpsManager.OPSTR_MOCK_LOCATION

        @Suppress("DEPRECATION") // unsafeCheckOpNoThrow: replacement needs API 36+; minSdk is 33.
        fun isMockAppSelected(context: Context): Boolean {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
            return runCatching {
                appOps.unsafeCheckOpNoThrow(OP_NAME, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
            }.getOrDefault(false)
        }
    }
}
