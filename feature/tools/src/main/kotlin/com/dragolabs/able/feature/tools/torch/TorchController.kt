package com.dragolabs.able.feature.tools.torch

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Torch availability reported by Android's camera service for the selected flash unit. */
sealed interface TorchEvent {
    data class Mode(val enabled: Boolean) : TorchEvent
    /** The camera device is in use by another app or the flash unit is otherwise unavailable. */
    data object Unavailable : TorchEvent
    data class Strength(val level: Int) : TorchEvent
}

/**
 * Feature-local adapter over [CameraManager] torch APIs (TORCH-001). Strength levels use the
 * Android 13 APIs and are exposed only when the flash unit reports more than one level.
 */
class TorchController(context: Context) {
    private val cameraManager: CameraManager? = context.getSystemService(CameraManager::class.java)

    /** Flash-equipped camera, preferring the back camera. Null when no flash unit exists. */
    val cameraId: String? = runCatching {
        val manager = cameraManager ?: return@runCatching null
        val withFlash = manager.cameraIdList.filter { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        withFlash.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: withFlash.firstOrNull()
    }.getOrNull()

    val maxStrength: Int = characteristic(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
    val defaultStrength: Int = characteristic(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL) ?: 1

    val isAvailable: Boolean get() = cameraId != null
    val supportsStrength: Boolean get() = maxStrength > 1

    private fun <T> characteristic(key: CameraCharacteristics.Key<T>): T? = runCatching {
        val id = cameraId ?: return@runCatching null
        cameraManager?.getCameraCharacteristics(id)?.get(key)
    }.getOrNull()

    /** Live torch state. The camera service delivers the current state right after registration. */
    fun events(): Flow<TorchEvent> = callbackFlow {
        val manager = cameraManager
        val id = cameraId
        if (manager == null || id == null) {
            close()
            return@callbackFlow
        }
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == id) trySend(TorchEvent.Mode(enabled))
            }

            override fun onTorchModeUnavailable(cameraId: String) {
                if (cameraId == id) trySend(TorchEvent.Unavailable)
            }

            override fun onTorchStrengthLevelChanged(cameraId: String, newStrengthLevel: Int) {
                if (cameraId == id) trySend(TorchEvent.Strength(newStrengthLevel))
            }
        }
        manager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { manager.unregisterTorchCallback(callback) }
    }

    /** Returns false when Android rejected the request (for example, camera in use). */
    fun setTorch(on: Boolean, strength: Int? = null): Boolean {
        val manager = cameraManager ?: return false
        val id = cameraId ?: return false
        return try {
            if (on && strength != null && supportsStrength) {
                manager.turnOnTorchWithStrengthLevel(id, strength.coerceIn(1, maxStrength))
            } else {
                manager.setTorchMode(id, on)
            }
            true
        } catch (_: CameraAccessException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: UnsupportedOperationException) {
            false
        }
    }

    fun currentStrength(): Int? = runCatching {
        val id = cameraId ?: return@runCatching null
        if (supportsStrength) cameraManager?.getTorchStrengthLevel(id) else null
    }.getOrNull()
}
