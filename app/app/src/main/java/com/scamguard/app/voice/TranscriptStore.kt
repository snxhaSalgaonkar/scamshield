package com.scamguard.app.voice

import android.content.Context
import java.io.File

/**
 * What was heard on a call, kept on this phone only (app-private storage, never sent or
 * exported) so the call's details can show it. One small text file per call window, removed
 * by "Delete all my data". The audio itself is never stored.
 */
object TranscriptStore {
    data class Line(val ts: Long, val text: String)

    private const val DIR = "transcripts"
    private const val MAX_FILES = 200

    private fun dir(context: Context) = File(context.filesDir, DIR)

    // Window ids are UUIDs or "sim-" + UUID; anything else is rejected so it cannot escape the folder.
    private fun file(context: Context, windowId: String): File? =
        if (windowId.matches(Regex("[A-Za-z0-9-]{1,64}"))) File(dir(context), "$windowId.txt") else null

    @Synchronized
    fun append(context: Context, windowId: String, ts: Long, text: String) {
        val f = file(context, windowId) ?: return
        if (!f.exists()) {
            f.parentFile?.mkdirs()
            prune(context)
        }
        f.appendText("$ts\t${text.replace(Regex("[\\t\\r\\n]+"), " ")}\n")
    }

    fun read(context: Context, windowId: String): List<Line> {
        val f = file(context, windowId)?.takeIf { it.exists() } ?: return emptyList()
        return f.readLines().mapNotNull { row ->
            val tab = row.indexOf('\t')
            row.substring(0, maxOf(tab, 0)).toLongOrNull()?.let { Line(it, row.substring(tab + 1)) }
        }
    }

    fun clearAll(context: Context) {
        dir(context).deleteRecursively()
    }

    /** Keeps only the newest [MAX_FILES] transcripts. */
    private fun prune(context: Context) {
        val files = dir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(MAX_FILES - 1).forEach { it.delete() }
    }
}
