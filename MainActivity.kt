package com.chargesounds

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private val nameViews = mutableMapOf<ChargeEvent, TextView>()
    private var pending: ChargeEvent? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Charger Sounds"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }

        val master = Switch(this).apply {
            text = "Charger Sounds service"
            textSize = 20f
            isChecked = Prefs.serviceEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, on ->
                Prefs.setServiceEnabled(this@MainActivity, on)
                if (on) startSvc() else stopService(Intent(this@MainActivity, ChargeService::class.java))
            }
        }
        root.addView(master)

        val hint = TextView(this).apply {
            text = "Pick a sound for each event. Turn an event off to silence it."
            setPadding(0, dp(8), 0, dp(4))
        }
        root.addView(hint)

        for (e in ChargeEvent.values()) addEventCard(root, e)

        val battBtn = Button(this).apply {
            text = "Allow background running (recommended)"
            setOnClickListener {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:$packageName")
                        )
                    )
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        }
        root.addView(battBtn, lp().apply { topMargin = dp(16) })

        setContentView(ScrollView(this).apply { addView(root) })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (Prefs.serviceEnabled(this)) startSvc()
    }

    private fun lp() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun startSvc() {
        try {
            startForegroundService(Intent(this, ChargeService::class.java))
        } catch (_: Exception) {
        }
    }

    private fun addEventCard(root: LinearLayout, e: ChargeEvent) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0x14000000)
        }

        card.addView(Switch(this).apply {
            text = e.title
            textSize = 18f
            isChecked = Prefs.enabled(this@MainActivity, e)
            setOnCheckedChangeListener { _, on -> Prefs.setEnabled(this@MainActivity, e, on) }
        })

        val nameView = TextView(this).apply {
            text = "Sound: " + Prefs.soundName(this@MainActivity, e)
            setPadding(0, dp(6), 0, dp(6))
        }
        nameViews[e] = nameView
        card.addView(nameView)

        if (e == ChargeEvent.FULL) {
            card.addView(CheckBox(this).apply {
                text = "Keep repeating until I unplug"
                isChecked = Prefs.loopFull(this@MainActivity)
                setOnCheckedChangeListener { _, on -> Prefs.setLoopFull(this@MainActivity, on) }
            })
        }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val weight = { LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        row.addView(Button(this).apply {
            text = "Choose"
            setOnClickListener { chooseSound(e) }
        }, weight())
        row.addView(Button(this).apply {
            text = "Test"
            setOnClickListener { SoundPlayer.play(this@MainActivity, e, false) }
        }, weight())
        row.addView(Button(this).apply {
            text = "Stop"
            setOnClickListener { SoundPlayer.stop() }
        }, weight())
        card.addView(row)

        root.addView(card, lp().apply { topMargin = dp(12) })
    }

    private fun chooseSound(e: ChargeEvent) {
        pending = e
        AlertDialog.Builder(this)
            .setTitle(e.title)
            .setItems(arrayOf("System sounds", "Pick an audio file", "Use default")) { _, which ->
                when (which) {
                    0 -> {
                        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, e.title)
                            Prefs.soundUri(this@MainActivity, e)?.let {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it)
                            }
                        }
                        startActivityForResult(i, REQ_SYSTEM)
                    }
                    1 -> {
                        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "audio/*"
                            addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            )
                        }
                        startActivityForResult(i, REQ_FILE)
                    }
                    else -> {
                        Prefs.setSound(this, e, null, null)
                        refreshName(e)
                    }
                }
            }
            .show()
    }

    private fun refreshName(e: ChargeEvent) {
        nameViews[e]?.text = "Sound: " + Prefs.soundName(this, e)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val e = pending ?: return
        if (resultCode != RESULT_OK || data == null) return

        when (requestCode) {
            REQ_SYSTEM -> {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                    data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                } else {
                    data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                }
                if (uri != null) {
                    val name = try {
                        RingtoneManager.getRingtone(this, uri)?.getTitle(this)
                    } catch (_: Exception) {
                        null
                    } ?: "System sound"
                    Prefs.setSound(this, e, uri, name)
                    refreshName(e)
                }
            }
            REQ_FILE -> {
                val uri = data.data
                if (uri != null) {
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {
                    }
                    var name = "Custom sound"
                    try {
                        contentResolver.query(uri, null, null, null, null)?.use { c ->
                            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0 && c.moveToFirst()) name = c.getString(idx)
                        }
                    } catch (_: Exception) {
                    }
                    Prefs.setSound(this, e, uri, name)
                    refreshName(e)
                }
            }
        }
    }

    companion object {
        private const val REQ_SYSTEM = 100
        private const val REQ_FILE = 101
    }
}
