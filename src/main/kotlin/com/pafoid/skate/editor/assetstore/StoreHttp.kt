package com.pafoid.skate.editor.assetstore

import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/** Cooperative cancellation for downloads; checked between chunks. */
class CancelToken {
    private val flag = AtomicBoolean(false)
    val isCancelled: Boolean get() = flag.get()
    fun cancel() = flag.set(true)
}

class StoreCancelledException : IOException("cancelled")

class StoreHttpException(val status: Int, message: String) : IOException(message)

class HttpResult(val status: Int, val body: ByteArray, val headers: Map<String, List<String>> = emptyMap()) {
    val text: String get() = body.toString(Charsets.UTF_8)
    val isSuccess: Boolean get() = status in 200..299
}

/**
 * The asset store's only route to the network (AST-16 §2), so tests use a fake and never open sockets.
 * [get] returns any status; [download] throws [StoreHttpException] on a non-2xx status.
 */
interface StoreHttp {
    fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResult

    /** Streams [url] into [target]; [progress] gets (bytes so far, total or null). */
    fun download(url: String, headers: Map<String, String>, target: File, cancel: CancelToken, progress: (Long, Long?) -> Unit = { _, _ -> })

    companion object {
        const val USER_AGENT = "SkateEngine/0.1 (asset-store)"
        private val SECRET_HEADERS = setOf("authorization", "x-auth-token", "cookie")

        /** [headers] safe to log: credentials replaced with `***`. */
        fun redact(headers: Map<String, String>): Map<String, String> =
            headers.mapValues { (k, v) -> if (k.lowercase() in SECRET_HEADERS) "***" else v }

        /** Seconds to wait from a `Retry-After` header (delta-seconds form only), capped at [maxSeconds]. */
        fun retryAfterSeconds(headers: Map<String, List<String>>, maxSeconds: Long = 10): Long? =
            headers.entries.firstOrNull { it.key.equals("retry-after", ignoreCase = true) }
                ?.value?.firstOrNull()?.trim()?.toLongOrNull()?.coerceIn(0, maxSeconds)
    }
}

/** JDK 17 `HttpClient` implementation: sends a unique User-Agent (Poly Haven requirement), retries one 429. */
class JdkStoreHttp(
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build(),
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) : StoreHttp {

    override fun get(url: String, headers: Map<String, String>): HttpResult {
        var response = client.send(request(url, headers), HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() == 429) {
            StoreHttp.retryAfterSeconds(response.headers().map())?.let { sleep(it * 1000) } ?: sleep(1000)
            response = client.send(request(url, headers), HttpResponse.BodyHandlers.ofByteArray())
        }
        return HttpResult(response.statusCode(), response.body(), response.headers().map())
    }

    override fun download(url: String, headers: Map<String, String>, target: File, cancel: CancelToken, progress: (Long, Long?) -> Unit) {
        val response = client.send(request(url, headers), HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) {
            response.body().close()
            throw StoreHttpException(response.statusCode(), "HTTP ${response.statusCode()} for $url")
        }
        val total = response.headers().firstValueAsLong("content-length").let { if (it.isPresent) it.asLong else null }
        target.parentFile?.mkdirs()
        response.body().use { input ->
            target.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    if (cancel.isCancelled) throw StoreCancelledException()
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    done += n
                    progress(done, total)
                }
            }
        }
    }

    private fun request(url: String, headers: Map<String, String>): HttpRequest {
        val builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET()
            .header("User-Agent", StoreHttp.USER_AGENT)
        headers.forEach { (k, v) -> builder.header(k, v) }
        return builder.build()
    }
}
