package com.scamguard.app.screencheck

import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.scamguard.app.alerts.ScreenCheckPrompt

/**
 * Invisible activity that asks Android for screen-sharing consent and, if the user
 * agrees, starts [ScreenCheckService]. On Android 14+ the user can choose to share only
 * the video-call app instead of the whole screen.
 */
class ScreenCheckActivity : ComponentActivity() {

    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenCheckService.start(this, result.resultCode, data)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        ScreenCheckPrompt.cancel(this)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        if (mpm == null) {
            finish()
            return
        }
        consent.launch(mpm.createScreenCaptureIntent())
    }
}
