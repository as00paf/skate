package com.pafoid.skate.engine.platform.steampipe

import kotlinx.serialization.Serializable

/** Why a [SteamPublishTarget] cannot be uploaded yet. The editor shows each as a translated issue. */
enum class SteamPublishIssue { APP_ID_INVALID, DEPOT_ID_INVALID, DEPOT_ID_SAME_AS_APP_ID, DESCRIPTION_EMPTY, EXCLUSION_INVALID }

/**
 * Where a project uploads on Steam (NET-07, docs/design/NET-07.md §0): the user's app, one Windows depot (Q5), the
 * build description template and the depot file exclusions. Stored in the project file; it holds no credentials.
 * A build is only uploaded, never set live (Q3), so there is no branch.
 *
 * [descriptionTemplate] supports `{project}`, `{version}` and `{date}`; see [describe].
 */
@Serializable
data class SteamPublishTarget(
    val appId: Long = 0,
    val depotId: Long = 0,
    val descriptionTemplate: String = DEFAULT_DESCRIPTION,
    val exclusions: List<String> = DEFAULT_EXCLUSIONS,
) {
    /** Problems blocking an upload, in a stable order; empty when the target is usable. */
    fun issues(): List<SteamPublishIssue> = buildList {
        if (!isSteamId(appId)) add(SteamPublishIssue.APP_ID_INVALID)
        if (!isSteamId(depotId)) add(SteamPublishIssue.DEPOT_ID_INVALID)
        else if (depotId == appId) add(SteamPublishIssue.DEPOT_ID_SAME_AS_APP_ID)
        if (descriptionTemplate.isBlank()) add(SteamPublishIssue.DESCRIPTION_EMPTY)
        if (exclusions.any { !isValidExclusion(it) }) add(SteamPublishIssue.EXCLUSION_INVALID)
    }

    val isValid: Boolean get() = issues().isEmpty()

    /** The build description with its placeholders filled in. Unknown placeholders are kept as typed. */
    fun describe(project: String, version: String, date: String): String =
        descriptionTemplate
            .replace("{project}", project)
            .replace("{version}", version)
            .replace("{date}", date)

    companion object {
        const val DEFAULT_DESCRIPTION = "{project} {version} ({date})"

        /** Dev-only files that must never reach a depot (`steam_appid.txt`, §7) and debug symbols. */
        val DEFAULT_EXCLUSIONS: List<String> = listOf("steam_appid.txt", "*.pdb")

        /** Steam app and depot IDs are unsigned 32-bit and never 0. */
        fun isSteamId(id: Long): Boolean = id in 1..0xFFFF_FFFFL

        /** A depot-relative wildcard: not blank, no quote or control character, not absolute, no `..` segment. */
        fun isValidExclusion(pattern: String): Boolean {
            if (pattern.isBlank() || pattern.any { it == '"' || it.isISOControl() }) return false
            if (pattern.startsWith("/") || pattern.startsWith("\\") || (pattern.length > 1 && pattern[1] == ':')) return false
            return pattern.split('/', '\\').none { it == ".." }
        }

        /** Parses an ID typed in the UI: digits only, in range; null otherwise. */
        fun parseSteamId(text: String): Long? = text.trim().takeIf { t -> t.isNotEmpty() && t.all(Char::isDigit) && t.length <= 10 }
            ?.toLong()?.takeIf(::isSteamId)
    }
}
