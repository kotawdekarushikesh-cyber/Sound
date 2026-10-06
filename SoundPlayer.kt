package com.chargesounds

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

object SoundPlayer {
    private var player: MediaPlayer? = null

    @Synchronized
    fun play(context: Context, event: ChargeEvent, loop: Boolean = false) {
        stop()
        val uri = Prefs.soundUri(context, event) ?: Prefs.defaultUri(event)
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(
                        if (event == ChargeEvent.FULL) AudioAttributes.USAGE_ALARM
                        else AudioAttributes.USAGE_NOTIFICATION
                    )
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setDataSource(context.applicationContext, uri)
            mp.isLooping = loop
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener {
                it.release()
                synchronized(SoundPlayer) { if (player === it) player = null }
            }
            mp.setOnErrorListener { m, _, _ ->
                m.release()
                synchronized(SoundPlayer) { if (player === m) player = null }
                true
            }
            player = mp
            mp.prepareAsync()
        } catch (e: Exception) {
            player = null
        }
    }

    @Synchronized
    fun stop() {
        player?.let {
            try { it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        player = null
    }
}
