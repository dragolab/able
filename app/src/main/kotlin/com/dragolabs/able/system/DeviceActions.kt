package com.dragolabs.able.system

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import com.dragolabs.able.MainActivity
import com.dragolabs.able.core.common.AbleResult
import com.dragolabs.able.core.data.ableContainer
import com.dragolabs.able.core.model.ToolId
import com.dragolabs.able.navigation.EXTRA_ROUTE
import com.dragolabs.able.navigation.deepLinkForTool
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Outcome of an action triggered from a tile or widget, reported honestly to the user. */
enum class DeviceActionOutcome { APPLIED, REJECTED, UNVERIFIED, UNSUPPORTED }

/**
 * Shared device actions for Quick Settings tiles and widgets (TORCH-001, SYS-001, LOCK-002).
 * Every privileged change is read back before it is reported as applied.
 */
object DeviceActions {

    fun torchCameraId(context: Context): String? {
        val cameraManager = context.getSystemService(CameraManager::class.java) ?: return null
        return runCatching {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    /** Reads the current torch state: Android reports it immediately on callback registration. */
    suspend fun readTorchState(context: Context): Boolean? {
        val cameraManager = context.getSystemService(CameraManager::class.java) ?: return null
        val id = torchCameraId(context) ?: return null
        return withTimeoutOrNull(500) {
            suspendCancellableCoroutine { cont ->
                val callback = object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        if (cameraId == id && cont.isActive) {
                            cameraManager.unregisterTorchCallback(this)
                            cont.resume(enabled)
                        }
                    }

                    override fun onTorchModeUnavailable(cameraId: String) {
                        if (cameraId == id && cont.isActive) {
                            cameraManager.unregisterTorchCallback(this)
                            cont.resume(false)
                        }
                    }
                }
                cameraManager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
                cont.invokeOnCancellation { cameraManager.unregisterTorchCallback(callback) }
            }
        }
    }

    suspend fun setTorch(context: Context, enabled: Boolean): DeviceActionOutcome {
        val cameraManager = context.getSystemService(CameraManager::class.java) ?: return DeviceActionOutcome.UNSUPPORTED
        val id = torchCameraId(context) ?: return DeviceActionOutcome.UNSUPPORTED
        return try {
            cameraManager.setTorchMode(id, enabled)
            if (readTorchState(context) == enabled) DeviceActionOutcome.APPLIED else DeviceActionOutcome.UNVERIFIED
        } catch (_: Exception) {
            DeviceActionOutcome.REJECTED
        }
    }

    /**
     * Toggles NFC through NfcAdapter (WRITE_SECURE_SETTINGS, advanced mode) or Shizuku, then
     * polls the adapter: enabling NFC is asynchronous and can take a few seconds.
     */
    suspend fun toggleNfc(context: Context): DeviceActionOutcome {
        val settings = context.ableContainer.secureSettings
        val current = settings.isNfcEnabled() ?: return DeviceActionOutcome.UNSUPPORTED
        if (!settings.canToggleNfcDirectly()) return DeviceActionOutcome.REJECTED
        val target = !current
        if (settings.setNfcEnabled(target) !is AbleResult.Success) return DeviceActionOutcome.REJECTED
        // Logged by the caller (ActionRunner) together with the user-visible report.
        repeat(NFC_READ_BACK_STEPS) {
            if (settings.isNfcEnabled() == target) return DeviceActionOutcome.APPLIED
            delay(NFC_READ_BACK_INTERVAL_MS)
        }
        return if (settings.isNfcEnabled() == target) DeviceActionOutcome.APPLIED else DeviceActionOutcome.UNVERIFIED
    }

    private const val NFC_READ_BACK_STEPS = 20
    private const val NFC_READ_BACK_INTERVAL_MS = 250L

    fun openAbleIntent(context: Context, route: String): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun openToolIntent(context: Context, tool: ToolId): Intent = openAbleIntent(context, deepLinkForTool(tool))
}
