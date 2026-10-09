package com.example.sipatkilatis.capture

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** What automatic screening needs, and how to send the user to fix each one. */
object Permissions {

    fun hasSms(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    /** Android 13+ asks for POST_NOTIFICATIONS; older versions only need notifications not blocked. */
    fun hasNotifications(context: Context) =
        if (Build.VERSION.SDK_INT >= 33)
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** "Notification access" for chat apps is a special setting the user turns on in system Settings. */
    fun hasChatAccess(context: Context) =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    fun chatAccessSettings(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 30)
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, MessageNotificationListener::class.java).flattenToString(),
            )
        else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun appSettings(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    /** Xiaomi / Redmi / POCO (MIUI, HyperOS) stop background apps unless Autostart is allowed. */
    val isXiaomi: Boolean
        get() = Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")
}
