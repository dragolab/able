package com.dragolabs.able.core.data.shortcut

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import com.dragolabs.able.core.model.ShortcutItem
import com.dragolabs.able.core.model.ShortcutTargetType

/** Why a stored shortcut cannot be opened. */
enum class ShortcutLaunchFailure {
    /** Stored data is malformed or fails validation. */
    INVALID,

    /** The stored type is unknown, or the target cannot be expressed as a launch intent. */
    UNSUPPORTED,

    /** The target app is not installed (or has no launcher entry). */
    APP_MISSING,

    /** No activity handles the intent (no viewer/browser, settings page missing). */
    NO_HANDLER,

    /** Android refused the launch (not exported, permission revoked). */
    BLOCKED,
}

sealed interface ShortcutIntentResult {
    data class Ready(val intent: Intent) : ShortcutIntentResult
    data class Failed(val reason: ShortcutLaunchFailure) : ShortcutIntentResult
}

sealed interface ShortcutLaunchOutcome {
    data object Launched : ShortcutLaunchOutcome
    data class Failed(val reason: ShortcutLaunchFailure) : ShortcutLaunchOutcome
}

/**
 * Builds the launch [Intent] for any stored shortcut definition (SHORT-001/SHORT-002), shared by
 * Shortcut Maker (pinned launcher shortcuts, "Try it") and Home. Every intent carries an action,
 * as launcher shortcuts require. Stored data is decoded and validated by [decodeTarget] first;
 * it is never trusted blindly.
 */
class ShortcutIntentFactory(private val context: Context) {
    private val pm: PackageManager get() = context.packageManager

    fun build(item: ShortcutItem): ShortcutIntentResult = build(item.targetType, item.targetData, item.targetPackage)

    /** [type] is null when the stored type name is not recognized. */
    fun build(type: ShortcutTargetType?, data: String, packageName: String?): ShortcutIntentResult {
        if (type == null) return ShortcutIntentResult.Failed(ShortcutLaunchFailure.UNSUPPORTED)
        val target = decodeTarget(type, data, packageName) ?: return ShortcutIntentResult.Failed(ShortcutLaunchFailure.INVALID)
        return build(target)
    }

    fun build(target: ShortcutTarget): ShortcutIntentResult = when (target) {
        is ShortcutTarget.App -> {
            if (!isInstalled(target.packageName)) {
                failed(ShortcutLaunchFailure.APP_MISSING)
            } else {
                pm.getLaunchIntentForPackage(target.packageName)?.component?.let { component ->
                    ready(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component))
                } ?: failed(ShortcutLaunchFailure.APP_MISSING)
            }
        }
        is ShortcutTarget.Activity ->
            if (!isInstalled(target.packageName)) {
                failed(ShortcutLaunchFailure.APP_MISSING)
            } else {
                ready(Intent(Intent.ACTION_MAIN).setComponent(ComponentName(target.packageName, target.className)))
            }
        is ShortcutTarget.SettingsPage -> ready(Intent(target.action))
        is ShortcutTarget.Document -> documentIntent(target)
        is ShortcutTarget.Website -> {
            val uri = Uri.parse(target.url)
            if (uri.scheme?.lowercase() !in WEB_SCHEMES || uri.host.isNullOrBlank()) {
                failed(ShortcutLaunchFailure.INVALID)
            } else {
                ready(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
            }
        }
        is ShortcutTarget.AbleAction ->
            if (isValidAbleRoute(target.route)) ready(ableIntent(target.route)) else failed(ShortcutLaunchFailure.INVALID)
    }

    /** Builds and starts the intent in a new task. Failures are reported, never hidden. */
    fun launch(target: ShortcutTarget): ShortcutLaunchOutcome = start(build(target))

    fun launch(type: ShortcutTargetType?, data: String, packageName: String?): ShortcutLaunchOutcome =
        start(build(type, data, packageName))

    private fun start(result: ShortcutIntentResult): ShortcutLaunchOutcome = when (result) {
        is ShortcutIntentResult.Failed -> ShortcutLaunchOutcome.Failed(result.reason)
        is ShortcutIntentResult.Ready -> try {
            context.startActivity(Intent(result.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ShortcutLaunchOutcome.Launched
        } catch (_: ActivityNotFoundException) {
            ShortcutLaunchOutcome.Failed(ShortcutLaunchFailure.NO_HANDLER)
        } catch (_: SecurityException) {
            // Not exported, or the file permission was revoked.
            ShortcutLaunchOutcome.Failed(ShortcutLaunchFailure.BLOCKED)
        } catch (_: IllegalArgumentException) {
            ShortcutLaunchOutcome.Failed(ShortcutLaunchFailure.INVALID)
        }
    }

    private fun documentIntent(target: ShortcutTarget.Document): ShortcutIntentResult {
        val uri = Uri.parse(target.uri)
        if (uri.scheme != CONTENT_SCHEME) return failed(ShortcutLaunchFailure.INVALID)
        return if (target.isFolder) {
            val docUri = runCatching {
                DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
            }.getOrNull() ?: return failed(ShortcutLaunchFailure.INVALID)
            ready(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        } else {
            val type = target.mimeType ?: runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: "*/*"
            ready(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, type)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }
    }

    /** Opens Able's `MainActivity` with the `route` deep-link extra handled by `:app`. */
    private fun ableIntent(route: String): Intent {
        val main = pm.getLaunchIntentForPackage(context.packageName)?.component
            ?: ComponentName(context.packageName, FALLBACK_MAIN_ACTIVITY)
        return Intent(Intent.ACTION_MAIN)
            .setComponent(main)
            .putExtra(EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    private fun isInstalled(pkg: String): Boolean = try {
        pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun ready(intent: Intent) = ShortcutIntentResult.Ready(intent)
    private fun failed(reason: ShortcutLaunchFailure) = ShortcutIntentResult.Failed(reason)

    companion object {
        /** String extra read by `MainActivity` (see code-structure.md, deep links). */
        const val EXTRA_ROUTE = "route"
        private const val FALLBACK_MAIN_ACTIVITY = "com.dragolabs.able.MainActivity"
        private const val CONTENT_SCHEME = "content"
        private val WEB_SCHEMES = setOf("http", "https")
    }
}
