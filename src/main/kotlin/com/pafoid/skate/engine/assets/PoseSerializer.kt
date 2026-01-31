package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.animation.BoneOverride
import com.pafoid.skate.engine.utils.serialization.Serializer
import kotlinx.serialization.encodeToString
import java.io.File

object PoseSerializer {

    fun savePose(boneOverride: BoneOverride, filePath: String) {
        val json = Serializer.json.encodeToString(boneOverride)
        File(filePath).writeText(json)
    }

    fun loadPose(filePath: String): BoneOverride? {
        val file = File(filePath)
        if (!file.exists()) {
            println("Warning: Pose file not found: $filePath")
            return null
        }
        val json = file.readText()
        return Serializer.json.decodeFromString(json)
    }
}
