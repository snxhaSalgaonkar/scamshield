package com.scamguard.app.voice

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import com.scamguard.app.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/**
 * Connects the app to Shizuku, which runs [ShellAudioService] with ADB-shell rights. Used by
 * call listening (records the call itself, both sides, no speakerphone needed) and by the
 * response engine (end any call, suspend payment apps, screen off, switch off). Shizuku must
 * be installed and started on the phone (Wireless debugging or adb) and must allow this app.
 *
 * One shell service is bound and shared; it lives until the app process ends or [unbind].
 */
object ShizukuAudio {
    enum class Status { NOT_RUNNING, NO_PERMISSION, READY }

    private const val REQUEST_CODE = 0x5A1
    private val lock = Mutex()
    private var connection: ServiceConnection? = null
    @Volatile private var service: ICallAudioService? = null

    fun status(): Status = try {
        when {
            !Shizuku.pingBinder() || Shizuku.isPreV11() -> Status.NOT_RUNNING
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> Status.READY
            else -> Status.NO_PERMISSION
        }
    } catch (_: Throwable) {
        Status.NOT_RUNNING
    }

    /**
     * Whether call listening will actually record the call through Shizuku: the recorded audio
     * reaches the speech service only through a pipe, which needs Android 13+.
     */
    fun usable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && status() == Status.READY

    /** Shows Shizuku's own "allow this app" dialog. */
    fun requestPermission() {
        try {
            if (Shizuku.pingBinder()) Shizuku.requestPermission(REQUEST_CODE)
        } catch (_: Throwable) {
            // Shizuku stopped meanwhile; the screen shows its status.
        }
    }

    private fun args(context: Context) = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellAudioService::class.java.name))
        .daemon(false)
        .processNameSuffix("callaudio")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    /** The shared shell service, started if needed; null if Shizuku is not ready or does not answer in time. */
    suspend fun bind(context: Context): ICallAudioService? = lock.withLock {
        service?.takeIf { it.asBinder().pingBinder() }?.let { return@withLock it }
        service = null
        if (status() != Status.READY) return@withLock null
        val ready = CompletableDeferred<ICallAudioService?>()
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                ready.complete(if (binder != null && binder.pingBinder()) ICallAudioService.Stub.asInterface(binder) else null)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                ready.complete(null)
            }
        }
        try {
            // Shizuku delivers the connection on the main thread.
            withContext(Dispatchers.Main) { Shizuku.bindUserService(args(context), conn) }
        } catch (_: Throwable) {
            return@withLock null
        }
        connection = conn
        service = withTimeoutOrNull(BIND_TIMEOUT_MS) { ready.await() }
        service
    }

    fun unbind(context: Context) {
        val conn = connection ?: return
        connection = null
        service = null
        try {
            Shizuku.unbindUserService(args(context), conn, true)
        } catch (_: Throwable) {
            // Shizuku already gone.
        }
    }

    /**
     * Runs one response action on the shell service. Returns null on success, otherwise why it
     * failed ("Shizuku not ready", or the shell's reason).
     */
    suspend fun run(context: Context, action: (ICallAudioService) -> String): String? {
        val svc = bind(context) ?: return NOT_READY
        return withContext(Dispatchers.IO) {
            try {
                action(svc).ifEmpty { null }
            } catch (e: Exception) {
                // RemoteException / DeadObjectException when Shizuku died.
                service = null
                e.javaClass.simpleName
            }
        }
    }

    suspend fun endCall(context: Context) = run(context) { it.endCall() }
    suspend fun forceStop(context: Context, pkg: String) = run(context) { it.forceStop(pkg) }
    suspend fun setSuspended(context: Context, pkgs: List<String>, suspended: Boolean) =
        run(context) { it.setSuspended(pkgs.toTypedArray(), suspended, Process.myUserHandle().hashCode()) }
    suspend fun sleep(context: Context) = run(context) { it.sleep() }
    suspend fun powerOff(context: Context) = run(context) { it.powerOff() }

    const val NOT_READY = "Shizuku not ready"
    private const val BIND_TIMEOUT_MS = 6_000L
}
