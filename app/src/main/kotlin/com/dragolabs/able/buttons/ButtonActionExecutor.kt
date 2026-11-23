package com.dragolabs.able.buttons

import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import com.dragolabs.able.R
import com.dragolabs.able.actions.ActionCatalog
import com.dragolabs.able.actions.ActionRoute
import com.dragolabs.able.actions.ActionRunner
import com.dragolabs.able.actions.ReportMode
import com.dragolabs.able.core.data.ableContainer
import com.dragolabs.able.feature.tools.buttons.ButtonTarget
import kotlinx.coroutines.launch

/**
 * Runs the target of a recognised button gesture (BTN-002), reusing the Action Catalog and
 * [ActionRunner] so outcomes are read back, logged and reported like tiles and widgets.
 * Activities are started from the Accessibility service, which Android allows while it is bound.
 */
internal class ButtonActionExecutor(private val context: Context) {
    private val keyguard = context.getSystemService(KeyguardManager::class.java)

    private val locked: Boolean get() = keyguard?.isKeyguardLocked == true

    fun run(target: ButtonTarget) {
        when (target) {
            is ButtonTarget.Catalog -> runCatalog(target.actionId)
            ButtonTarget.Camera -> start(
                Intent(if (locked) MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE else MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            )
            is ButtonTarget.App -> {
                val intent = runCatching { context.packageManager.getLaunchIntentForPackage(target.packageName) }.getOrNull()
                if (intent == null) toast(R.string.buttons_result_app_missing) else start(intent)
            }
            is ButtonTarget.Activity -> start(
                Intent(Intent.ACTION_MAIN).setComponent(ComponentName(target.packageName, target.className)),
            )
        }
    }

    private fun runCatalog(actionId: String) {
        val action = ActionCatalog.find(actionId)
        if (action == null) {
            toast(R.string.buttons_result_action_missing)
            return
        }
        // Same rule as the Quick Settings tiles: privileged system changes need an unlocked device.
        if (action.requiresUnlock && locked) {
            toast(R.string.buttons_result_unlock_first)
            return
        }
        when (val route = runCatching { action.route(context) }.getOrNull()) {
            ActionRoute.RunInPlace -> context.ableContainer.applicationScope.launch {
                ActionRunner.execute(context, action, ReportMode.FAILURES_ONLY)
            }
            is ActionRoute.OpenScreen -> start(route.intent)
            is ActionRoute.Unsupported, null -> toast(R.string.buttons_result_unsupported)
        }
    }

    private fun start(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            toast(R.string.buttons_result_no_app)
        } catch (_: SecurityException) {
            toast(R.string.buttons_result_denied)
        } catch (_: RuntimeException) {
            toast(R.string.buttons_result_failed)
        }
    }

    private fun toast(res: Int) = Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
}
