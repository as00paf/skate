package com.pafoid.skate.editor.vcs.auth

import java.util.concurrent.ConcurrentHashMap

/** A username and a secret (token or password) for one Git host. [toString] never shows the secret. */
data class GitCredential(val username: String, val secret: String) {
    override fun toString(): String = "GitCredential($username, ***)"
}

/**
 * EDT-60 §5: where Git credentials live. Only the OS credential store is [persistent]; nothing secret is ever written
 * to project files, `.git/config`, a remote URL, `editor.config` or a log.
 */
interface CredentialStore {
    /** False for the in-memory fallback: credentials are forgotten when the editor closes (the UI says so). */
    val persistent: Boolean

    fun get(key: String): GitCredential?
    fun put(key: String, credential: GitCredential)
    fun delete(key: String)
}

/** The fallback when no OS store is available, and the store used by tests: memory only, for this session. */
class SessionCredentialStore : CredentialStore {
    private val entries = ConcurrentHashMap<String, GitCredential>()
    override val persistent: Boolean = false
    override fun get(key: String): GitCredential? = entries[key]
    override fun put(key: String, credential: GitCredential) {
        entries[key] = credential
    }
    override fun delete(key: String) {
        entries.remove(key)
    }
}

object CredentialStores {
    /** Windows Credential Manager when it can be reached, else [SessionCredentialStore]. */
    fun platformDefault(): CredentialStore = WindowsCredentialStore.createOrNull() ?: SessionCredentialStore()
}

/** Git credentials per host, under the OS-store entry `skate:git:<host>` (EDT-60 §5). */
class GitCredentials(val store: CredentialStore) {
    fun forUrl(url: String): GitCredential? = RemoteUrls.host(url)?.let { store.get(key(it)) }

    fun has(host: String): Boolean = store.get(key(host)) != null

    fun save(host: String, credential: GitCredential) = store.put(key(host), credential)

    fun signOut(host: String) = store.delete(key(host))

    companion object {
        fun key(host: String): String = "skate:git:${host.lowercase()}"
    }
}
