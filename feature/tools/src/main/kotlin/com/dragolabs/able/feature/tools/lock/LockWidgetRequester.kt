package com.dragolabs.able.feature.tools.lock

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/**
 * Pins the transparent "double tap to lock" home-screen widget through Android's own
 * confirmation dialog (`AppWidgetManager.requestPinAppWidget`). The provider lives in `:app`;
 * it is referenced by class name because feature modules cannot depend on the app module.
 */
class LockWidgetRequester(context: Context) {
    private val appContext = context.applicationContext
    private val component = ComponentName(appContext.packageName, PROVIDER_CLASS)
    private val manager: AppWidgetManager? = AppWidgetManager.getInstance(appContext)

    /** False when the current launcher does not support pinning widgets from apps. */
    val canPin: Boolean
        get() = runCatching { manager?.isRequestPinAppWidgetSupported == true }.getOrDefault(false)

    /** Number of lock widgets currently placed on any home screen. */
    fun placedCount(): Int = runCatching { manager?.getAppWidgetIds(component)?.size ?: 0 }.getOrDefault(0)

    /** Returns true when Android accepted the request; the launcher then asks the user to confirm. */
    fun request(): Boolean = runCatching {
        manager?.requestPinAppWidget(component, null, null) == true
    }.getOrDefault(false)

    private companion object {
        const val PROVIDER_CLASS = "com.dragolabs.able.lock.LockTapWidgetProvider"
    }
}
