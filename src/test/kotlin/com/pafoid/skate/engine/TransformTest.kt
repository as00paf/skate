package com.pafoid.skate.engine

import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.toMatrix
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TransformTest {

    @Test
    fun `test identity transform matrix`() {
        val t = Transform()
        val matrix = t.toMatrix()
        
        val expected = Matrix4f().identity()
        assertMatrixEquals(expected, matrix)
    }

    @Test
    fun `test translation in transform matrix`() {
        val t = Transform(translation = Vector3f(10f, 20f, 30f))
        val matrix = t.toMatrix()
        
        val expected = Matrix4f().translation(10f, 20f, 30f)
        assertMatrixEquals(expected, matrix)
    }

    @Test
    fun `test rotation in transform matrix`() {
        val t = Transform(rotation = Vector3f(90f, 0f, 0f))
        val matrix = t.toMatrix()
        
        val expected = Matrix4f().rotate(Math.toRadians(90.0).toFloat(), 1f, 0f, 0f)
        assertMatrixEquals(expected, matrix)
    }

    @Test
    fun `test complex transform matrix`() {
        val t = Transform(
            translation = Vector3f(1f, 2f, 3f),
            rotation = Vector3f(0f, 90f, 0f),
            scale = Vector3f(2f, 2f, 2f)
        )
        val matrix = t.toMatrix()
        
        val expected = Matrix4f()
            .translate(1f, 2f, 3f)
            .rotate(Math.toRadians(90.0).toFloat(), 0f, 1f, 0f)
            .scale(2f, 2f, 2f)
            
        assertMatrixEquals(expected, matrix)
    }

    private fun assertMatrixEquals(expected: Matrix4f, actual: Matrix4f, epsilon: Float = 1e-5f) {
        assertEquals(expected.m00(), actual.m00(), epsilon)
        assertEquals(expected.m01(), actual.m01(), epsilon)
        assertEquals(expected.m02(), actual.m02(), epsilon)
        assertEquals(expected.m03(), actual.m03(), epsilon)
        
        assertEquals(expected.m10(), actual.m10(), epsilon)
        assertEquals(expected.m11(), actual.m11(), epsilon)
        assertEquals(expected.m12(), actual.m12(), epsilon)
        assertEquals(expected.m13(), actual.m13(), epsilon)
        
        assertEquals(expected.m20(), actual.m20(), epsilon)
        assertEquals(expected.m21(), actual.m21(), epsilon)
        assertEquals(expected.m22(), actual.m22(), epsilon)
        assertEquals(expected.m23(), actual.m23(), epsilon)
        
        assertEquals(expected.m30(), actual.m30(), epsilon)
        assertEquals(expected.m31(), actual.m31(), epsilon)
        assertEquals(expected.m32(), actual.m32(), epsilon)
        assertEquals(expected.m33(), actual.m33(), epsilon)
    }
}
