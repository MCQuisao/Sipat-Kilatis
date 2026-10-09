package com.example.sipatkilatis.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Per-app language (English / Filipino). AppCompat stores the choice and restarts the activity
 * so all string resources switch at once. Works on Android 8+.
 */
object AppLanguage {
    const val ENGLISH = "en"
    const val FILIPINO = "fil"

    fun current(): String {
        // No app-specific choice yet -> follow the phone's language
        val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            .ifEmpty { LocaleListCompat.getAdjustedDefault().toLanguageTags() }
        return if (tag.startsWith("fil") || tag.startsWith("tl")) FILIPINO else ENGLISH
    }

    fun set(tag: String) {
        if (tag != current()) AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}
