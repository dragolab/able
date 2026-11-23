package com.dragolabs.able.feature.battery.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.dragolabs.able.core.capability.ShizukuAdapter
import com.dragolabs.able.core.common.AbleResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Kernel battery fields read through Shizuku (BAT-HLT-002). Null fields were not exposed. */
data class AdvancedBatteryReading(
    val designCapacityMah: Int?,
    val fullCapacityMah: Int?,
    /** Full/design ratio; a calculation, not a laboratory measurement. */
    val capacityRatioPercent: Float?,
    val cycleCount: Int?,
    val manufactureDate: String?,
    val firstUseDate: String?,
    val chargingPolicy: String?,
) {
    val isEmpty: Boolean
        get() = listOf(designCapacityMah, fullCapacityMah, cycleCount, manufactureDate, firstUseDate, chargingPolicy).all { it == null }
}

sealed interface AdvancedReadResult {
    data class Success(val reading: AdvancedBatteryReading) : AdvancedReadResult
    data object ShizukuUnavailable : AdvancedReadResult
    data object Failed : AdvancedReadResult
}

class AdvancedBatteryHealthReader(private val shizuku: ShizukuAdapter) {

    suspend fun read(): AdvancedReadResult {
        if (!shizuku.isReady()) return AdvancedReadResult.ShizukuUnavailable
        return when (val result = shizuku.executeCommand(COMMAND)) {
            is AbleResult.Success -> AdvancedReadResult.Success(parse(result.data))
            is AbleResult.Failure, AbleResult.Loading -> AdvancedReadResult.Failed
        }
    }

    companion object {
        private val COMMAND = """
            for f in /sys/class/power_supply/*/charge_full_design /sys/class/power_supply/*/charge_full /sys/class/power_supply/*/cycle_count /sys/class/power_supply/*/manufacture_date /sys/class/power_supply/*/first_usage_date /sys/class/power_supply/*/charging_policy; do
              if [ -r "${'$'}f" ]; then printf '%s=%s\n' "${'$'}{f##*/}" "${'$'}(cat "${'$'}f" 2>/dev/null)"; fi
            done
        """.trimIndent()

        fun parse(output: String): AdvancedBatteryReading {
            // First value wins when several power supplies expose the same field.
            val values = mutableMapOf<String, String>()
            output.lineSequence().forEach { line ->
                val separator = line.indexOf('=')
                if (separator > 0) values.putIfAbsent(line.substring(0, separator).trim(), line.substring(separator + 1).trim())
            }
            // Kernel nodes report µAh.
            fun capacity(name: String): Int? = values[name]?.toLongOrNull()?.takeIf { it > 0 }?.let { (it / 1000).toInt() }
            val design = capacity("charge_full_design")
            val full = capacity("charge_full")
            val ratio = if (design != null && full != null && design > 0) full * 100f / design else null
            return AdvancedBatteryReading(
                designCapacityMah = design,
                fullCapacityMah = full,
                capacityRatioPercent = ratio,
                cycleCount = values["cycle_count"]?.toIntOrNull()?.takeIf { it >= 0 },
                manufactureDate = values["manufacture_date"]?.takeIf(String::isNotBlank),
                firstUseDate = values["first_usage_date"]?.takeIf(String::isNotBlank),
                chargingPolicy = values["charging_policy"]?.takeIf(String::isNotBlank),
            )
        }
    }
}

/** System-reported app activity over a window (BAT-REV-001). Observational only. */
data class AppUsageSummary(
    val packageName: String,
    val label: String,
    val foregroundMs: Long,
    val foregroundServiceMs: Long,
)

class AppUsageReviewSource(private val context: Context) {

    /** Top apps by foreground + foreground-service time in the last [windowMs]. */
    suspend fun topApps(windowMs: Long = 24 * 60 * 60 * 1000L, limit: Int = 8): List<AppUsageSummary> = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return@withContext emptyList()
        val end = System.currentTimeMillis()
        val stats = runCatching { manager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, end - windowMs, end) }
            .getOrNull().orEmpty()
        val pm = context.packageManager
        stats.groupBy { it.packageName }
            .map { (pkg, list) ->
                AppUsageSummary(
                    packageName = pkg,
                    label = labelFor(pm, pkg),
                    foregroundMs = list.sumOf { it.totalTimeVisible },
                    foregroundServiceMs = list.sumOf { it.totalTimeForegroundServiceUsed },
                )
            }
            .filter { it.packageName != context.packageName && (it.foregroundMs > 0 || it.foregroundServiceMs > 0) }
            .sortedByDescending { it.foregroundMs + it.foregroundServiceMs }
            .take(limit)
    }

    private fun labelFor(pm: PackageManager, pkg: String): String = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}

/** Resolves protected-app labels at display time (packages are stored, not names). */
class AppLabelResolver(private val context: Context) {
    suspend fun labels(packages: Collection<String>): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        packages.map { pkg ->
            pkg to runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        }.sortedBy { it.second.lowercase() }
    }
}
