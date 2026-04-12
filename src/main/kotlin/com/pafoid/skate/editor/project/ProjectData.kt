package com.pafoid.skate.editor.project

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
    fun getLastOpenedString(): String {
        val now = System.currentTimeMillis()
        val diff = now - lastOpened

        return when {
            diff < 60000 -> "Just now"
            diff < 3600000 -> "${diff / 60000} minutes ago"
            diff < 86400000 -> "${diff / 3600000} hours ago"
            diff < 604800000 -> "${diff / 86400000} days ago"
            else -> {
                val instant = Instant.ofEpochMilli(lastOpened)
                val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault())
                    .withZone(ZoneId.systemDefault())
                formatter.format(instant)
            }
        }
    }
}
