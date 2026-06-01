package com.pafoid.skate.engine.assets.serialization

import com.pafoid.skate.engine.assets.data.models.animations.BoneOverride
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.data.LogLevel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class PoseSerializer: KoinComponent {
    private val serializer: Serializer by inject()
    private val logger: LoggerService by inject()

    fun savePose(boneOverride: BoneOverride, filePath: String) {
        val json = serializer.encode(boneOverride)
        File(filePath).writeText(json)
    }

    fun loadPose(filePath: String): BoneOverride? {
        val file = File(filePath)
        if (!file.exists()) {
            logger.log("Warning: Pose file not found: $filePath", LogLevel.ERROR)
            return null
        }
        val json = file.readText()
        return serializer.decode(json)
    }
}