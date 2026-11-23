package com.dragolabs.able.core.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.dragolabs.able.core.model.BatteryHealth
import com.dragolabs.able.core.model.BatteryState
import com.dragolabs.able.core.model.BatteryStatus
import com.dragolabs.able.core.model.PlugType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Battery readings from the sticky ACTION_BATTERY_CHANGED broadcast. The receiver is registered
 * only while the flow is collected (no background sampling, MON-008).
 */
class BatteryStatusRepository(private val context: Context) {

    private val batteryManager = context.getSystemService(BatteryManager::class.java)

    /** True when the device reports a battery at all (TVs usually do not). */
    fun hasBattery(): Boolean {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        return sticky.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)
    }

    fun current(): BatteryStatus {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return sticky?.let(::parse) ?: BatteryStatus.Unavailable
    }

    val status: Flow<BatteryStatus> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(parse(intent))
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        trySend(sticky?.let(::parse) ?: BatteryStatus.Unavailable)
        awaitClose { context.unregisterReceiver(receiver) }
    }.conflate().distinctUntilChanged()

    fun parse(intent: Intent): BatteryStatus {
        val present = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)
        if (!present) return BatteryStatus.Unavailable.copy(timestampEpochMs = System.currentTimeMillis())

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) (level * 100f / scale).toInt() else null

        val state = when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> BatteryState.CHARGING
            BatteryManager.BATTERY_STATUS_FULL -> BatteryState.FULL
            BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryState.DISCHARGING
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryState.NOT_CHARGING
            else -> BatteryState.UNKNOWN
        }
        val plug = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> PlugType.AC
            BatteryManager.BATTERY_PLUGGED_USB -> PlugType.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> PlugType.WIRELESS
            BatteryManager.BATTERY_PLUGGED_DOCK -> PlugType.DOCK
            else -> PlugType.NONE
        }
        val health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
            BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
            BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.FAILURE
            else -> BatteryHealth.UNKNOWN
        }
        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        // EXTRA_CYCLE_COUNT is public from Android 14 (BAT-HLT-001).
        val cycles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1).takeIf { it >= 0 }
        } else {
            null
        }
        val current = batteryManager
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.takeIf { it != Int.MIN_VALUE && it != 0 }
        val chargeRemaining = batteryManager?.computeChargeTimeRemaining()?.takeIf { it > 0 }

        return BatteryStatus(
            levelPercent = percent,
            state = state,
            plugType = plug,
            temperatureCelsius = tempTenths.takeIf { it != Int.MIN_VALUE }?.let { it / 10f },
            voltageMillivolts = voltage.takeIf { it > 0 },
            currentMicroAmperes = current,
            cycleCount = cycles,
            health = health,
            technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() },
            chargeTimeRemainingMs = chargeRemaining,
            timestampEpochMs = System.currentTimeMillis(),
        )
    }
}
