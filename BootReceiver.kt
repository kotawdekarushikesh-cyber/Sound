package com.chargesounds

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.serviceEnabled(context)) {
            try {
                context.startForegroundService(Intent(context, ChargeService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
