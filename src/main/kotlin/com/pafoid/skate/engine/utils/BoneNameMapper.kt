package com.pafoid.skate.engine.utils

object BoneNameMapper {
    fun map(originalName: String): String {
        var name = originalName
        if (name.startsWith("mixamorig:")) {
            name = name.substring("mixamorig:".length)
        } else if (name.startsWith("mixamorig9_")) {
            name = name.substring("mixamorig9_".length)
        } else if (name.startsWith("mixamorig_")) {
            name = name.substring("mixamorig_".length)
        } else if (name.startsWith("mixamorig")) { // Fallback if no separator
             name = name.substring("mixamorig".length)
        }
        return name
    }
}
