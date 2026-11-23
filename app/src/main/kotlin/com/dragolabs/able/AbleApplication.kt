package com.dragolabs.able

import android.app.Application
import android.content.Context
import com.dragolabs.able.core.data.AbleContainer
import com.dragolabs.able.core.data.AbleContainerOwner
import com.dragolabs.able.core.data.DefaultAbleContainer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.acra.ACRA
import org.acra.ReportField
import org.acra.config.dialog
import org.acra.config.mailSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra

class AbleApplication : Application(), AbleContainerOwner {

    override val container: AbleContainer by lazy {
        DefaultAbleContainer(this, AccessibilityScreenLockController(this))
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        initCrashReports()
    }

    override fun onCreate() {
        super.onCreate()
        // ACRA starts a separate ":acra" process to send a report. It must not build the
        // container there (DataStore is single-process); only the main process syncs the switch.
        if (ACRA.isACRASenderServiceProcess()) return
        container.applicationScope.launch {
            container.preferences.userPreferencesFlow
                .map { it.telemetryOptIn }
                .distinctUntilChanged()
                .collect(::setCrashReportsEnabled)
        }
    }

    override fun onTerminate() {
        container.shizuku.destroy()
        super.onTerminate()
    }

    /**
     * User-reviewed crash reports (PRIV-001, ADR-015): when Able crashes, ACRA shows a dialog and,
     * only if the user taps Send, opens their email app with the report attached for review.
     * Nothing is sent automatically, no third-party server is involved, and no analytics are
     * collected. Disabled unless the user enabled crash reports in Settings → Privacy.
     */
    private fun initCrashReports() {
        val acraPrefs = getSharedPreferences(ACRA_PREFS, MODE_PRIVATE)
        if (!acraPrefs.contains(ACRA.PREF_ENABLE_ACRA)) {
            // ACRA is enabled by default when the key is missing; Able's default is off.
            acraPrefs.edit().putBoolean(ACRA.PREF_ENABLE_ACRA, false).putBoolean(ACRA.PREF_DISABLE_ACRA, true).commit()
        }
        initAcra {
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.KEY_VALUE_LIST
            sharedPreferencesName = ACRA_PREFS
            // Minimal fields only: no logcat, device ID, user email, installed apps or settings.
            reportContent = listOf(
                ReportField.REPORT_ID,
                ReportField.USER_CRASH_DATE,
                ReportField.APP_VERSION_CODE,
                ReportField.APP_VERSION_NAME,
                ReportField.ANDROID_VERSION,
                ReportField.BRAND,
                ReportField.PHONE_MODEL,
                ReportField.BUILD,
                ReportField.STACK_TRACE,
            )
            dialog {
                title = getString(R.string.crash_dialog_title)
                text = getString(R.string.crash_dialog_text)
                positiveButtonText = getString(R.string.crash_dialog_send)
                negativeButtonText = getString(R.string.crash_dialog_discard)
                resIcon = null
            }
            mailSender {
                mailTo = CRASH_REPORT_EMAIL
                reportAsFile = true
                reportFileName = "able-crash-report.txt"
                subject = getString(R.string.crash_mail_subject)
                body = getString(R.string.crash_mail_body)
            }
        }
    }

    private fun setCrashReportsEnabled(enabled: Boolean) {
        getSharedPreferences(ACRA_PREFS, MODE_PRIVATE).edit()
            .putBoolean(ACRA.PREF_ENABLE_ACRA, enabled)
            .putBoolean(ACRA.PREF_DISABLE_ACRA, !enabled)
            .apply()
        runCatching { ACRA.errorReporter.setEnabled(enabled) }
    }

    private companion object {
        const val ACRA_PREFS = "able_crash_reports"
        const val CRASH_REPORT_EMAIL = "info.dragolabs@gmail.com"
    }
}
