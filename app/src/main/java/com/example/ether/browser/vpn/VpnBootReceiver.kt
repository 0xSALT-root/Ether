package com.example.ether.browser.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class VpnBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var vpnManager: VpnManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.d("Boot completed, triggering VpnManager initialization")
            // Touched to ensure injection and init block execution
            vpnManager.toString()
        }
    }
}
