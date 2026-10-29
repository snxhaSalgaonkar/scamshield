package com.scamguard.app.voice;

/**
 * Runs in a Shizuku user service (ADB-shell rights). Records call audio for call listening and
 * performs the real-time response actions an ordinary app is not allowed to do.
 * Every action returns "" on success, otherwise a short reason.
 */
interface ICallAudioService {
    /** Called by Shizuku when the service is unbound. Transaction code is fixed by Shizuku. */
    void destroy() = 16777114;

    /**
     * Records [source] (a MediaRecorder.AudioSource value) as 16 kHz mono PCM16 into [sink].
     * Returns "" on success, otherwise a short reason.
     */
    String start(in ParcelFileDescriptor sink, int source) = 1;

    void stop() = 2;

    /** Hangs up the current phone call (end-call key). */
    String endCall() = 3;

    /** Force-stops an app, which ends a WhatsApp / Telegram / Skype call it is running. */
    String forceStop(String pkg) = 4;

    /** Suspends (greys out, cannot be opened) or unsuspends apps for [userId]. */
    String setSuspended(in String[] pkgs, boolean suspended, int userId) = 5;

    /** Turns the screen off, which locks the phone when a screen lock is set. */
    String sleep() = 6;

    /** Switches the phone off. */
    String powerOff() = 7;
}
