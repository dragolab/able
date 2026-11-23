package com.dragolabs.able.core.data

import com.dragolabs.able.core.capability.DeviceCapabilities
import com.dragolabs.able.core.capability.CapabilityManager
import com.dragolabs.able.core.capability.PermissionCoordinator
import com.dragolabs.able.core.capability.SecureSettingsAdapter
import com.dragolabs.able.core.capability.ShizukuAdapter
import com.dragolabs.able.core.capability.ShizukuConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

/** Snapshot of optional accesses, used by the capability checklist (SET-002) and Home safety (HOME-003). */
data class AccessSnapshot(
    val shizuku: ShizukuConnectionState = ShizukuConnectionState.NOT_AVAILABLE,
    val accessibilityEnabled: Boolean = false,
    val usageAccess: Boolean = false,
    val notifications: Boolean = false,
    val writeSettings: Boolean = false,
    val writeSecureSettings: Boolean = false,
    val fineLocation: Boolean = false,
    val notificationPolicy: Boolean = false,
)

/**
 * Android does not broadcast most grant changes, so access state is re-read on [refresh]
 * (called when the activity resumes). Shizuku changes are observed live.
 */
class CapabilityStatusRepository(
    private val capabilityManager: CapabilityManager,
    private val shizuku: ShizukuAdapter,
    private val permissions: PermissionCoordinator,
    private val secureSettings: SecureSettingsAdapter,
    private val screenLock: ScreenLockController,
) {
    private val polled = MutableStateFlow(read())

    val device: DeviceCapabilities by lazy { capabilityManager.evaluateCapabilities() }

    val access: Flow<AccessSnapshot> = combine(polled, shizuku.connectionState) { snapshot, shizukuState ->
        snapshot.copy(shizuku = shizukuState)
    }

    val current: StateFlow<AccessSnapshot> get() = polled.asStateFlow()

    fun refresh() {
        shizuku.updateState()
        polled.value = read()
    }

    private fun read() = AccessSnapshot(
        shizuku = shizuku.connectionState.value,
        accessibilityEnabled = runCatching { screenLock.isServiceEnabled() }.getOrDefault(false),
        usageAccess = permissions.hasUsageStatsPermission(),
        notifications = permissions.hasNotificationPermission(),
        writeSettings = permissions.hasWriteSettingsPermission(),
        writeSecureSettings = secureSettings.hasWriteSecureSettings(),
        fineLocation = permissions.hasFineLocationPermission(),
        notificationPolicy = permissions.hasNotificationPolicyAccess(),
    )
}
