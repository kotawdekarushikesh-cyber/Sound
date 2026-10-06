package com.chargesounds

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

enum class ChargeEvent(val key: String, val title: String) {
    CONNECTED("connected", "Charger connected"),
    DISCONNECTED("disconnected", "Charger disconnected"),
    FULL("full", "Battery full")
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("chargesounds", Context.MODE_PRIVATE)

    fun enabled(c: Context, e: ChargeEvent) = sp(c).getBoolean("enabled_${e.key}", true)
    fun setEnabled(c: Context, e: ChargeEvent, v: Boolean) =
        sp(c).edit().putBoolean("enabled_${e.key}", v).apply()

    fun soundUri(c: Context, e: ChargeEvent): Uri? =
        sp(c).getString("uri_${e.key}", null)?.let { Uri.parse(it) }

    fun soundName(c: Context, e: ChargeEvent): String =
        sp(c).getString("name_${e.key}", null) ?: "Default sound"

    fun setSound(c: Context, e: ChargeEvent, uri: Uri?, name: String?) {
        sp(c).edit().apply {
            if (uri == null) {
                remove("uri_${e.key}")
                remove("name_${e.key}")
            } else {
                putString("uri_${e.key}", uri.toString())
                putString("name_${e.key}", name ?: "Custom sound")
            }
        }.apply()
    }

    fun loopFull(c: Context) = sp(c).getBoolean("loop_full", false)
    fun setLoopFull(c: Context, v: Boolean) = sp(c).edit().putBoolean("loop_full", v).apply()

    fun serviceEnabled(c: Context) = sp(c).getBoolean("service_enabled", true)
    fun setServiceEnabled(c: Context, v: Boolean) =
        sp(c).edit().putBoolean("service_enabled", v).apply()

    fun defaultUri(e: ChargeEvent): Uri =
        RingtoneManager.getDefaultUri(
            if (e == ChargeEvent.FULL) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
        )
}
