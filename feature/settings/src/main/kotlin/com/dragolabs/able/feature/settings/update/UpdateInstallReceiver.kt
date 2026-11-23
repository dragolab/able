package com.dragolabs.able.feature.settings.update

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.dragolabs.able.core.data.ableContainer

/**
 * Receives the PackageInstaller session status (UPD-001). Not exported: only the explicit
 * PendingIntent created by [AppUpdater] reaches it.
 *
 * On STATUS_PENDING_USER_ACTION it opens Android's confirmation dialog (Able is in the foreground
 * because the user just tapped "Download and install"); if that fails, Settings offers a
 * "Continue installation" button that launches the same intent from the activity.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val confirm = if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            null
        }
        AppUpdater.get(context.ableContainer).onInstallStatus(status, message, confirm)
        if (confirm != null) {
            try {
                context.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                // Settings shows "Continue installation".
            } catch (_: SecurityException) {
                // Background activity start refused; Settings shows "Continue installation".
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.dragolabs.able.action.UPDATE_INSTALL_STATUS"
    }
}
