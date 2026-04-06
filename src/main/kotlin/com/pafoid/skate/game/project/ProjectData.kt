package com.pafoid.skate.game.project

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

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
