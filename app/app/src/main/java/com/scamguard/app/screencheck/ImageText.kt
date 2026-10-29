package com.scamguard.app.screencheck

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Reads the text in an image the user picked or shared (a forwarded screenshot, a fake
 * notice, a payment "receipt"). On-device OCR only; the image is never stored or sent.
 */
object ImageText {
    /** The text in the image, "" when none was found, or null when the image could not be read. */
    suspend fun read(context: Context, uri: Uri): String? {
        val image = try {
            InputImage.fromFilePath(context, uri)
        } catch (_: Exception) {
            return null
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(it.text) }
                    .addOnFailureListener { cont.resume(null) }
            }
        } finally {
            recognizer.close()
        }
    }
}
