package com.dragolabs.able.core.capability

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager

data class DeviceCapabilities(
    val hasFlashTorch: Boolean,
    val hasNfc: Boolean,
    val hasAccelerometer: Boolean,
    val hasMagnetometer: Boolean,
    val hasTelephony: Boolean,
    val hasTouchscreen: Boolean,
    val hasBattery: Boolean
)

class CapabilityManager(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val sensorManager: SensorManager? = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val batteryManager: BatteryManager? = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    fun evaluateCapabilities(): DeviceCapabilities {
        val hasFlash = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        val hasNfc = packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)
        val hasTelephony = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        val hasTouchscreen = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

        val hasAccel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        val hasMag = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null

        // TV or special devices might report no battery
        val hasBattery = batteryManager?.isCharging != null ||
                context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)) != null

        return DeviceCapabilities(
            hasFlashTorch = hasFlash,
            hasNfc = hasNfc,
            hasAccelerometer = hasAccel,
            hasMagnetometer = hasMag,
            hasTelephony = hasTelephony,
            hasTouchscreen = hasTouchscreen,
            hasBattery = hasBattery
        )
    }
}
