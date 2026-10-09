package com.example.sipatkilatis.data

import android.content.Context
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
}
