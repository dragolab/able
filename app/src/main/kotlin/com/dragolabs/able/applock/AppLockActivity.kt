package com.dragolabs.able.applock

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.dragolabs.able.R

/**
 * Transparent activity that only hosts Android's system `BiometricPrompt` for a locked app
 * (APPLOCK-001). Fingerprint, face (Class 3 / strong) or the device PIN, pattern or password
 * are handled entirely by the system; Able never sees or stores a credential.
 *
 * `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` is a supported combination on Android 11+ (API 30+),
 * so it is always valid with minSdk 33 (androidx.biometric `BiometricManager.Authenticators`
 * documentation, checked 2026-11-22). With `DEVICE_CREDENTIAL` no negative button may be set;
 * the user cancels with Back or by tapping outside, which sends them home.
 *
 * Declared not exported, excluded from recents, `noHistory`, with its own task affinity and
 * not shown over the keyguard, so it cannot be reused by other apps or linger in recents.
 */
class AppLockActivity : FragmentActivity() {
    private var targetPackage: String? = null
    private var prompt: BiometricPrompt? = null
    private var resolved = false

    /** Incremented per prompt so callbacks of a cancelled prompt are ignored. */
    private var session = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        start(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val next = intent.getStringExtra(EXTRA_PACKAGE)
        if (next != null && next != targetPackage) {
            // A different locked app came up while a prompt was open: restart for that app.
            targetPackage?.let { AppLockBridge.controller?.onCancelled(it, goHome = false) }
            session++
            prompt?.cancelAuthentication()
            resolved = false
            start(intent)
        }
    }

    private fun start(intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        val controller = AppLockBridge.controller
        if (pkg == null || controller == null) {
            // Service gone or malformed request: nothing to protect any more.
            finishQuietly()
            return
        }
        targetPackage = pkg
        if (controller.isUnlocked(pkg)) {
            resolve(success = true)
            return
        }
        val mySession = ++session
        val authenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            // Fail closed: without a usable screen lock the app is not opened.
            Toast.makeText(this, R.string.applock_prompt_unavailable, Toast.LENGTH_LONG).show()
            resolve(success = false)
            return
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.applock_prompt_title, appLabel(pkg)))
            .setSubtitle(getString(R.string.applock_prompt_subtitle))
            .setAllowedAuthenticators(authenticators)
            .setConfirmationRequired(false)
            .build()
        prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (mySession == session) resolve(success = true)
                }

                // Covers user cancel, lockout, timeout and "no credential": all keep the app closed.
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (mySession == session) resolve(success = false)
                }

                // A single failed attempt: the system prompt stays open for another try.
                override fun onAuthenticationFailed() = Unit
            },
        ).also { it.authenticate(info) }
    }

    private fun appLabel(pkg: String): CharSequence = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L)))
    }.getOrDefault(pkg)

    private fun resolve(success: Boolean) {
        if (resolved) return
        resolved = true
        val pkg = targetPackage
        val controller = AppLockBridge.controller
        if (pkg != null && controller != null) {
            if (success) controller.onAuthenticated(pkg) else controller.onCancelled(pkg, goHome = true)
        }
        finishQuietly()
    }

    override fun onDestroy() {
        // Closed without a result (screen off, the user switched away): keep the app locked.
        if (!resolved && !isChangingConfigurations) {
            resolved = true
            targetPackage?.let { AppLockBridge.controller?.onCancelled(it, goHome = false) }
        }
        super.onDestroy()
    }

    private fun finishQuietly() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_PACKAGE = "com.dragolabs.able.applock.extra.PACKAGE"
    }
}
