package com.pafoid.skate.engine.utils

import org.joml.Matrix4f
import org.lwjgl.assimp.AIMatrix4x4
import org.lwjgl.assimp.AIScene
import org.lwjgl.assimp.Assimp

fun AIMatrix4x4.toJomlMatrix(): Matrix4f {
    return Matrix4f(
        this.a1(), this.b1(), this.c1(), this.d1(),
        this.a2(), this.b2(), this.c2(), this.d2(),
        this.a3(), this.b3(), this.c3(), this.d3(),
        this.a4(), this.b4(), this.c4(), this.d4()
    )
}

fun AIScene.printMetadata(from: String) {
    val metadata = mMetaData() ?: run {
        println("No scene metadata")
        return
    }

    val numProperties = metadata.mNumProperties()
    val keys = metadata.mKeys()
    val values = metadata.mValues()

    for (i in 0 until numProperties) {
        val key = keys[i].dataString()
        val entry = values[i]

        print("$from Metadata [$key] = ")

        when (entry.mType()) {
            Assimp.AI_BOOL ->
                println(entry.mData(1).get(0) != 0.toByte())

            Assimp.AI_INT32 ->
                println(entry.mData(4).getInt(0))

            Assimp.AI_UINT64 ->
                println(entry.mData(8).getLong(0))

            Assimp.AI_FLOAT ->
                println(entry.mData(4).getFloat(0))

            Assimp.AI_DOUBLE ->
                println(entry.mData(8).getDouble(0))

            /*Assimp.AI_AISTRING -> {
                val aiStr = AIString.create(entry.mData())
                entry.mData(aiStr.length)
                println(aiStr.dataString())
            }

            Assimp.AI_AIVECTOR3D -> {
                val vec = AIVector3D.create(entry.mData())
                println("(${vec.x()}, ${vec.y()}, ${vec.z()})")
            }*/

            else -> println("Unknown metadata type")
        }
    }
}
