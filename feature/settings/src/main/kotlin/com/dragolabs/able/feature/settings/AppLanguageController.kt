package com.dragolabs.able.feature.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

/**
 * Wraps Android's per-app language API (API 33+) so the in-app selector and the system
 * "App languages" page stay consistent (SET-001). `null` means "System default".
 */
class AppLanguageController(context: Context) {
    private val localeManager: LocaleManager? = context.getSystemService(LocaleManager::class.java)

    /** Language currently applied by Android for Able, or null when it follows the system. */
    fun current(): String? {
        val locales = runCatching { localeManager?.applicationLocales }.getOrNull() ?: return null
        if (locales.isEmpty) return null
        return locales[0].language.takeIf { it in SUPPORTED }
    }

    /** Applies [tag]; Android recreates visible activities with the new configuration. */
    fun apply(tag: String?): Boolean {
        val manager = localeManager ?: return false
        return runCatching {
            manager.applicationLocales = if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        }.isSuccess
    }

    companion object {
        const val ITALIAN = "it"
        const val ENGLISH = "en"
        val SUPPORTED = setOf(ITALIAN, ENGLISH)
    }
}
