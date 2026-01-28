package com.pafoid.skate.engine.animation

import com.google.gson.GsonBuilder
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.scenes.components.ComponentDeserializer
import org.joml.Quaternionf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class BoneOverrideSerializationTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(Component::class.java, ComponentDeserializer())
        .create()

    private val tempFile = File("temp_bone_override.json")

    @AfterEach
    fun tearDown() {
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    @Test
    fun `BoneOverride should serialize to and deserialize from a file correctly`() {
        // Given
        val originalOverride = BoneOverride()
        val boneName1 = "mixamorig9_Hine"
        val rotation1 = Quaternionf().rotateAxis(0.1f, 1f, 0f, 0f)
        val boneName2 = "mixamorig9_Spine"
        val rotation2 = Quaternionf().rotateAxis(0.2f, 0f, 1f, 0f)

        originalOverride.addOverride(boneName1, rotation1)
        originalOverride.addOverride(boneName2, rotation2)

        // When
        val json = gson.toJson(originalOverride)
        tempFile.writeText(json)

        val loadedJson = tempFile.readText()
        val deserializedOverride = gson.fromJson(loadedJson, BoneOverride::class.java)

        // Then
        assertNotNull(deserializedOverride)
        assertEquals(originalOverride.getOverride(boneName1), deserializedOverride.getOverride(boneName1))
        assertEquals(originalOverride.getOverride(boneName2), deserializedOverride.getOverride(boneName2))
        assertNull(deserializedOverride.getOverride("nonExistentBone"))
    }
}

