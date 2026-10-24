package com.pafoid.skate.engine.assets.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector2i
import org.joml.Vector3f
import org.joml.Vector4f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JomlSerializersTest {
    private fun <T> roundTrip(serializer: KSerializer<T>, value: T): T =
        Json.decodeFromString(serializer, Json.encodeToString(serializer, value))

    @Test
    fun JomlSerializers_Vector2i_RoundTrips() {
        assertEquals(Vector2i(3, -4), roundTrip(Vector2iSerializer, Vector2i(3, -4)))
    }

    @Test
    fun JomlSerializers_Vector2f_RoundTrips() {
        assertEquals(Vector2f(1.5f, -2.25f), roundTrip(Vector2fSerializer, Vector2f(1.5f, -2.25f)))
    }

    @Test
    fun JomlSerializers_Vector3f_RoundTrips() {
        assertEquals(Vector3f(1f, 2f, 3f), roundTrip(Vector3fSerializer, Vector3f(1f, 2f, 3f)))
    }

    @Test
    fun JomlSerializers_Vector4f_RoundTrips() {
        assertEquals(Vector4f(1f, 2f, 3f, 4f), roundTrip(Vector4fSerializer, Vector4f(1f, 2f, 3f, 4f)))
    }

    @Test
    fun JomlSerializers_Quaternionf_RoundTrips() {
        val q = Quaternionf().rotateXYZ(0.3f, 0.5f, 0.7f)
        assertEquals(q, roundTrip(QuaternionfSerializer, q))
    }

    @Test
    fun JomlSerializers_Matrix4f_RoundTripsAllElements() {
        val m = Matrix4f().translation(1f, 2f, 3f).rotateY(0.4f).scale(2f, 3f, 4f)
        assertEquals(m, roundTrip(Matrix4fSerializer, m))
    }

    @Test
    fun JomlSerializers_Vector3f_MissingFieldsDefaultToZero() {
        assertEquals(Vector3f(1f, 0f, 0f), Json.decodeFromString(Vector3fSerializer, "{\"x\":1.0}"))
    }

    @Test
    fun JomlSerializers_Quaternionf_MissingFieldsDefaultToIdentity() {
        assertEquals(Quaternionf(), Json.decodeFromString(QuaternionfSerializer, "{}"))
    }
}
