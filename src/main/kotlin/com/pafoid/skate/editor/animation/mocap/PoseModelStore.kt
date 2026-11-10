package com.pafoid.skate.editor.animation.mocap

import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/** Downloads [url] into [target], reporting 0..1 progress; throws on failure or when [cancel] is set. */
fun interface ModelFetcher {
    fun fetch(url: String, target: File, cancel: AtomicBoolean, onProgress: (Float) -> Unit)

    companion object {
        val HTTP = ModelFetcher { url, target, cancel, onProgress ->
            val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build()
            val response = client.send(HttpRequest.newBuilder(URI(url)).timeout(Duration.ofMinutes(10)).GET().build(), HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() != 200) {
                response.body().close()
                throw IOException("HTTP ${response.statusCode()}")
            }
            val total = response.headers().firstValueAsLong("Content-Length").orElse(-1L)
            response.body().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        if (cancel.get()) throw IOException("cancelled")
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
    }
}

/**
 * The MediaPipe pose landmarker model used by the Tasks API (ANI-18). Newer MediaPipe releases have no built-in pose
 * model (the legacy `mp.solutions` API is gone), so the editor downloads Google's `pose_landmarker_heavy.task` once
 * into the per-user data folder and reuses it. A model path set in Setup always wins and is never downloaded over.
 *
 * The download happens here, in the editor, not in the Python adapter: the editor reports progress and errors in its
 * own UI, and the adapter stays a local-files-only tool.
 */
class PoseModelStore(
    private val dir: () -> File,
    private val fetcher: ModelFetcher = ModelFetcher.HTTP,
    private val url: String = DEFAULT_URL,
    private val minBytes: Long = MIN_BYTES,
) {
    /** Where the downloaded model lives (it may not exist yet). */
    val defaultFile: File get() = File(dir(), FILE_NAME)

    /**
     * The model to pass to the adapter: [override] when set (it must exist), else [defaultFile], downloaded first if
     * missing. Throws [MocapException] with a message for the UI when the model cannot be had.
     */
    fun resolve(override: String?, cancel: AtomicBoolean = AtomicBoolean(false), onProgress: (Float) -> Unit = {}): File {
        if (!override.isNullOrBlank()) {
            val file = File(override)
            if (!file.isFile) throw MocapException("Model file not found: $override")
            return file
        }
        val file = defaultFile
        if (file.isFile && file.length() >= minBytes) return file
        file.parentFile?.mkdirs()
        val part = File(file.parentFile, "$FILE_NAME.part")
        try {
            onProgress(0f)
            fetcher.fetch(url, part, cancel, onProgress)
            if (cancel.get()) throw MocapException("cancelled")
            if (!part.isFile || part.length() < minBytes) throw IOException("the download is incomplete (${part.length()} bytes)")
            Files.move(part.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            onProgress(1f)
            return file
        } catch (e: MocapException) {
            throw e
        } catch (e: Exception) {
            if (cancel.get()) throw MocapException("cancelled")
            throw MocapException("Could not download the pose model from $url: ${e.message ?: e.javaClass.simpleName}. " +
                "Check the internet connection, or download it yourself and set it as the model file in Setup.")
        } finally {
            part.delete()
        }
    }

    companion object {
        const val FILE_NAME = "pose_landmarker_heavy.task"
        const val DEFAULT_URL = "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_heavy/float16/latest/pose_landmarker_heavy.task"
        /** The real model is about 30 MB; anything under 1 MB is an error page or a cut download. */
        const val MIN_BYTES = 1_000_000L
    }
}
