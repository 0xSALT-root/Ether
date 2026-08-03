package com.example.ether.ui.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannel() {
        // NotificationChannel is available from API 26 (Android 8.0), our minSdk is 26.
        val name = "Ether Notifications"
        val descriptionText = "App updates and browser notifications"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun showVpnDisconnectNotification() {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("VPN Smart Connect")
            .setContentText("You've left a protected site. Disconnect VPN?")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(VPN_DISCONNECT_NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "ether_channel"
        private const val VPN_DISCONNECT_NOTIFICATION_ID = 1001
    }
}
