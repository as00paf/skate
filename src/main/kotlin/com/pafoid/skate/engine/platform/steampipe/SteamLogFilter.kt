package com.pafoid.skate.engine.platform.steampipe

/**
 * Redacts steamcmd output before it is shown, logged or saved (docs/design/NET-07.md §5.4): the account name becomes
 * its first two characters followed by `***`, and any run of 32 or more token characters (login tokens, hex keys)
 * becomes `[redacted]`. Skate never keeps the raw output.
 */
class SteamLogFilter(username: String?) {
    private val userPattern: Regex? = username?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { Regex(Regex.escape(it), RegexOption.IGNORE_CASE) }
    private val userMask: String = username?.trim()?.take(2).orEmpty() + "***"

    fun filter(line: String): String {
        var out = TOKEN.replace(line, REDACTED)
        if (userPattern != null) out = userPattern.replace(out, Regex.escapeReplacement(userMask))
        return out
    }

    companion object {
        const val REDACTED = "[redacted]"
        private val TOKEN = Regex("[A-Za-z0-9+/=_-]{32,}")
    }
}
