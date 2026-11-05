package com.pafoid.skate.editor.project

import com.pafoid.skate.engine.core.StringManager
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

data class RecentProjectDisplayInfo(
    val name: String,
    val path: String,
    val lastOpened: Long,
    val exists: Boolean
) {
    /** "Just now", "5 minutes ago", ... in the editor language; older than a week shows the date. */
    fun getLastOpenedString(strings: StringManager, now: Long = System.currentTimeMillis()): String {
        val diff = now - lastOpened

        return when {
            diff < 60000 -> strings.getString("project.last_opened.just_now")
            diff < 3600000 -> (diff / 60000).toInt().let { strings.getQuantityString("project.last_opened.minutes", it, it) }
            diff < 86400000 -> (diff / 3600000).toInt().let { strings.getQuantityString("project.last_opened.hours", it, it) }
            diff < 604800000 -> (diff / 86400000).toInt().let { strings.getQuantityString("project.last_opened.days", it, it) }
            else -> {
                val instant = Instant.ofEpochMilli(lastOpened)
                val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.forLanguageTag(strings.locale))
                    .withZone(ZoneId.systemDefault())
                formatter.format(instant)
            }
        }
    }
}
