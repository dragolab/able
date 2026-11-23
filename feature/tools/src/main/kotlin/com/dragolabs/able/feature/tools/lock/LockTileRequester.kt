package com.dragolabs.able.feature.tools.lock

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import androidx.core.content.ContextCompat

enum class TileRequestResult { ADDED, ALREADY_ADDED, NOT_ADDED, FAILED }

/**
 * Requests the Able screen lock Quick Settings tile through Android's own consent dialog
 * (`StatusBarManager.requestAddTileService`, API 33). The tile service lives in `:app`; it is
 * referenced by class name because feature modules cannot depend on the app module.
 */
class LockTileRequester(private val context: Context) {
    private val component = ComponentName(context.packageName, TILE_CLASS)

    val isAvailable: Boolean = runCatching {
        context.packageManager.getServiceInfo(component, PackageManager.ComponentInfoFlags.of(0L))
        true
    }.getOrDefault(false)

    /** Returns false when the request could not be sent. [onResult] runs on the main thread. */
    fun request(label: String, onResult: (TileRequestResult) -> Unit): Boolean {
        if (!isAvailable) return false
        val manager = context.getSystemService(StatusBarManager::class.java) ?: return false
        return runCatching {
            manager.requestAddTileService(
                component,
                label,
                Icon.createWithResource(context, context.applicationInfo.icon),
                ContextCompat.getMainExecutor(context),
            ) { code ->
                onResult(
                    when (code) {
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> TileRequestResult.ADDED
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> TileRequestResult.ALREADY_ADDED
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> TileRequestResult.NOT_ADDED
                        else -> TileRequestResult.FAILED
                    },
                )
            }
            true
        }.getOrDefault(false)
    }

    private companion object {
        const val TILE_CLASS = "com.dragolabs.able.tile.ScreenLockTileService"
    }
}
