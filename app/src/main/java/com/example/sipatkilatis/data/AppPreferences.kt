package com.example.sipatkilatis.data

import android.content.Context
import com.example.sipatkilatis.model.Appearance
import com.example.sipatkilatis.model.Sensitivity

/** Small settings stored on the device (SharedPreferences). Nothing here is ever uploaded. */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(value) = prefs.edit().putBoolean("onboarding_done", value).apply()

    var protectionOn: Boolean
        get() = prefs.getBoolean("protection_on", true)
        set(value) = prefs.edit().putBoolean("protection_on", value).apply()

    var sensitivity: Sensitivity
        get() = Sensitivity.valueOf(prefs.getString("sensitivity", Sensitivity.NORMAL.name)!!)
        set(value) = prefs.edit().putString("sensitivity", value.name).apply()

    var appearance: Appearance
        get() = runCatching { Appearance.valueOf(prefs.getString("appearance", null)!!) }.getOrDefault(Appearance.SYSTEM)
        set(value) = prefs.edit().putString("appearance", value.name).apply()

    /** Use the local LLM (if installed) to explain SUSPICIOUS / SCAM results. */
    var aiExplanations: Boolean
        get() = prefs.getBoolean("ai_explanations", true)
        set(value) = prefs.edit().putBoolean("ai_explanations", value).apply()

    /** "en" / "fil", or null = follow the phone. Kept here too so background alerts use the right language. */
    var language: String?
        get() = prefs.getString("language", null)
        set(value) = prefs.edit().putString("language", value).apply()
}
