package com.pafoid.skate.engine.assets

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
        /*if (name.contains(":")) {
            name = name.substringAfter(":")
        }*/

        // Remove the Assimp FBX suffix that gets added to distinguish naming conventions
        if (name.contains("_\$AssimpFbx\$")) {
            name = name.substringBefore("_\$AssimpFbx\$")
        }

        // Only remove underscore suffixes if they appear to be extra identifiers
        // Check if the part after underscore looks like an identifier (numbers, etc.)
        if (name.contains("_")) {
            val parts = name.split("_")
            if (parts.size > 1) {
                // Check if the part after the first underscore looks like extra info
                val suffix = parts.drop(1).joinToString("_")
                if (suffix.matches(Regex("[0-9]+.*"))) { // If suffix starts with numbers
                    name = parts[0] // Just keep the first part
                } else {
                    // Keep the first two parts if there are multiple underscores
                    // This handles cases like "Left_Arm_Part" -> "Left_Arm" rather than just "Left"
                    name = parts.take(2).joinToString("_")
                }
            }
        }

        return name
    }
}