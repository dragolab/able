package com.dragolabs.able.feature.tools.shortcuts

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.Immutable
import com.dragolabs.able.core.data.shortcut.ShortcutIntentFactory
import com.dragolabs.able.core.data.shortcut.ShortcutIntentResult
import com.dragolabs.able.core.data.shortcut.ShortcutLaunchOutcome
import com.dragolabs.able.core.data.shortcut.ShortcutTarget
import com.dragolabs.able.core.data.shortcut.isValidAbleRoute
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

@Immutable
data class InstalledApp(val packageName: String, val label: String)

/** Why a target cannot be pinned right now (SHORT-002: reject before pinning, with a reason). */
enum class TargetProblem { APP_MISSING, ACTIVITY_MISSING, SETTINGS_MISSING, NO_VIEWER, NO_BROWSER, PERMISSION_LOST, INVALID }

enum class PinRequestResult { REQUESTED, UPDATED, UNSUPPORTED, REJECTED }

/**
 * Builds launch intents for shortcut targets, checks they resolve, and talks to the launcher
 * through [ShortcutManagerCompat] (SHORT-001/SHORT-002). Launcher acceptance is reported only
 * from the pin callback or from reading back pinned shortcuts; never assumed.
 */
class ShortcutLauncher(private val context: Context) {
    private val pm: PackageManager = context.packageManager
    private val intents = ShortcutIntentFactory(context)

    fun isPinSupported(): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    // Catalogs -----------------------------------------------------------------------------

    suspend fun launcherApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            .map { it.activityInfo.packageName }
            .distinct()
            .mapNotNull { pkg ->
                runCatching { pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L)) }.getOrNull()
                    ?.let { InstalledApp(pkg, it.loadLabel(pm).toString()) }
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Curated settings pages that open on this device. */
    suspend fun availableSettings(): List<String> = withContext(Dispatchers.IO) {
        availableSettingsActions(curatedSettingsActions) { action ->
            pm.resolveActivity(Intent(action), PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
                ?.activityInfo
                ?.let { "${it.packageName}/${it.name}" }
        }
    }

    suspend fun targetDrawable(target: ShortcutTarget): Drawable? = withContext(Dispatchers.IO) {
        runCatching {
            when (target) {
                is ShortcutTarget.App -> pm.getApplicationIcon(target.packageName)
                is ShortcutTarget.Activity -> pm.getActivityIcon(ComponentName(target.packageName, target.className))
                else -> null
            }
        }.getOrNull()
    }

    /** Default label suggestion for a target that carries a system label (apps, activities). */
    suspend fun systemLabel(target: ShortcutTarget): String? = withContext(Dispatchers.IO) {
        runCatching {
            when (target) {
                is ShortcutTarget.App -> pm.getApplicationInfo(target.packageName, PackageManager.ApplicationInfoFlags.of(0L)).loadLabel(pm).toString()
                is ShortcutTarget.Activity -> pm.getActivityInfo(
                    ComponentName(target.packageName, target.className),
                    PackageManager.ComponentInfoFlags.of(0L),
                ).loadLabel(pm).toString()
                is ShortcutTarget.Document -> documentName(Uri.parse(target.uri))
                else -> null
            }
        }.getOrNull()
    }

    private fun documentName(uri: Uri): String? {
        val docUri = if (DocumentsContract.isTreeUri(uri)) {
            DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        } else {
            uri
        }
        return context.contentResolver.query(docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }

    // Intents --------------------------------------------------------------------------------

    /**
     * The intent stored in the launcher shortcut, built by the shared [ShortcutIntentFactory]
     * (also used by Home). Null when the target cannot be expressed as an intent right now.
     */
    fun intentFor(target: ShortcutTarget): Intent? = (intents.build(target) as? ShortcutIntentResult.Ready)?.intent

    /** Null when the target can be opened; otherwise the reason it cannot. */
    suspend fun problemWith(target: ShortcutTarget): TargetProblem? = withContext(Dispatchers.IO) {
        when (target) {
            is ShortcutTarget.App -> if (intentFor(target) == null) TargetProblem.APP_MISSING else null
            is ShortcutTarget.Activity -> {
                val info = runCatching {
                    pm.getActivityInfo(ComponentName(target.packageName, target.className), PackageManager.ComponentInfoFlags.of(0L))
                }.getOrNull()
                when {
                    info == null -> TargetProblem.ACTIVITY_MISSING
                    !info.exported || !info.enabled -> TargetProblem.ACTIVITY_MISSING
                    info.permission != null && context.checkSelfPermission(info.permission!!) != PackageManager.PERMISSION_GRANTED -> TargetProblem.ACTIVITY_MISSING
                    else -> null
                }
            }
            is ShortcutTarget.SettingsPage -> if (resolves(Intent(target.action))) null else TargetProblem.SETTINGS_MISSING
            is ShortcutTarget.Document -> {
                val uri = Uri.parse(target.uri)
                val granted = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
                val intent = intentFor(target)
                when {
                    !granted -> TargetProblem.PERMISSION_LOST
                    intent == null -> TargetProblem.INVALID
                    !resolves(intent) -> TargetProblem.NO_VIEWER
                    else -> null
                }
            }
            is ShortcutTarget.Website -> if (resolves(intentFor(target)!!)) null else TargetProblem.NO_BROWSER
            is ShortcutTarget.AbleAction -> if (isValidAbleRoute(target.route)) null else TargetProblem.INVALID
        }
    }

    private fun resolves(intent: Intent): Boolean =
        pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())) != null

    /** Takes a persistable read grant for a SAF document/tree so the shortcut keeps working. */
    fun persistReadPermission(uri: Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    }.getOrDefault(false)

    /** Opens the target from inside Able ("Try it"). */
    fun open(target: ShortcutTarget): Boolean = intents.launch(target) == ShortcutLaunchOutcome.Launched

    // Pinning --------------------------------------------------------------------------------

    /** IDs of Able shortcuts the launcher currently reports as pinned (read-back). */
    fun pinnedIds(): Set<String> = runCatching {
        ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED).map { it.id }.toSet()
    }.getOrDefault(emptySet())

    /**
     * Sends a pin request. If the launcher already shows this shortcut, its label/icon are
     * updated in place instead. REQUESTED only means Android forwarded the request; the
     * launcher still asks the user and may decline.
     */
    fun requestPin(definitionId: String, target: ShortcutTarget, shortLabel: String, longLabel: String, icon: Bitmap): PinRequestResult {
        if (!isPinSupported()) return PinRequestResult.UNSUPPORTED
        val intent = intentFor(target) ?: return PinRequestResult.REJECTED
        val shortcutId = launcherShortcutId(definitionId)
        val info = ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel(shortLabel.take(MAX_SHORT_LABEL))
            .setLongLabel(longLabel.take(MAX_LONG_LABEL))
            .setIcon(IconCompat.createWithAdaptiveBitmap(icon))
            .setIntent(intent)
            .build()
        return try {
            if (shortcutId in pinnedIds()) {
                if (ShortcutManagerCompat.updateShortcuts(context, listOf(info))) PinRequestResult.UPDATED else PinRequestResult.REJECTED
            } else {
                val callback = PendingIntent.getBroadcast(
                    context,
                    shortcutId.hashCode(),
                    Intent(ACTION_PINNED).setPackage(context.packageName).putExtra(EXTRA_SHORTCUT_ID, shortcutId),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                if (ShortcutManagerCompat.requestPinShortcut(context, info, callback.intentSender)) PinRequestResult.REQUESTED else PinRequestResult.REJECTED
            }
        } catch (_: IllegalArgumentException) {
            PinRequestResult.REJECTED
        } catch (_: IllegalStateException) {
            PinRequestResult.REJECTED
        }
    }

    /** Emits launcher shortcut IDs whose pin request the launcher reported as accepted. */
    fun pinCallbacks(): Flow<String> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                intent.getStringExtra(EXTRA_SHORTCUT_ID)?.let { trySend(it) }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_PINNED), ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }

    companion object {
        const val EXTRA_ROUTE = ShortcutIntentFactory.EXTRA_ROUTE
        private const val ACTION_PINNED = "com.dragolabs.able.feature.tools.SHORTCUT_PINNED"
        private const val EXTRA_SHORTCUT_ID = "shortcut_id"
        const val MAX_SHORT_LABEL = 25
        const val MAX_LONG_LABEL = 60
    }
}
