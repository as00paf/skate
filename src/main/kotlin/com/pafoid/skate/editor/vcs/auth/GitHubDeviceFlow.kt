package com.pafoid.skate.editor.vcs.auth

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** A form POST: the response body's `key=value&…` pairs (GitHub's default answer format for these endpoints). */
fun interface FormPoster {
    fun post(url: String, form: Map<String, String>): Map<String, String>
}

/** [FormPoster] on `java.net.http` (no new library, EDT-60 §3). */
object JavaNetFormPoster : FormPoster {
    private val client by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build() }

    override fun post(url: String, form: Map<String, String>): Map<String, String> {
        val body = form.entries.joinToString("&") { (k, v) -> enc(k) + "=" + enc(v) }
        val request = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build()
        return parse(client.send(request, HttpResponse.BodyHandlers.ofString()).body())
    }

    fun parse(body: String): Map<String, String> = body.trim().split('&').filter { it.isNotEmpty() }.associate { pair ->
        val eq = pair.indexOf('=')
        if (eq < 0) dec(pair) to "" else dec(pair.substring(0, eq)) to dec(pair.substring(eq + 1))
    }

    private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)
    private fun dec(s: String) = URLDecoder.decode(s, Charsets.UTF_8)
}

/**
 * EDT-60 §5 phase 2: GitHub sign-in with the OAuth *device flow*. The editor shows [State.WaitingForUser.userCode]
 * and [State.WaitingForUser.verificationUri]; the user approves in the browser; [poll] (every
 * [State.WaitingForUser.intervalSeconds]) ends in [State.SignedIn] with a token for the OS store. No password ever
 * reaches Skate. Pure state machine: [poster] does the HTTP, the caller does the waiting (off the frame).
 *
 * It needs the public `client_id` of the "Skate" OAuth App with device flow enabled ([GitHubOAuth.CLIENT_ID]).
 */
class GitHubDeviceFlow(
    private val clientId: String,
    private val poster: FormPoster = JavaNetFormPoster,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    sealed class State {
        object Idle : State()
        data class WaitingForUser(
            val userCode: String,
            val verificationUri: String,
            val intervalSeconds: Int,
            val expiresAtMs: Long,
            internal val deviceCode: String,
        ) : State() {
            override fun toString(): String = "WaitingForUser($userCode, $verificationUri, every ${intervalSeconds}s)"
        }
        data class SignedIn(val credential: GitCredential) : State()

        /** Ended without a token; [messageKey] says why (`vcs.signin.*`). */
        data class Failed(val messageKey: String, val detail: String = "") : State()
    }

    var state: State = State.Idle
        private set

    /** Asks GitHub for a user code. */
    fun start(scope: String = GitHubOAuth.SCOPE): State {
        state = try {
            val r = poster.post(DEVICE_CODE_URL, mapOf("client_id" to clientId, "scope" to scope))
            val device = r["device_code"]
            val user = r["user_code"]
            if (device == null || user == null) {
                State.Failed("vcs.signin.failed", r["error_description"] ?: r["error"].orEmpty())
            } else {
                State.WaitingForUser(
                    userCode = user,
                    verificationUri = r["verification_uri"] ?: "https://github.com/login/device",
                    intervalSeconds = r["interval"]?.toIntOrNull() ?: 5,
                    expiresAtMs = clock() + (r["expires_in"]?.toLongOrNull() ?: 900L) * 1000L,
                    deviceCode = device,
                )
            }
        } catch (e: Exception) {
            State.Failed("vcs.signin.network", e.message.orEmpty())
        }
        return state
    }

    /** One check: still pending (maybe with a longer interval), signed in, or failed. */
    fun poll(): State {
        val waiting = state as? State.WaitingForUser ?: return state
        if (clock() >= waiting.expiresAtMs) return State.Failed("vcs.signin.expired").also { state = it }
        state = try {
            val r = poster.post(
                ACCESS_TOKEN_URL,
                mapOf("client_id" to clientId, "device_code" to waiting.deviceCode, "grant_type" to GRANT_TYPE),
            )
            val token = r["access_token"]
            when {
                token != null -> State.SignedIn(GitCredential(TOKEN_USER, token))
                r["error"] == "authorization_pending" -> waiting
                r["error"] == "slow_down" -> waiting.copy(
                    intervalSeconds = r["interval"]?.toIntOrNull() ?: (waiting.intervalSeconds + SLOW_DOWN_STEP),
                )
                r["error"] == "expired_token" -> State.Failed("vcs.signin.expired")
                r["error"] == "access_denied" -> State.Failed("vcs.signin.denied")
                else -> State.Failed("vcs.signin.failed", r["error_description"] ?: r["error"].orEmpty())
            }
        } catch (e: Exception) {
            // A network hiccup is not the end: try again at the next interval.
            waiting
        }
        return state
    }

    fun cancel() {
        state = State.Idle
    }

    companion object {
        const val DEVICE_CODE_URL = "https://github.com/login/device/code"
        const val ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"
        const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
        const val SLOW_DOWN_STEP = 5

        /** GitHub takes an OAuth token over HTTPS as the password, with any non-empty user name. */
        const val TOKEN_USER = "x-access-token"
    }
}

/** The "Skate" GitHub OAuth App. */
object GitHubOAuth {
    /**
     * Null until the human registers the app (EDT-60 phase 2: "set up with the human"). While null, browser sign-in
     * is hidden and GitHub works with a pasted personal access token.
     */
    val CLIENT_ID: String? = System.getProperty("skate.github.clientId")?.takeIf { it.isNotBlank() }

    const val SCOPE = "repo"

    val browserSignInAvailable: Boolean get() = CLIENT_ID != null
}
