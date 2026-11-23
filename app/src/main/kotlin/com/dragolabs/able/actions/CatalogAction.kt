package com.dragolabs.able.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.dragolabs.able.R
import com.dragolabs.able.core.model.AbleAction
import com.dragolabs.able.core.model.ActionCategory
import com.dragolabs.able.core.model.ActionExecutionType

/** Access an action needs before it can run in place (ADR-023 two-tier model). */
enum class ActionPrivilege(@StringRes val labelRes: Int?) {
    NONE(null),

    /** `WRITE_SECURE_SETTINGS` granted through ADB, or Shizuku. */
    SECURE_SETTINGS(R.string.action_privilege_secure),

    /** "Modify system settings" special app access. */
    WRITE_SETTINGS(R.string.action_privilege_write_settings),

    /** The Able screen lock Accessibility service. */
    ACCESSIBILITY(R.string.action_privilege_accessibility),

    /** Do Not Disturb access, only for part of the action (e.g. silent ringer). */
    NOTIFICATION_POLICY(R.string.action_privilege_notification_policy),
}

/**
 * Live state of an action as read from Android, never assumed.
 *
 * @param active `true`/`false` for toggles, `null` when the action has no on/off state.
 * @param available `false` when the device or Android version cannot run the action.
 */
data class ActionState(
    val active: Boolean?,
    val available: Boolean,
    @DrawableRes val iconRes: Int,
    @StringRes val statusRes: Int? = null,
    val statusText: String? = null,
) {
    fun status(context: Context): String? = statusText ?: statusRes?.let(context::getString)
}

/** The same state shown while Android is still switching to [target] ("Turning on…"). */
fun ActionState.transitioningTo(target: Boolean): ActionState = copy(
    active = target,
    statusRes = if (target) R.string.action_state_turning_on else R.string.action_state_turning_off,
    statusText = null,
)

/** How a surface (tile, widget, in-app row) should handle a tap right now. */
sealed interface ActionRoute {
    /** The action can run without showing a screen. */
    data object RunInPlace : ActionRoute

    /** The action needs a screen: a tool, Android settings, or a consent flow. */
    data class OpenScreen(val intent: Intent) : ActionRoute

    /** Not supported on this device; [infoIntent] may explain why. */
    data class Unsupported(val infoIntent: Intent?) : ActionRoute
}

enum class ActionOutcome { APPLIED, REJECTED, UNVERIFIED, UNSUPPORTED }

/**
 * Executable entry of the Able Action Catalog (ADR-024, CUST-001, CUST-002).
 *
 * Implementations read state back after every change and report [ActionOutcome] honestly.
 */
abstract class CatalogAction(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int,
    val category: ActionCategory,
    val type: ActionExecutionType,
    val privilege: ActionPrivilege = ActionPrivilege.NONE,
    /** Privileged system changes require the device to be unlocked when run from a tile. */
    val requiresUnlock: Boolean = false,
    /** No confirmation toast when the action succeeds (e.g. the screen just locked). */
    val silentOnSuccess: Boolean = false,
) {
    val requiresPrivilege: Boolean get() = privilege != ActionPrivilege.NONE

    /** Broadcasts after which surfaces showing this action re-read its state (e.g. NFC adapter changes). */
    open val stateChangedActions: List<String> = emptyList()

    /** Settings URIs (ContentObserver) after which surfaces re-read this action's state. */
    open fun stateChangedUris(context: Context): List<Uri> = emptyList()

    /**
     * Re-read interval while a tile is listening, for state Android may change behind Able's back
     * (e.g. wireless debugging turning off when Wi-Fi changes). Needed in addition to
     * [stateChangedUris]: Android defers ContentObserver callbacks to cached processes, and a
     * tile service process usually is cached even while its tile is visible. `null` = no polling.
     */
    open val listeningPollMs: Long? = null

    /**
     * State Android reports while a change is still in progress (e.g. NFC STATE_TURNING_ON in an
     * adapter broadcast), or `null` when [intent] reports a settled state.
     */
    open fun transitionalState(context: Context, intent: Intent): ActionState? = null

    open fun isSupported(context: Context): Boolean = true

    open suspend fun readState(context: Context): ActionState =
        ActionState(active = null, available = isSupported(context), iconRes = iconRes)

    abstract fun route(context: Context): ActionRoute

    /** Runs the action in place. Only called when [route] returned [ActionRoute.RunInPlace]. */
    open suspend fun execute(context: Context): ActionOutcome = ActionOutcome.UNSUPPORTED

    /** Serializable descriptor from `:core:model`, used for persistence and export. */
    fun toDescriptor(context: Context): AbleAction = AbleAction(
        id = id,
        category = category,
        titleResKey = context.resources.getResourceEntryName(titleRes),
        descriptionResKey = context.resources.getResourceEntryName(descriptionRes),
        iconKey = context.resources.getResourceEntryName(iconRes),
        requiresPrivilege = requiresPrivilege,
        executionType = type,
    )
}

@StringRes
fun ActionCategory.labelRes(): Int = when (this) {
    ActionCategory.CONNECTIVITY -> R.string.action_category_connectivity
    ActionCategory.BATTERY -> R.string.action_category_battery
    ActionCategory.TOOLS -> R.string.action_category_tools
    ActionCategory.SHORTCUTS -> R.string.action_category_shortcuts
    ActionCategory.SYSTEM -> R.string.action_category_system
}
