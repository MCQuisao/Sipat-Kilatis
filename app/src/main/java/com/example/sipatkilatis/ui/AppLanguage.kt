package com.example.sipatkilatis.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.sipatkilatis.graph

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

    fun set(context: Context, tag: String) {
        // Also saved in our own settings: background alerts have no Activity to read AppCompat's choice from
        val graph = context.graph
        graph.prefs.language = tag
        graph.alerts.createChannels()   // re-name the notification channels in the new language
        if (tag != current()) AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}
