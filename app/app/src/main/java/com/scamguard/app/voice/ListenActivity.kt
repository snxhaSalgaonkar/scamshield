package com.scamguard.app.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.scamguard.app.util.Permissions

/**
 * Invisible activity behind the prompt's buttons. Being visible when the Allow tap lands is
 * what lets Android start a microphone service; it asks for the microphone permission if
 * needed, starts [CallListenService] and closes.
 */
class ListenActivity : ComponentActivity() {
    enum class Mode { CALL, TEST, DECLINE }

    private val mic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening()
        finish()
    }

    private val mode get() = Mode.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_MODE) } ?: Mode.DECLINE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        ListenPrompt.cancel(this)
        when {
            mode == Mode.DECLINE -> finish()
            Permissions.isGranted(this, Manifest.permission.RECORD_AUDIO) -> {
                startListening()
                finish()
            }
            else -> mic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /** Starts listening and opens the Live call check screen, so the words are visible at once. */
    private fun startListening() {
        CallListenService.start(this, test = mode == Mode.TEST)
        startActivity(CallListenService.openScreenIntent(this))
    }

    companion object {
        private const val EXTRA_MODE = "mode"

        fun intent(context: Context, mode: Mode): Intent = Intent(context, ListenActivity::class.java)
            .putExtra(EXTRA_MODE, mode.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
