package com.scamguard.app.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.DataInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Reads the microphone itself (16 kHz mono PCM16) and streams it into the speech service
 * through a pipe ([android.speech.RecognizerIntent.EXTRA_AUDIO_SOURCE], Android 13+). During a phone call
 * Android gives ordinary apps silence; with the app's accessibility helper enabled it lets
 * the app keep capturing, which the speech service's own microphone access does not get.
 *
 * With [shell] (a Shizuku user service with ADB-shell rights) the call itself is recorded
 * first: VOICE_CALL (both sides), then the caller's side, then the user's side. For a [voip]
 * call (WhatsApp, Telegram...) the shell instead loops back the app's call playback, which is
 * the other person's voice. Only if those fail does it fall back to the microphone sources.
 *
 * Audio only passes through memory: nothing is written to storage. Detects "digital
 * silence" (every sample zero, which means Android is blocking capture rather than the room
 * being quiet) and then tries the next audio source.
 */
class CallAudioCapture(
    private val onLevel: (Float) -> Unit,
    private val onSourceChanged: (name: String, viaShizuku: Boolean) -> Unit,
    private val onBlocked: (name: String) -> Unit,
    private val shell: ICallAudioService? = null,
    private val onShellLost: () -> Unit = {},
    /** The call is in a messaging app (VoIP), so the phone's call-audio sources cannot hear it. */
    private val voip: Boolean = false,
) {
    private val queue = ArrayBlockingQueue<ByteArray>(QUEUE_CHUNKS)
    @Volatile private var running = false
    @Volatile private var out: FileOutputStream? = null
    private var reader: Thread? = null
    private var writer: Thread? = null
    private var lastRead: ParcelFileDescriptor? = null

    /** True once real (non-zero) audio has been captured. */
    @Volatile var gotAudio = false
        private set

    fun start() {
        if (running) return
        running = true
        reader = Thread(::readLoop, "call-audio-read").apply { start() }
        writer = Thread(::writeLoop, "call-audio-write").apply { start() }
    }

    /** A fresh pipe for the next recognition attempt; the previous one is closed. Returns the read end. */
    @Synchronized
    fun newPipe(): ParcelFileDescriptor {
        val (read, write) = ParcelFileDescriptor.createPipe()
        val old = out
        out = ParcelFileDescriptor.AutoCloseOutputStream(write)
        try { old?.close() } catch (_: IOException) {}
        try { lastRead?.close() } catch (_: IOException) {}
        lastRead = read
        queue.clear()
        return read
    }

    fun stop() {
        running = false
        try { shell?.stop() } catch (_: Exception) {} // DeadObjectException if Shizuku died
        reader?.interrupt()
        writer?.interrupt()
        synchronized(this) {
            try { out?.close() } catch (_: IOException) {}
            try { lastRead?.close() } catch (_: IOException) {}
            out = null
            lastRead = null
        }
        queue.clear()
    }

    private fun readLoop() {
        if (shell != null && readShell(shell)) return
        if (running) readMic()
    }

    /** Silence-tracking state shared by both capture paths. */
    private var zeroMs = 0

    /** How long a source may give only digital silence before it counts as blocked. */
    private var blockedMs = BLOCKED_MS

    /** Level, silence detection and queueing for one chunk; returns true once the source looks blocked. */
    private fun process(chunk: ShortArray, n: Int): Boolean {
        var sum = 0.0
        var allZero = true
        for (i in 0 until n) {
            val v = chunk[i].toInt()
            if (v != 0) allZero = false
            sum += v.toDouble() * v
        }
        if (allZero) zeroMs += n * 1000 / RATE else { zeroMs = 0; gotAudio = true }
        val dbfs = if (sum == 0.0) -90.0 else 20 * log10(sqrt(sum / n) / 32768.0)
        // The same rough scale the speech service reports (-2 silent .. 10 loud).
        onLevel(((dbfs + 60) / 5).toFloat().coerceIn(-2f, 10f))
        queue.offer(toBytes(chunk, n))
        return zeroMs >= blockedMs && !gotAudio
    }

    /**
     * Records the call through Shizuku; false if no call-audio source gave real audio, or if
     * the recorder stopped mid-call (Shizuku killed, recorder error), so the microphone takes over.
     */
    private fun readShell(svc: ICallAudioService): Boolean {
        val chunk = ShortArray(RATE / 10)
        val bytes = ByteArray(chunk.size * 2)
        for ((source, name) in if (voip) VOIP_SHELL_SOURCES else SHELL_SOURCES) {
            if (!running) return true
            val (read, write) = ParcelFileDescriptor.createPipe()
            // Binder calls throw RemoteException (e.g. DeadObjectException) when Shizuku has died.
            val error = try { svc.start(write, source) } catch (e: Exception) { e.javaClass.simpleName }
            write.close()
            if (!error.isNullOrEmpty()) {
                read.close()
                onBlocked("$name ($error)")
                continue
            }
            onSourceChanged(name, true)
            zeroMs = 0
            // A VoIP stream plays nothing while the other person is silent, so give it longer.
            blockedMs = if (source == SOURCE_VOIP_PLAYBACK) VOIP_BLOCKED_MS else BLOCKED_MS
            var blocked = false
            var lost = false
            DataInputStream(ParcelFileDescriptor.AutoCloseInputStream(read)).use { input ->
                try {
                    while (running) {
                        input.readFully(bytes)
                        for (i in chunk.indices) chunk[i] = ((bytes[2 * i + 1].toInt() shl 8) or (bytes[2 * i].toInt() and 0xff)).toShort()
                        if (process(chunk, chunk.size)) {
                            blocked = true
                            break
                        }
                    }
                } catch (_: IOException) {
                    // The shell service stopped or died.
                    blocked = !gotAudio
                    lost = gotAudio && running
                }
            }
            try { svc.stop() } catch (_: Exception) {}
            if (lost) {
                onShellLost()
                return false
            }
            if (!blocked) return true
            onBlocked(name)
        }
        return false
    }

    @SuppressLint("MissingPermission") // Checked before the service starts listening.
    private fun readMic() {
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val chunk = ShortArray(RATE / 10) // 100 ms
        var sourceIndex = 0
        while (running && sourceIndex < SOURCES.size) {
            val (source, name) = SOURCES[sourceIndex]
            val rec = try {
                AudioRecord(source, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, RATE))
            } catch (e: RuntimeException) {
                null
            }
            if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
                rec?.release()
                sourceIndex++
                continue
            }
            onSourceChanged(name, false)
            zeroMs = 0
            blockedMs = BLOCKED_MS
            var blocked = false
            try {
                rec.startRecording()
                while (running) {
                    val n = rec.read(chunk, 0, chunk.size)
                    if (n <= 0) continue
                    if (process(chunk, n)) {
                        blocked = true
                        break
                    }
                }
            } catch (e: RuntimeException) {
                Log.w(TAG, "capture failed on $name: ${e.javaClass.simpleName}")
            } finally {
                try { rec.stop() } catch (_: RuntimeException) {}
                rec.release()
            }
            if (!blocked) break
            onBlocked(name)
            sourceIndex++
        }
        // Every source gave silence: keep the last one's silence flowing so the recogniser still runs.
    }

    private fun writeLoop() {
        while (running) {
            val bytes = try { queue.poll(200, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { null } ?: continue
            val o = out ?: continue
            try {
                o.write(bytes)
            } catch (_: IOException) {
                // The recogniser closed its end; the next attempt gets a new pipe.
            }
        }
    }

    private fun toBytes(s: ShortArray, n: Int): ByteArray {
        val b = ByteArray(n * 2)
        for (i in 0 until n) {
            b[2 * i] = (s[i].toInt() and 0xff).toByte()
            b[2 * i + 1] = (s[i].toInt() shr 8 and 0xff).toByte()
        }
        return b
    }

    companion object {
        private const val TAG = "CallAudio"
        const val RATE = 16_000
        private const val QUEUE_CHUNKS = 30
        private const val BLOCKED_MS = 3_000
        private const val VOIP_BLOCKED_MS = 15_000
        /** Not a MediaRecorder source: the shell's loop-back of messaging-app call audio ([ShellAudioService]). */
        const val SOURCE_VOIP_PLAYBACK = -100
        /** Call-audio sources, only recordable with shell rights (Shizuku). */
        private val SHELL_SOURCES = listOf(
            MediaRecorder.AudioSource.VOICE_CALL to "call audio, both sides",
            MediaRecorder.AudioSource.VOICE_DOWNLINK to "call audio, caller's side",
            MediaRecorder.AudioSource.VOICE_UPLINK to "call audio, your side",
        )

        /** For a messaging-app call: its call playback (the other person), through Shizuku. */
        private val VOIP_SHELL_SOURCES = listOf(
            SOURCE_VOIP_PLAYBACK to "app call audio, caller's side",
        )

        /** Tried in order until one gives real audio during the call. */
        private val SOURCES = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION to "voice recognition",
            MediaRecorder.AudioSource.MIC to "microphone",
            MediaRecorder.AudioSource.VOICE_COMMUNICATION to "voice communication",
            MediaRecorder.AudioSource.CAMCORDER to "camcorder",
        )
    }
}
