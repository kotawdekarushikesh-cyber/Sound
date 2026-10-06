package com.chargesounds

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder

class ChargeService : Service() {

    private var fullAnnounced = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    fullAnnounced = false
                    if (Prefs.enabled(c, ChargeEvent.CONNECTED)) {
                        SoundPlayer.play(c, ChargeEvent.CONNECTED)
                    }
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    fullAnnounced = false
                    SoundPlayer.stop()
                    if (Prefs.enabled(c, ChargeEvent.DISCONNECTED)) {
                        SoundPlayer.play(c, ChargeEvent.DISCONNECTED)
                    }
                }
                Intent.ACTION_BATTERY_CHANGED -> checkFull(c, i)
            }
        }
    }

    private fun checkFull(c: Context, i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1

        if (!plugged) {
            fullAnnounced = false
            return
        }
        val isFull = status == BatteryManager.BATTERY_STATUS_FULL || pct >= 100
        if (isFull && !fullAnnounced) {
            fullAnnounced = true
            if (Prefs.enabled(c, ChargeEvent.FULL)) {
                SoundPlayer.play(c, ChargeEvent.FULL, Prefs.loopFull(c))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "charger_sounds"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Charger Sounds", NotificationManager.IMPORTANCE_MIN)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Charger Sounds is running")
            .setContentText("Listening for charger events")
            .setContentIntent(open)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        SoundPlayer.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
