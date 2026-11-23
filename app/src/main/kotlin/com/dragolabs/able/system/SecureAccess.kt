package com.dragolabs.able.system

import android.content.Context
import com.dragolabs.able.R
import com.dragolabs.able.actions.ActionRunner
import com.dragolabs.able.core.capability.SelfGrantResult
import com.dragolabs.able.core.data.ableContainer
import com.dragolabs.able.core.model.ActionExecutionStatus

/**
 * WRITE_SECURE_SETTINGS for tiles, widgets and catalog actions. When Shizuku is connected and the
 * permission is missing, Able grants it to itself (SecureSettingsAdapter.ensureWriteSecureSettings),
 * records the grant in the action log and explains it once with a toast.
 */
object SecureAccess {

    const val LOG_ACTION_ID = "system.write_secure_settings.self_grant"

    suspend fun ensure(context: Context): SelfGrantResult {
        val container = context.ableContainer
        val result = runCatching { container.secureSettings.ensureWriteSecureSettings() }.getOrDefault(SelfGrantResult.GRANT_FAILED)
        when (result) {
            SelfGrantResult.GRANTED_VIA_SHIZUKU -> {
                container.actionLog.log(LOG_ACTION_ID, ActionExecutionStatus.SUCCESS, "pm grant via Shizuku; checkSelfPermission = granted")
                container.capabilityStatus.refresh()
                ActionRunner.toast(context, context.getString(R.string.action_self_grant_done, container.secureSettings.writeSecureSettingsRevokeCommand()))
            }
            SelfGrantResult.GRANT_FAILED ->
                container.actionLog.log(LOG_ACTION_ID, ActionExecutionStatus.FAILED, "pm grant via Shizuku; checkSelfPermission = denied")
            SelfGrantResult.ALREADY_GRANTED, SelfGrantResult.UNAVAILABLE -> Unit
        }
        return result
    }
}
