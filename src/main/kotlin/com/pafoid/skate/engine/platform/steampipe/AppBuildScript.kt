package com.pafoid.skate.engine.platform.steampipe

/** One depot of an [AppBuildScript]: everything under the content root, minus [exclusions]. */
data class DepotMapping(val depotId: Long, val exclusions: List<String> = emptyList())

/**
 * A SteamPipe app build script (`app_build_<appid>.vdf`, docs/design/NET-07.md §2), written by [toVdf].
 *
 * It never contains `SetLive`: Skate uploads builds and the user sets them live in Steamworks (Q3). [preview] makes
 * steamcmd scan and report without uploading (`"Preview" "1"`).
 *
 * Values are written without escape sequences, as in Valve's samples (`"ContentRoot" "..\content\"`, with a raw
 * backslash before the closing quote), because steamcmd reads the file without escape processing (⚠ UNVERIFIED, spike
 * NET-07a). So a value must not contain `"` or a control character: [description] is sanitized, and paths that hold
 * them are refused.
 */
data class AppBuildScript(
    val appId: Long,
    val description: String,
    val contentRoot: String,
    val buildOutput: String,
    val depots: List<DepotMapping>,
    val preview: Boolean = false,
) {
    init {
        require(SteamPublishTarget.isSteamId(appId)) { "Invalid Steam app ID: $appId" }
        require(depots.isNotEmpty()) { "A build needs at least one depot" }
        depots.forEach { require(SteamPublishTarget.isSteamId(it.depotId)) { "Invalid Steam depot ID: ${it.depotId}" } }
        require(depots.map { it.depotId }.distinct().size == depots.size) { "Duplicate depot ID" }
        requirePlain("ContentRoot", contentRoot)
        requirePlain("BuildOutput", buildOutput)
        depots.flatMap { it.exclusions }.forEach { require(SteamPublishTarget.isValidExclusion(it)) { "Invalid exclusion: $it" } }
    }

    /** The script text: fixed key order, tabs, `\n` line ends, depots sorted by ID. Same input, same bytes. */
    fun toVdf(): String = buildString {
        line(0, "\"AppBuild\"")
        line(0, "{")
        pair(1, "AppID", appId.toString())
        pair(1, "Desc", sanitizeDescription(description))
        pair(1, "ContentRoot", asDirectory(contentRoot))
        pair(1, "BuildOutput", asDirectory(buildOutput))
        pair(1, "Preview", if (preview) "1" else "0")
        line(1, "\"Depots\"")
        line(1, "{")
        depots.sortedBy { it.depotId }.forEach { depot ->
            line(2, "\"${depot.depotId}\"")
            line(2, "{")
            line(3, "\"FileMapping\"")
            line(3, "{")
            pair(4, "LocalPath", "*")
            pair(4, "DepotPath", ".")
            pair(4, "Recursive", "1")
            line(3, "}")
            depot.exclusions.forEach { pair(3, "FileExclusion", it) }
            line(2, "}")
        }
        line(1, "}")
        line(0, "}")
    }

    /** File name steamcmd conventions use for this script. */
    val fileName: String get() = "app_build_$appId.vdf"

    private fun StringBuilder.line(indent: Int, text: String) {
        repeat(indent) { append('\t') }
        append(text).append('\n')
    }

    private fun StringBuilder.pair(indent: Int, key: String, value: String) = line(indent, "\"$key\" \"$value\"")

    companion object {
        /** Longest description kept; Steamworks shows a short line in the builds list. */
        const val MAX_DESCRIPTION = 200

        /** `"` becomes `'`, control characters become spaces, runs of spaces collapse, trimmed and capped. */
        fun sanitizeDescription(text: String): String =
            text.map { c -> when { c == '"' -> '\''; c.isISOControl() -> ' '; else -> c } }
                .joinToString("")
                .replace(Regex(" {2,}"), " ")
                .trim()
                .take(MAX_DESCRIPTION)

        /** A directory path ending with a separator, as Valve's samples write them. */
        fun asDirectory(path: String): String = if (path.endsWith("\\") || path.endsWith("/")) path else "$path\\"

        private fun requirePlain(key: String, value: String) {
            require(value.isNotBlank()) { "$key is empty" }
            require(value.none { it == '"' || it.isISOControl() }) { "$key contains a quote or control character" }
        }
    }
}
