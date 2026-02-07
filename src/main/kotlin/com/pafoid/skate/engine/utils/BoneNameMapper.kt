package com.pafoid.skate.engine.utils

object BoneNameMapper {
    fun map(originalName: String): String {
        var name = originalName
        
        // Remove common Mixamo prefixes and namespaces
        val prefixes = listOf("mixamorig:", "mixamorig9_", "mixamorig9:", "mixamorig_", "mixamorig", "9:")
        
        for (prefix in prefixes) {
            if (name.startsWith(prefix, ignoreCase = true)) {
                name = name.substring(prefix.length)
                break
            }
        }
        
        // If there's still a colon (namespace), take everything after it
        if (name.contains(":")) {
            name = name.substringAfter(":")
        }
        if (name.contains("_")) {
            name = name.substringBefore("_")
        }

        return name
    }
}
