package com.dragolabs.able.core.data.shortcut

import com.dragolabs.able.core.model.ShortcutItem
import com.dragolabs.able.core.model.ShortcutTargetType
import com.dragolabs.able.core.model.ToolId
import java.net.URI

// Pure (JVM-testable) codec for stored shortcut definitions (SHORT-001..SHORT-003), shared by
// Shortcut Maker and Home.
//
// The Room schema (`shortcuts` table) has no dedicated columns for some targets, so they are
// stored in existing fields:
// - Applications: targetData = targetPackage = package name.
// - Activities: targetPackage = package, targetData = class name (relative ".Foo" and the legacy
//   flattened "pkg/cls" form are accepted on read).
// - Android settings pages: targetType ACTIVITY, targetData "settings:<action>", targetPackage null.
// - Files/folders: targetData = content:// URI; targetPackage = "folder" (SAF tree URI) or
//   "mime:<type>" (or null).
// - Websites: targetData = http(s) URL.
// - Able actions: targetData = `MainActivity` route (`home`, `battery`, `apps`, `tools`, `tool/<key>`).

/** What a shortcut opens. */
sealed interface ShortcutTarget {
    data class App(val packageName: String) : ShortcutTarget
    data class Activity(val packageName: String, val className: String) : ShortcutTarget
    data class SettingsPage(val action: String) : ShortcutTarget
    data class Document(val uri: String, val mimeType: String?, val isFolder: Boolean) : ShortcutTarget
    data class Website(val url: String) : ShortcutTarget

    /** An Able destination opened through the `route` deep link (for example `tool/torch`). */
    data class AbleAction(val route: String) : ShortcutTarget
}

const val SETTINGS_PREFIX = "settings:"

/** FILE_OR_FOLDER keeps the content:// URI in targetData; targetPackage carries "folder" or "mime:<type>". */
const val DOC_FOLDER = "folder"
const val DOC_MIME_PREFIX = "mime:"

/** Encoded form stored in [ShortcutItem]: type, data and package. */
data class EncodedTarget(val type: ShortcutTargetType, val data: String, val packageName: String?)

fun ShortcutTarget.encode(): EncodedTarget = when (this) {
    is ShortcutTarget.App -> EncodedTarget(ShortcutTargetType.APPLICATION, packageName, packageName)
    is ShortcutTarget.Activity -> EncodedTarget(ShortcutTargetType.ACTIVITY, className, packageName)
    is ShortcutTarget.SettingsPage -> EncodedTarget(ShortcutTargetType.ACTIVITY, SETTINGS_PREFIX + action, null)
    is ShortcutTarget.Document -> EncodedTarget(
        ShortcutTargetType.FILE_OR_FOLDER,
        uri,
        if (isFolder) DOC_FOLDER else mimeType?.let { DOC_MIME_PREFIX + it },
    )
    is ShortcutTarget.Website -> EncodedTarget(ShortcutTargetType.URL, url, null)
    is ShortcutTarget.AbleAction -> EncodedTarget(ShortcutTargetType.ABLE_ACTION, route, null)
}

/** Returns null for malformed or unsafe stored data, which the UI shows as an unavailable target. */
fun decodeTarget(type: ShortcutTargetType, data: String, packageName: String?): ShortcutTarget? = when (type) {
    ShortcutTargetType.APPLICATION ->
        (packageName?.takeIf { it.isNotBlank() } ?: data).trim().takeIf(::isValidPackageName)?.let { ShortcutTarget.App(it) }
    ShortcutTargetType.ACTIVITY -> decodeActivity(data.trim(), packageName?.trim())
    ShortcutTargetType.FILE_OR_FOLDER -> data.takeIf(::isValidContentUri)?.let {
        ShortcutTarget.Document(
            uri = it,
            mimeType = packageName?.takeIf { p -> p.startsWith(DOC_MIME_PREFIX) }?.removePrefix(DOC_MIME_PREFIX)?.ifBlank { null },
            isFolder = packageName == DOC_FOLDER,
        )
    }
    ShortcutTargetType.URL -> normalizeWebUrl(data)?.let { ShortcutTarget.Website(it) }
    ShortcutTargetType.ABLE_ACTION -> {
        val route = data.trim()
        when {
            isValidAbleRoute(route) -> ShortcutTarget.AbleAction(route)
            ToolId.fromKey(route) != null -> ShortcutTarget.AbleAction("tool/$route")
            else -> null
        }
    }
}

/** Tolerates an unknown stored type name (returns null). */
fun decodeTarget(typeName: String, data: String, packageName: String?): ShortcutTarget? =
    ShortcutTargetType.entries.firstOrNull { it.name == typeName }?.let { decodeTarget(it, data, packageName) }

fun ShortcutItem.target(): ShortcutTarget? = decodeTarget(targetType, targetData, targetPackage)

private fun decodeActivity(data: String, packageName: String?): ShortcutTarget? = when {
    data.startsWith(SETTINGS_PREFIX) ->
        data.removePrefix(SETTINGS_PREFIX).takeIf(::isValidSettingsAction)?.let { ShortcutTarget.SettingsPage(it) }
    // Current format: targetPackage + class name (relative ".Foo" allowed).
    !packageName.isNullOrBlank() && data.isNotBlank() && !data.contains('/') -> activityOrNull(packageName, data)
    // Tolerated legacy format: flattened "pkg/cls".
    data.contains('/') -> activityOrNull(data.substringBefore('/'), data.substringAfter('/'))
    else -> null
}

private fun activityOrNull(pkg: String, cls: String): ShortcutTarget.Activity? {
    val fullClass = if (cls.startsWith(".")) pkg + cls else cls
    return if (isValidPackageName(pkg) && isValidClassName(fullClass)) ShortcutTarget.Activity(pkg, fullClass) else null
}

// ---------------------------------------------------------------------------------------------
// Validation
// ---------------------------------------------------------------------------------------------

private val PACKAGE_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
private val CLASS_REGEX = Regex("^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*$")
private val SETTINGS_ACTION_REGEX = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")
private val CONTENT_URI_REGEX = Regex("^content://[^/\\s]+(/\\S*)?$")

/** Android package name: at least two dot-separated segments, each starting with a letter. */
fun isValidPackageName(name: String): Boolean = PACKAGE_REGEX.matches(name)

/** Fully qualified Java class name (nested `$` classes allowed). */
fun isValidClassName(name: String): Boolean = CLASS_REGEX.matches(name)

/** Intent action string such as `android.settings.WIFI_SETTINGS`. */
fun isValidSettingsAction(action: String): Boolean = SETTINGS_ACTION_REGEX.matches(action)

/** Only `content://` URIs with an authority are accepted for files and folders. */
fun isValidContentUri(uri: String): Boolean = CONTENT_URI_REGEX.matches(uri)

/**
 * Validates a user-entered website and returns a normalized http(s) URL, or null.
 * A missing scheme defaults to https. Other schemes (javascript:, file:, intent:...) are rejected.
 */
fun normalizeWebUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
    val hasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(trimmed)
    if (!hasScheme && trimmed.contains(':') && !Regex("^[^/:]+:\\d+(/.*)?$").matches(trimmed)) return null
    val candidate = if (hasScheme) trimmed else "https://$trimmed"
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host?.lowercase() ?: return null
    if (!isValidWebHost(host)) return null
    if (uri.port != -1 && uri.port !in 1..65535) return null
    return candidate.replaceFirst(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://"), "$scheme://")
}

private fun isValidWebHost(host: String): Boolean {
    if (host == "localhost") return true
    // IPv4
    if (Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host)) return host.split('.').all { it.toInt() in 0..255 }
    // IPv6 literal (java.net.URI keeps the brackets).
    if (host.startsWith("[") && host.endsWith("]")) return true
    if (host.length > 253 || !host.contains('.')) return false
    val labels = host.split('.')
    if (labels.any { it.isEmpty() || it.length > 63 || it.startsWith("-") || it.endsWith("-") }) return false
    if (!labels.all { label -> label.all { it.isLetterOrDigit() || it == '-' } }) return false
    val tld = labels.last()
    return tld.length >= 2 && !tld.all { it.isDigit() }
}

// ---------------------------------------------------------------------------------------------
// Able actions
// ---------------------------------------------------------------------------------------------

/** Top-level Able destinations accepted by `MainActivity`'s `route` extra. */
val ableTopLevelRoutes: List<String> = listOf("home", "battery", "apps", "tools")

const val ABLE_TOOL_ROUTE_PREFIX = "tool/"

fun ableToolRoute(tool: ToolId): String = ABLE_TOOL_ROUTE_PREFIX + tool.key

fun isValidAbleRoute(route: String): Boolean =
    route in ableTopLevelRoutes || (route.startsWith(ABLE_TOOL_ROUTE_PREFIX) && ToolId.fromKey(route.removePrefix(ABLE_TOOL_ROUTE_PREFIX)) != null)

/** The tool an Able route opens, or null for top-level routes and invalid input. */
fun toolForAbleRoute(route: String): ToolId? =
    route.takeIf { it.startsWith(ABLE_TOOL_ROUTE_PREFIX) }?.let { ToolId.fromKey(it.removePrefix(ABLE_TOOL_ROUTE_PREFIX)) }
