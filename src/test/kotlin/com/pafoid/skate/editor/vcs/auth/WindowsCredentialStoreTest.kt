package com.pafoid.skate.editor.vcs.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.util.UUID

/** EDT-60 §5: a real round trip through Windows Credential Manager, under a throwaway entry it deletes again. */
@EnabledOnOs(OS.WINDOWS)
class WindowsCredentialStoreTest {
    @Test
    fun PutGetDelete_RoundTrips() {
        val store = WindowsCredentialStore.createOrNull()
        assertNotNull(store)
        store!!
        assertTrue(store.persistent)
        val key = "skate:git:test-${UUID.randomUUID()}"
        try {
            assertNull(store.get(key))
            store.put(key, GitCredential("me", "tök€n-123"))
            assertEquals(GitCredential("me", "tök€n-123"), store.get(key))
            store.put(key, GitCredential("you", "other"))
            assertEquals(GitCredential("you", "other"), store.get(key))
        } finally {
            store.delete(key)
        }
        assertNull(store.get(key))
        // Deleting a missing entry is harmless.
        store.delete(key)
    }

    @Test
    fun PlatformDefault_IsPersistentOnWindows() {
        assertTrue(CredentialStores.platformDefault().persistent)
    }
}
