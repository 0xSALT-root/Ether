package com.example.ether

import android.app.Application
import com.example.ether.browser.vpn.VpnManager
import com.example.ether.ui.notification.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class EtherApplication : Application() {

    @Inject
    lateinit var vpnManager: VpnManager

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        notificationHelper.createNotificationChannel()
    }
}
