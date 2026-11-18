package com.pafoid.skate.editor.assetimport.osm

import com.pafoid.skate.editor.assetstore.CancelToken
import com.pafoid.skate.editor.assetstore.HttpResult
import com.pafoid.skate.editor.assetstore.StoreCancelledException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * The OSM code's only route to the network (Overpass, Nominatim, map tiles; AST-17 §3.1), so tests use a fake and
 * never open sockets. Every request carries [USER_AGENT] (Q-N3). [get] returns any status.
 */
fun interface OsmHttp {
    fun get(url: String, headers: Map<String, String>, cancel: CancelToken): HttpResult

    companion object {
        /** Stable, distinct User-Agent required by the OpenFreeMap, Overpass and Nominatim policies (Q-N3). */
        const val USER_AGENT = "SkateEngine/0.1 (+https://github.com/as00paf/skate)"

        /** Headers every OSM request starts from; callers add theirs. */
        fun headers(vararg extra: Pair<String, String>): Map<String, String> = mapOf("User-Agent" to USER_AGENT) + extra
    }
}

/** JDK 17 `HttpClient` implementation; [CancelToken] aborts the request in flight. */
class JdkOsmHttp(
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build(),
    private val timeout: Duration = Duration.ofSeconds(90),
) : OsmHttp {
    override fun get(url: String, headers: Map<String, String>, cancel: CancelToken): HttpResult {
        val builder = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().setHeader("User-Agent", OsmHttp.USER_AGENT)
        headers.forEach { (k, v) -> builder.setHeader(k, v) }
        val future = client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
        while (true) {
            if (cancel.isCancelled) {
                future.cancel(true)
                throw StoreCancelledException()
            }
            try {
                val response = future.get(100, TimeUnit.MILLISECONDS)
                return HttpResult(response.statusCode(), response.body(), response.headers().map())
            } catch (_: TimeoutException) {
                // poll the cancel token again
            } catch (_: CancellationException) {
                throw StoreCancelledException()
            } catch (e: java.util.concurrent.ExecutionException) {
                throw (e.cause as? java.io.IOException) ?: java.io.IOException(e.cause?.message ?: "request failed", e.cause)
            }
        }
    }
}
