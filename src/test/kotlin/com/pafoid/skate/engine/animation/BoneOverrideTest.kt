package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.ecs.components.BoneOverride
import org.joml.Quaternionf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BoneOverrideTest {

    @Test
    fun `addOverride should store and getOverride should retrieve the correct quaternion`() {
        // Given
        val boneOverride = BoneOverride()
        val boneName = "mixamorig9_Spine"
        val rotation = Quaternionf().rotateAxis(1.57f, 0f, 1f, 0f) // 90 degrees on Y

        // When
        boneOverride.addOverride(boneName, rotation)
        val retrievedRotation = boneOverride.getOverride(boneName)

        // Then
        assertNotNull(retrievedRotation)
        assertEquals(rotation, retrievedRotation)
    }

    @Test
    fun `getOverride should return null for a bone with no override`() {
        // Given
        val boneOverride = BoneOverride()
        val boneName = "mixamorig9_Spine"

        // When
        val retrievedRotation = boneOverride.getOverride(boneName)

        // Then
        assertNull(retrievedRotation)
    }
}
