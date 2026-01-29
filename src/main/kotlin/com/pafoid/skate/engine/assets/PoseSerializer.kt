package com.pafoid.skate.engine.assets

import com.google.gson.GsonBuilder
import com.pafoid.skate.engine.animation.BoneOverride
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.scenes.components.ComponentDeserializer
import java.io.File

object PoseSerializer {

    // TODO: take from injection
    private val gson = GsonBuilder()
        .registerTypeAdapter(Component::class.java, ComponentDeserializer())
        .setPrettyPrinting()
        .create()

    fun savePose(boneOverride: BoneOverride, filePath: String) {
        val json = gson.toJson(boneOverride)
        File(filePath).writeText(json)
    }

    fun loadPose(filePath: String): BoneOverride? {
        val file = File(filePath)
        if (!file.exists()) {
            println("Warning: Pose file not found: $filePath")
            return null
        }
        val json = file.readText()
        return gson.fromJson(json, BoneOverride::class.java)
    }
}
