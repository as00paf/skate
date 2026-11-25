package com.pafoid.skate.editor.vcs.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RemoteUrlsTest {
    @Test
    fun Strip_RemovesUserAndTokenFromHttpsUrls() {
        val p = RemoteUrls.strip("  https://me:ghp_ab%40c@github.com/me/game.git ")
        assertEquals("https://github.com/me/game.git", p.url)
        assertEquals("me", p.username)
        assertEquals("ghp_ab@c", p.secret)
        assertTrue(p.hadCredentials)
        assertFalse(p.toString().contains("ghp_"))
    }

    @Test
    fun Strip_UserOnlyAndCleanUrls() {
        RemoteUrls.strip("https://me@host.example:8443/a.git").let {
            assertEquals("https://host.example:8443/a.git", it.url)
            assertEquals("me", it.username)
            assertNull(it.secret)
        }
        RemoteUrls.strip("https://github.com/me/game.git").let {
            assertEquals("https://github.com/me/game.git", it.url)
            assertFalse(it.hadCredentials)
        }
        // An @ in the path is not user info.
        assertFalse(RemoteUrls.strip("https://host/a@b.git").hadCredentials)
        // SSH URLs keep their user: it is not a secret.
        assertEquals("git@github.com:me/game.git", RemoteUrls.strip("git@github.com:me/game.git").url)
    }

    @Test
    fun Host_ForEachKindOfUrl() {
        assertEquals("github.com", RemoteUrls.host("https://GitHub.com/me/game.git"))
        assertEquals("github.com", RemoteUrls.host("https://me:t@github.com/me/game.git"))
        assertEquals("host.example:8443", RemoteUrls.host("https://host.example:8443/a.git"))
        assertEquals("github.com", RemoteUrls.host("git@github.com:me/game.git"))
        assertEquals("gitlab.com", RemoteUrls.host("ssh://git@gitlab.com:22/me/game.git"))
        assertNull(RemoteUrls.host("file:///C:/repos/game.git"))
        assertNull(RemoteUrls.host("C:\\repos\\game.git"))
        assertNull(RemoteUrls.host("/srv/game.git"))
        assertTrue(RemoteUrls.isGitHub("https://github.com/me/game.git"))
    }

    @Test
    fun GitCredentials_AreKeptPerHostInTheStore() {
        val store = SessionCredentialStore()
        val creds = GitCredentials(store)
        creds.save("GitHub.com", GitCredential("me", "tok"))
        assertEquals("tok", store.get("skate:git:github.com")?.secret)
        assertEquals("tok", creds.forUrl("https://github.com/me/game.git")?.secret)
        assertNull(creds.forUrl("https://gitlab.com/me/game.git"))
        assertNull(creds.forUrl("file:///C:/x.git"))
        assertFalse(GitCredential("me", "tok").toString().contains("tok"))
        creds.signOut("github.com")
        assertFalse(creds.has("github.com"))
        assertFalse(store.persistent)
    }
}
