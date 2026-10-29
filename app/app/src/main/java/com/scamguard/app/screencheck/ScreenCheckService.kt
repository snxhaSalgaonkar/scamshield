package com.scamguard.app.screencheck

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.scamguard.app.BuildConfig
import com.scamguard.app.R
import com.scamguard.app.alerts.NotificationChannels
import com.scamguard.app.appContainer
import com.scamguard.app.bus.ScreenCategory
import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.collectors.AuthorityText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The on-demand screen check. While a video call window is open it captures one
 * downscaled frame every few seconds (with the user's screen-sharing consent), reads the
 * text on it with on-device ML Kit OCR, and emits [SignalEvent.ScreenEvidence] when
 * official-looking text appears (police, warrant, court, "verification account").
 *
 * Frames and text live only in memory for one check and are never stored or sent; only
 * the matched categories reach the risk engine. Stops when the call ends, when the user
 * taps Stop or ends sharing, or after [MAX_MS]. If the video app hides its screen from
 * capture, frames come back blank and the notification says so.
 */
class ScreenCheckService : LifecycleService() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var job: Job? = null
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_DATA, Intent::class.java) }
        if (projection != null) return START_NOT_STICKY
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Android requires the foreground service to be running before the projection is used.
        startInForeground(R.string.screen_check_running)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val p = try {
            mpm?.getMediaProjection(intent.getIntExtra(EXTRA_RESULT_CODE, 0), data)
        } catch (_: RuntimeException) {
            null
        }
        if (p == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() = stopSelf()
        }, Handler(Looper.getMainLooper()))
        projection = p

        val metrics = resources.displayMetrics
        val scale = minOf(1f, CAPTURE_WIDTH.toFloat() / metrics.widthPixels)
        val w = (metrics.widthPixels * scale).toInt().coerceAtLeast(1)
        val h = (metrics.heightPixels * scale).toInt().coerceAtLeast(1)
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = r
        display = p.createVirtualDisplay(
            "scam-check", w, h, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null,
        )
        job = lifecycleScope.launch { checkLoop() }
        return START_NOT_STICKY
    }

    private suspend fun checkLoop() {
        val c = appContainer
        val started = System.currentTimeMillis()
        var reported = emptySet<ScreenCategory>()
        var blankFrames = 0
        var sawWindow = false
        delay(FIRST_FRAME_MS)
        while (lifecycleScope.isActive) {
            val now = System.currentTimeMillis()
            val live = c.sessionManager.live.value
            if (live != null) sawWindow = true
            val callOver = if (sawWindow) live == null || live.window.callEnded else now - started > NO_CALL_MS
            if (callOver || now - started > MAX_MS) break

            val frame = latestFrame()
            if (frame != null) {
                if (isBlank(frame)) {
                    blankFrames++
                    if (blankFrames == BLANK_LIMIT) startInForeground(R.string.screen_check_blocked)
                } else {
                    if (blankFrames >= BLANK_LIMIT) startInForeground(R.string.screen_check_running)
                    blankFrames = 0
                    val text = recognize(frame)
                    val found = AuthorityText.categories(text)
                    if (BuildConfig.DEBUG) Log.d(TAG, "frame ok, ${text.length} chars, categories=$found")
                    if ((found - reported).isNotEmpty()) {
                        reported = reported + found
                        c.eventBus.emit(SignalEvent.ScreenEvidence(now, found))
                    }
                }
                frame.recycle()
            }
            delay(INTERVAL_MS)
        }
        stopSelf()
    }

    /** The newest captured frame as a bitmap, or null if none arrived yet. */
    private fun latestFrame(): Bitmap? {
        val image = try { reader?.acquireLatestImage() } catch (_: IllegalStateException) { null } ?: return null
        return image.use {
            val plane = it.planes[0]
            val rowPixels = plane.rowStride / plane.pixelStride
            val padded = Bitmap.createBitmap(rowPixels, it.height, Bitmap.Config.ARGB_8888)
            padded.copyPixelsFromBuffer(plane.buffer)
            if (rowPixels == it.width) padded
            else Bitmap.createBitmap(padded, 0, 0, it.width, it.height).also { padded.recycle() }
        }
    }

    /** True when a grid of sample pixels is all near-black (a screen hidden from capture). */
    private fun isBlank(b: Bitmap): Boolean {
        for (y in 1 until GRID) for (x in 1 until GRID) {
            val px = b.getPixel(b.width * x / GRID, b.height * y / GRID)
            if (Color.red(px) + Color.green(px) + Color.blue(px) > BLANK_LUMA) return false
        }
        return true
    }

    private suspend fun recognize(b: Bitmap): String = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(b, 0))
            .addOnSuccessListener { cont.resume(it.text) }
            .addOnFailureListener { cont.resume("") }
    }

    private fun startInForeground(@StringRes body: Int) {
        val stop = PendingIntent.getService(
            this, 0, Intent(this, ScreenCheckService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, NotificationChannels.STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.screen_check_title))
            .setContentText(getString(body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(body)))
            .setOngoing(true)
            .addAction(0, getString(R.string.screen_check_stop), stop)
            .build()
        if (projection == null) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, n, type)
        } else {
            try {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, n)
            } catch (_: SecurityException) {
                // Notifications turned off; the check keeps running.
            }
        }
    }

    override fun onDestroy() {
        job?.cancel()
        display?.release()
        reader?.close()
        projection?.stop()
        projection = null
        recognizer.close()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScreenCheck"
        private const val ACTION_STOP = "com.scamguard.app.screencheck.STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "projection_data"
        private const val NOTIFICATION_ID = 0x3002
        private const val CAPTURE_WIDTH = 720
        private const val FIRST_FRAME_MS = 1_500L
        private const val INTERVAL_MS = 3_000L
        /** Without any call window (e.g. a manual test) the check runs this long. */
        private const val NO_CALL_MS = 2 * 60_000L
        private const val MAX_MS = 90 * 60_000L
        private const val BLANK_LIMIT = 3
        private const val BLANK_LUMA = 3 * 16
        private const val GRID = 10

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCheckService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
