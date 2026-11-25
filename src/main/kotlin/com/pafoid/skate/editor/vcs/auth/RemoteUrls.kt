package com.pafoid.skate.editor.vcs.auth

import java.net.URLDecoder

/**
 * EDT-60 §5: remote URLs are stored without credentials. [strip] takes `https://user:token@host/…` apart so the
 * editor can store the URL clean and offer to keep the credentials in the OS store instead.
 */
object RemoteUrls {
    data class Parsed(val url: String, val username: String?, val secret: String?) {
        val hadCredentials: Boolean get() = username != null || secret != null
        override fun toString(): String = "Parsed($url, $username, ${if (secret == null) "null" else "***"})"
    }

    /** [raw] trimmed, with any `user[:secret]@` removed from an http(s) URL. Other URLs come back unchanged. */
    fun strip(raw: String): Parsed {
        val url = raw.trim()
        val scheme = url.indexOf("://")
        if (scheme < 0 || !isHttp(url)) return Parsed(url, null, null)
        val authorityStart = scheme + 3
        val authorityEnd = url.indexOf('/', authorityStart).let { if (it < 0) url.length else it }
        val at = url.lastIndexOf('@', authorityEnd - 1)
        if (at < authorityStart) return Parsed(url, null, null)
        val info = url.substring(authorityStart, at)
        val colon = info.indexOf(':')
        val user = decode(if (colon < 0) info else info.substring(0, colon))
        val secret = if (colon < 0) null else decode(info.substring(colon + 1))
        return Parsed(url.substring(0, authorityStart) + url.substring(at + 1), user.ifEmpty { null }, secret?.ifEmpty { null })
    }

    fun isHttp(url: String): Boolean = url.trim().lowercase().let { it.startsWith("https://") || it.startsWith("http://") }

    /**
     * The host credentials are kept under: `host` or `host:port` for http(s), the host of an `ssh://` or scp-like
     * `git@host:path` URL, null for local paths and `file://`.
     */
    fun host(url: String): String? {
        val u = strip(url).url
        val scheme = u.indexOf("://")
        if (scheme >= 0) {
            if (u.substring(0, scheme).lowercase() == "file") return null
            val start = scheme + 3
            val end = u.indexOf('/', start).let { if (it < 0) u.length else it }
            val authority = u.substring(start, end).substringAfterLast('@')
            val host = if (isHttp(u)) authority else authority.substringBefore(':')
            return host.lowercase().ifEmpty { null }
        }
        // scp-like: [user@]host:path (not a Windows drive letter such as C:\…).
        val colon = u.indexOf(':')
        if (colon > 1 && !u.substring(0, colon).contains('/') && !u.substring(0, colon).contains('\\')) {
            return u.substring(0, colon).substringAfterLast('@').lowercase().ifEmpty { null }
        }
        return null
    }

    fun isGitHub(url: String): Boolean = host(url) == GITHUB_HOST

    const val GITHUB_HOST = "github.com"

    private fun decode(s: String): String = URLDecoder.decode(s.replace("+", "%2B"), Charsets.UTF_8)
}
