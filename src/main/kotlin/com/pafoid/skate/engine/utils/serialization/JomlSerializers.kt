package com.pafoid.skate.engine.utils.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

object Vector2fSerializer : KSerializer<Vector2f> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Vector2f") {
        element<Float>("x")
        element<Float>("y")
    }

    override fun serialize(encoder: Encoder, value: Vector2f) {
        encoder.encodeStructure(descriptor) {
            encodeFloatElement(descriptor, 0, value.x)
            encodeFloatElement(descriptor, 1, value.y)
        }
    }

    override fun deserialize(decoder: Decoder): Vector2f {
        return decoder.decodeStructure(descriptor) {
            var x = 0f
            var y = 0f
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> x = decodeFloatElement(descriptor, 0)
                    1 -> y = decodeFloatElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            Vector2f(x, y)
        }
    }
}

object Vector3fSerializer : KSerializer<Vector3f> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Vector3f") {
        element<Float>("x")
        element<Float>("y")
        element<Float>("z")
    }

    override fun serialize(encoder: Encoder, value: Vector3f) {
        encoder.encodeStructure(descriptor) {
            encodeFloatElement(descriptor, 0, value.x)
            encodeFloatElement(descriptor, 1, value.y)
            encodeFloatElement(descriptor, 2, value.z)
        }
    }

    override fun deserialize(decoder: Decoder): Vector3f {
        return decoder.decodeStructure(descriptor) {
            var x = 0f
            var y = 0f
            var z = 0f
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> x = decodeFloatElement(descriptor, 0)
                    1 -> y = decodeFloatElement(descriptor, 1)
                    2 -> z = decodeFloatElement(descriptor, 2)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            Vector3f(x, y, z)
        }
    }
}

object Vector4fSerializer : KSerializer<Vector4f> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Vector4f") {
        element<Float>("x")
        element<Float>("y")
        element<Float>("z")
        element<Float>("w")
    }

    override fun serialize(encoder: Encoder, value: Vector4f) {
        encoder.encodeStructure(descriptor) {
            encodeFloatElement(descriptor, 0, value.x)
            encodeFloatElement(descriptor, 1, value.y)
            encodeFloatElement(descriptor, 2, value.z)
            encodeFloatElement(descriptor, 3, value.w)
        }
    }

    override fun deserialize(decoder: Decoder): Vector4f {
        return decoder.decodeStructure(descriptor) {
            var x = 0f
            var y = 0f
            var z = 0f
            var w = 0f
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> x = decodeFloatElement(descriptor, 0)
                    1 -> y = decodeFloatElement(descriptor, 1)
                    2 -> z = decodeFloatElement(descriptor, 2)
                    3 -> w = decodeFloatElement(descriptor, 3)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            Vector4f(x, y, z, w)
        }
    }
}

object QuaternionfSerializer : KSerializer<Quaternionf> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Quaternionf") {
        element<Float>("x")
        element<Float>("y")
        element<Float>("z")
        element<Float>("w")
    }

    override fun serialize(encoder: Encoder, value: Quaternionf) {
        encoder.encodeStructure(descriptor) {
            encodeFloatElement(descriptor, 0, value.x)
            encodeFloatElement(descriptor, 1, value.y)
            encodeFloatElement(descriptor, 2, value.z)
            encodeFloatElement(descriptor, 3, value.w)
        }
    }

    override fun deserialize(decoder: Decoder): Quaternionf {
        return decoder.decodeStructure(descriptor) {
            var x = 0f
            var y = 0f
            var z = 0f
            var w = 1f
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> x = decodeFloatElement(descriptor, 0)
                    1 -> y = decodeFloatElement(descriptor, 1)
                    2 -> z = decodeFloatElement(descriptor, 2)
                    3 -> w = decodeFloatElement(descriptor, 3)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            Quaternionf(x, y, z, w)
        }
    }
}

object Matrix4fSerializer : KSerializer<Matrix4f> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Matrix4f") {
        for (i in 0 until 16) {
            element<Float>("m$i")
        }
    }

    override fun serialize(encoder: Encoder, value: Matrix4f) {
        encoder.encodeStructure(descriptor) {
            val buffer = FloatArray(16)
            value.get(buffer)
            for (i in 0 until 16) {
                encodeFloatElement(descriptor, i, buffer[i])
            }
        }
    }

    override fun deserialize(decoder: Decoder): Matrix4f {
        return decoder.decodeStructure(descriptor) {
            val buffer = FloatArray(16)
            while (true) {
                val index = decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) break
                if (index in 0 until 16) {
                    buffer[index] = decodeFloatElement(descriptor, index)
                } else {
                    error("Unexpected index: $index")
                }
            }
            Matrix4f().set(buffer)
        }
    }
}
