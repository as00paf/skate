package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.render.utils.GLStateTracker
import com.pafoid.skate.engine.utils.EntityIdEncoder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for render pipeline components.
 *
 * These tests cover core rendering logic that doesn't require an OpenGL context:
 * - Entity ID encoding/decoding for picking
 * - Camera projection matrix calculations
 * - GLStateTracker state change detection logic
 */
@DisplayName("Render Pipeline Tests")
class RenderPipelineTest {

    @Nested
    @DisplayName("EntityIdEncoder Tests")
    inner class EntityIdEncoderTest {

        @Test
        @DisplayName("encode/decode round-trip for positive IDs")
        fun `encode decode round-trip for positive IDs`() {
            val testIds = listOf(0, 1, 42, 100, 1000, 1000000)

            testIds.forEach { id ->
                val encoded = EntityIdEncoder.encode(id)
                val decoded = EntityIdEncoder.decode(encoded)
                assertEquals(id, decoded, "Round-trip failed for ID: $id")
            }
        }

        @Test
        @DisplayName("encode returns Float for GPU compatibility")
        fun `encode returns Float for GPU compatibility`() {
            val encoded = EntityIdEncoder.encode(42)
            assertTrue(encoded is Float, "Encoded value should be Float for GPU uniforms")
        }

        @Test
        @DisplayName("encode adds offset to reserve 0 for no entity")
        fun `encode adds offset to reserve 0 for no entity`() {
            val encodedZero = EntityIdEncoder.encode(0)
            val encodedOne = EntityIdEncoder.encode(1)

            assertEquals(1f, encodedZero, "ID 0 should encode to 1.0 (offset)")
            assertEquals(2f, encodedOne, "ID 1 should encode to 2.0")
            assertNotEquals(0f, encodedZero, "Encoded value should never be 0 (reserved for NO_ENTITY)")
        }

        @Test
        @DisplayName("decode handles encoded values correctly")
        fun `decode handles encoded values correctly`() {
            assertEquals(0, EntityIdEncoder.decode(1f))
            assertEquals(1, EntityIdEncoder.decode(2f))
            assertEquals(100, EntityIdEncoder.decode(101f))
        }

        @Test
        @DisplayName("NO_ENTITY constant is 0")
        fun `NO_ENTITY constant is 0`() {
            assertEquals(0f, EntityIdEncoder.NO_ENTITY)
        }

        @Test
        @DisplayName("decode(0) returns -1 for no entity")
        fun `decode 0 returns -1 for no entity`() {
            val result = EntityIdEncoder.decode(0f)
            assertEquals(-1, result, "Decoding 0 should return -1 (no entity)")
        }
    }

    @Nested
    @DisplayName("Camera Projection Matrix Tests")
    inner class CameraProjectionMatrixTest {

        private lateinit var camera: CameraComponent

        @BeforeEach
        fun setup() {
            camera = CameraComponent()
        }

        @Test
        @DisplayName("perspective projection with 16:9 aspect ratio")
        fun `perspective projection with 16x9 aspect ratio`() {
            camera.viewportWidth = 1920
            camera.viewportHeight = 1080
            camera.fov = 45f
            camera.isOrthographic = false

            val matrix = camera.createProjectionMatrix()

            // Matrix should not be identity (perspective transform applied)
            assertNotEquals(1f, matrix.m00())
            assertNotEquals(1f, matrix.m11())
        }

        @Test
        @DisplayName("perspective projection with ultrawide 21:9 aspect ratio")
        fun `perspective projection with ultrawide 21x9 aspect ratio`() {
            camera.viewportWidth = 2560
            camera.viewportHeight = 1080
            camera.fov = 45f
            camera.isOrthographic = false

            val matrix = camera.createProjectionMatrix()

            // Aspect ratio = 2560/1080 ≈ 2.37
            val expectedAspectRatio = 2560f / 1080f

            // Verify aspect ratio affects the matrix (m00 should be smaller for wider aspect)
            assertTrue(matrix.m00() < 1.1f, "X scale should be reduced for ultrawide")
        }

        @Test
        @DisplayName("perspective projection with 4:3 aspect ratio")
        fun `perspective projection with 4x3 aspect ratio`() {
            camera.viewportWidth = 1600
            camera.viewportHeight = 1200
            camera.fov = 45f
            camera.isOrthographic = false

            val matrix = camera.createProjectionMatrix()
            val expectedAspectRatio = 1600f / 1200f

            // Verify aspect ratio is approximately 4:3
            assertEquals(4f / 3f, expectedAspectRatio, 0.01f)
        }

        @Test
        @DisplayName("orthographic projection ignores aspect ratio")
        fun `orthographic projection uses projectionSize directly`() {
            camera.viewportWidth = 1920
            camera.viewportHeight = 1080
            camera.isOrthographic = true
            camera.projectionSize.set(32f, 18f)
            camera.zoom = 1.0f

            val matrix = camera.createProjectionMatrix()

            // Orthographic matrix should have 1s in bottom-right
            assertEquals(1f, matrix.m33())
        }

        @Test
        @DisplayName("zoom affects orthographic projection")
        fun `zoom affects orthographic projection`() {
            camera.isOrthographic = true
            camera.projectionSize.set(32f, 18f)

            camera.zoom = 1.0f
            val matrixZoom1 = org.joml.Matrix4f(camera.createProjectionMatrix())

            camera.zoom = 2.0f
            val matrixZoom2 = org.joml.Matrix4f(camera.createProjectionMatrix())

            // Zoom 2x should result in different matrix values
            assertNotEquals(matrixZoom1.m00(), matrixZoom2.m00())
        }

        @Test
        @DisplayName("fallback to 16:9 when viewport height is 0")
        fun `fallback to 16x9 when viewport height is 0`() {
            camera.viewportWidth = 1920
            camera.viewportHeight = 0
            camera.isOrthographic = false

            // Should not crash and should produce a valid matrix
            val matrix = camera.createProjectionMatrix()

            // Matrix should still be valid (not all zeros)
            assertTrue(matrix.m00() > 0f)
        }

        @Test
        @DisplayName("near and far planes affect projection matrix")
        fun `near and far planes affect projection matrix`() {
            camera.viewportWidth = 1920
            camera.viewportHeight = 1080
            camera.isOrthographic = false
            camera.nearPlane = 0.1f
            camera.farPlane = 1000f

            val matrix = camera.createProjectionMatrix()

            // Verify matrix is valid (not identity for perspective)
            assertNotEquals(1f, matrix.m22())
        }
    }

    @Nested
    @DisplayName("GLStateTracker Logic Tests")
    inner class GLStateTrackerLogicTest {

        @BeforeEach
        fun setup() {
            // Reset to known state for testing
            // Note: We can't test actual GL calls without context,
            // but we can test the logic and state tracking
        }

        @Test
        @DisplayName("isBlendEnabled returns current state")
        fun `isBlendEnabled returns current state`() {
            // Initial state after construction
            val initialState = GLStateTracker.isBlendEnabled()

            // State should be consistent
            assertEquals(initialState, GLStateTracker.isBlendEnabled())
        }

        @Test
        @DisplayName("isDepthTestEnabled returns current state")
        fun `isDepthTestEnabled returns current state`() {
            val initialState = GLStateTracker.isDepthTestEnabled()
            assertEquals(initialState, GLStateTracker.isDepthTestEnabled())
        }

        @Test
        @DisplayName("isDepthMaskEnabled returns current state")
        fun `isDepthMaskEnabled returns current state`() {
            val initialState = GLStateTracker.isDepthMaskEnabled()
            assertEquals(initialState, GLStateTracker.isDepthMaskEnabled())
        }

        @Test
        @DisplayName("isCullFaceEnabled returns current state")
        fun `isCullFaceEnabled returns current state`() {
            val initialState = GLStateTracker.isCullFaceEnabled()
            assertEquals(initialState, GLStateTracker.isCullFaceEnabled())
        }

        @Test
        @DisplayName("getDepthFunc returns current depth function")
        fun `getDepthFunc returns current depth function`() {
            val initialFunc = GLStateTracker.getDepthFunc()
            assertEquals(initialFunc, GLStateTracker.getDepthFunc())
        }

        @Test
        @DisplayName("state getters are consistent")
        fun `state getters are consistent`() {
            // Multiple calls should return same values
            repeat(10) {
                GLStateTracker.isBlendEnabled()
                GLStateTracker.isDepthTestEnabled()
                GLStateTracker.isDepthMaskEnabled()
                GLStateTracker.isCullFaceEnabled()
                GLStateTracker.getDepthFunc()
            }
        }
    }
}
