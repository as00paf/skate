package com.pafoid.skate.editor.vcs.auth

import com.pafoid.skate.editor.vcs.auth.GitHubDeviceFlow.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GitHubDeviceFlowTest {
    private var now = 1_000L
    private val requests = mutableListOf<Pair<String, Map<String, String>>>()
    private val replies = ArrayDeque<Map<String, String>>()

    private val poster = FormPoster { url, form ->
        requests += url to form
        replies.removeFirst().also { if ("__throw" in it) throw java.io.IOException(it.getValue("__throw")) }
    }

    private val flow = GitHubDeviceFlow("client-123", poster) { now }

    private fun started(interval: String = "5"): State.WaitingForUser {
        replies += mapOf(
            "device_code" to "dev-1", "user_code" to "ABCD-1234", "verification_uri" to "https://github.com/login/device",
            "interval" to interval, "expires_in" to "900",
        )
        return flow.start() as State.WaitingForUser
    }

    @Test
    fun Start_AsksForAUserCode() {
        val waiting = started()
        assertEquals("ABCD-1234", waiting.userCode)
        assertEquals(5, waiting.intervalSeconds)
        assertEquals(1_000L + 900_000L, waiting.expiresAtMs)
        assertEquals(GitHubDeviceFlow.DEVICE_CODE_URL to mapOf("client_id" to "client-123", "scope" to "repo"), requests.single())
        assertFalse(waiting.toString().contains("dev-1"))
    }

    @Test
    fun Poll_PendingThenSignedIn() {
        started()
        replies += mapOf("error" to "authorization_pending")
        assertTrue(flow.poll() is State.WaitingForUser)
        replies += mapOf("access_token" to "gho_tok", "token_type" to "bearer")
        val signed = flow.poll() as State.SignedIn
        assertEquals(GitCredential(GitHubDeviceFlow.TOKEN_USER, "gho_tok"), signed.credential)
        assertEquals(
            mapOf("client_id" to "client-123", "device_code" to "dev-1", "grant_type" to GitHubDeviceFlow.GRANT_TYPE),
            requests.last().second,
        )
        // Once signed in, polling again does not call GitHub.
        val count = requests.size
        flow.poll()
        assertEquals(count, requests.size)
    }

    @Test
    fun Poll_SlowDownRaisesTheInterval() {
        started()
        replies += mapOf("error" to "slow_down")
        assertEquals(10, (flow.poll() as State.WaitingForUser).intervalSeconds)
        replies += mapOf("error" to "slow_down", "interval" to "20")
        assertEquals(20, (flow.poll() as State.WaitingForUser).intervalSeconds)
    }

    @Test
    fun Poll_ExpiredAndDeniedEndTheFlow() {
        started()
        replies += mapOf("error" to "access_denied")
        assertEquals(State.Failed("vcs.signin.denied"), flow.poll())

        started()
        replies += mapOf("error" to "expired_token")
        assertEquals(State.Failed("vcs.signin.expired"), flow.poll())

        started()
        now += 901_000L
        assertEquals(State.Failed("vcs.signin.expired"), flow.poll())
    }

    @Test
    fun NetworkErrors_FailTheStartButNotAPoll() {
        val failing = GitHubDeviceFlow("c", { _, _ -> throw java.io.IOException("offline") }) { now }
        assertEquals(State.Failed("vcs.signin.network", "offline"), failing.start())

        val waiting = started()
        replies += mapOf("__throw" to "blip")
        assertEquals(waiting, flow.poll())
        replies += mapOf("access_token" to "t")
        assertTrue(flow.poll() is State.SignedIn)
    }

    @Test
    fun Start_WithAnErrorAnswerFails() {
        replies += mapOf("error" to "unauthorized_client", "error_description" to "Device flow is disabled")
        assertEquals(State.Failed("vcs.signin.failed", "Device flow is disabled"), flow.start())
    }

    @Test
    fun FormParsing() {
        assertEquals(
            mapOf("access_token" to "a b", "scope" to "repo,gist", "empty" to ""),
            JavaNetFormPoster.parse("access_token=a+b&scope=repo%2Cgist&empty\n"),
        )
    }
}
