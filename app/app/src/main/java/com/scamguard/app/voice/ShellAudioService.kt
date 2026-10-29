package com.scamguard.app.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * The Shizuku user service: runs in a separate process with ADB-shell rights, which (unlike
 * an app) may record the call-audio sources (VOICE_CALL, VOICE_DOWNLINK, VOICE_UPLINK).
 * It only streams raw PCM into the pipe the app hands it; nothing is written to storage.
 *
 * It also runs the response actions that need shell rights: ending any call, force-stopping a
 * messaging app's call, suspending payment apps, turning the screen off and switching off.
 * Each action is a fixed shell command; package names are validated, nothing else is accepted.
 *
 * Shizuku creates it by reflection, with the Context constructor when available.
 */
class ShellAudioService() : ICallAudioService.Stub() {
    private var context: Context? = null
    @Volatile private var running = false
    private var thread: Thread? = null
    /** The registered loop-back policy while [SOURCE_VOIP_PLAYBACK] records; unregistered on stop. */
    private var policy: Any? = null

    constructor(context: Context) : this() {
        this.context = context
    }

    override fun destroy() {
        stop()
        exitProcess(0)
    }

    override fun start(out: ParcelFileDescriptor, source: Int): String {
        stop()
        val rec = try {
            if (source == SOURCE_VOIP_PLAYBACK) createVoipCapture() else create(source)
        } catch (t: Throwable) {
            unregisterPolicy()
            out.close()
            val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
            return cause.javaClass.simpleName + ": " + cause.message
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            unregisterPolicy()
            out.close()
            return "not initialised"
        }
        try {
            rec.startRecording()
        } catch (t: Throwable) {
            rec.release()
            unregisterPolicy()
            out.close()
            return t.javaClass.simpleName + ": " + t.message
        }
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            rec.release()
            unregisterPolicy()
            out.close()
            return "did not start recording"
        }
        running = true
        thread = Thread({
            val stream = ParcelFileDescriptor.AutoCloseOutputStream(out)
            val buf = ByteArray(CHUNK_BYTES)
            try {
                while (running) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n < 0) break // recorder error; closing the pipe tells the app
                    if (n > 0) stream.write(buf, 0, n)
                }
            } catch (_: IOException) {
                // The app closed its end.
            } finally {
                try { rec.stop() } catch (_: Throwable) {}
                rec.release()
                unregisterPolicy()
                try { stream.close() } catch (_: IOException) {}
            }
        }, "shell-call-audio").apply { start() }
        return ""
    }

    override fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    /**
     * Captures what messaging apps play as call audio (usage VOICE_COMMUNICATION: the other
     * person in a WhatsApp / Telegram / Skype call). A VoIP call is not on the phone's call
     * path, so VOICE_CALL cannot hear it. This registers a system audio policy that loops that
     * playback back to us while still rendering it, so the user keeps hearing the call.
     * The shell holds MODIFY_AUDIO_ROUTING and CAPTURE_VOICE_COMMUNICATION_OUTPUT, which the
     * policy needs. The classes are system APIs, reached by reflection.
     */
    private fun createVoipCapture(): AudioRecord {
        val base = context ?: error("no context")
        // Attribute to the shell package, which owns this process's uid.
        val ctx = try { base.createPackageContext(SHELL_PACKAGE, 0) } catch (_: Throwable) { base }
        val format = AudioFormat.Builder()
            .setSampleRate(RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
        val usage = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build()

        val ruleCls = Class.forName("android.media.audiopolicy.AudioMixingRule")
        val ruleBuilderCls = Class.forName("android.media.audiopolicy.AudioMixingRule\$Builder")
        val ruleBuilder = ruleBuilderCls.getConstructor().newInstance()
        val matchUsage = ruleCls.getField("RULE_MATCH_ATTRIBUTE_USAGE").getInt(null)
        ruleBuilderCls.getMethod("addRule", AudioAttributes::class.java, Int::class.javaPrimitiveType)
            .invoke(ruleBuilder, usage, matchUsage)
        // Android 11+: voice-call audio is only matched when the rule says so explicitly.
        try {
            ruleBuilderCls.getMethod("voiceCommunicationCaptureAllowed", Boolean::class.javaPrimitiveType).invoke(ruleBuilder, true)
        } catch (_: NoSuchMethodException) {
            // Android 10: no such switch; the permission alone decides.
        }
        val rule = ruleBuilderCls.getMethod("build").invoke(ruleBuilder)

        val mixCls = Class.forName("android.media.audiopolicy.AudioMix")
        val mixBuilderCls = Class.forName("android.media.audiopolicy.AudioMix\$Builder")
        val mixBuilder = mixBuilderCls.getConstructor(ruleCls).newInstance(rule)
        mixBuilderCls.getMethod("setFormat", AudioFormat::class.java).invoke(mixBuilder, format)
        val loopBackRender = mixCls.getField("ROUTE_FLAG_LOOP_BACK").getInt(null) or mixCls.getField("ROUTE_FLAG_RENDER").getInt(null)
        mixBuilderCls.getMethod("setRouteFlags", Int::class.javaPrimitiveType).invoke(mixBuilder, loopBackRender)
        val mix = mixBuilderCls.getMethod("build").invoke(mixBuilder)

        val policyCls = Class.forName("android.media.audiopolicy.AudioPolicy")
        val policyBuilderCls = Class.forName("android.media.audiopolicy.AudioPolicy\$Builder")
        val policyBuilder = policyBuilderCls.getConstructor(Context::class.java).newInstance(ctx)
        policyBuilderCls.getMethod("addMix", mixCls).invoke(policyBuilder, mix)
        val newPolicy = policyBuilderCls.getMethod("build").invoke(policyBuilder)

        val audio = ctx.getSystemService(AudioManager::class.java) ?: error("no audio service")
        val result = AudioManager::class.java.getMethod("registerAudioPolicy", policyCls).invoke(audio, newPolicy) as Int
        if (result != 0) error("registerAudioPolicy returned $result")
        policy = newPolicy
        return policyCls.getMethod("createAudioRecordSink", mixCls).invoke(newPolicy, mix) as AudioRecord
    }

    private fun unregisterPolicy() {
        val p = policy ?: return
        policy = null
        try {
            val audio = context?.getSystemService(AudioManager::class.java) ?: return
            AudioManager::class.java.getMethod("unregisterAudioPolicy", Class.forName("android.media.audiopolicy.AudioPolicy")).invoke(audio, p)
        } catch (_: Throwable) {
            // Already gone with its process or the audio service restarted.
        }
    }

    override fun endCall(): String = sh("input", "keyevent", "KEYCODE_ENDCALL")

    override fun forceStop(pkg: String): String {
        if (!PKG.matches(pkg)) return "bad package"
        return sh("am", "force-stop", pkg)
    }

    override fun setSuspended(pkgs: Array<String>, suspended: Boolean, userId: Int): String {
        val errors = pkgs.mapNotNull { pkg ->
            if (!PKG.matches(pkg)) return@mapNotNull "$pkg: bad package"
            sh("pm", if (suspended) "suspend" else "unsuspend", "--user", userId.toString(), pkg)
                .takeIf { it.isNotEmpty() }?.let { "$pkg: $it" }
        }
        return errors.joinToString("; ")
    }

    override fun sleep(): String = sh("input", "keyevent", "KEYCODE_SLEEP")

    /** "reboot -p" is allowed for the shell user; "svc power shutdown" is the fallback. */
    override fun powerOff(): String {
        val first = sh("reboot", "-p")
        if (first.isEmpty()) return ""
        val second = sh("svc", "power", "shutdown")
        return if (second.isEmpty()) "" else "$first / $second"
    }

    /** Runs one command as the shell user; "" when it exits 0, else a short reason. */
    private fun sh(vararg cmd: String): String = try {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val finished = p.waitFor(CMD_TIMEOUT_S, TimeUnit.SECONDS)
        if (!finished) {
            p.destroy()
            "timeout"
        } else {
            val out = p.inputStream.bufferedReader().readText().trim()
            // pm prints "Package x new suspended state: true" and exits 0 on success.
            if (p.exitValue() == 0 && !out.startsWith("Error") && !out.contains("Exception")) "" else "exit ${p.exitValue()}: ${out.take(160)}"
        }
    } catch (t: Throwable) {
        t.javaClass.simpleName + ": " + t.message
    }

    /**
     * Attributes the recording to the shell package (uid 2000 owns it, and it holds the
     * call-audio permission); falls back to the plain constructor on older Android.
     */
    @SuppressLint("MissingPermission")
    private fun create(source: Int): AudioRecord {
        val format = AudioFormat.Builder()
            .setSampleRate(RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val size = maxOf(minBuf, RATE)
        val ctx = context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ctx != null) {
            val shell = try { ctx.createPackageContext(SHELL_PACKAGE, 0) } catch (_: Throwable) { null }
            if (shell != null) {
                return AudioRecord.Builder()
                    .setContext(shell)
                    .setAudioSource(source)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(size)
                    .build()
            }
        }
        return AudioRecord(source, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
    }

    private companion object {
        const val RATE = 16_000
        const val CHUNK_BYTES = 3_200 // 100 ms
        const val SHELL_PACKAGE = "com.android.shell"
        const val CMD_TIMEOUT_S = 5L
        /** Not a MediaRecorder source: the loop-back capture of messaging-app call audio. Mirrors CallAudioCapture. */
        const val SOURCE_VOIP_PLAYBACK = -100
        val PKG = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
    }
}
