package com.pafoid.skate.engine.assets.loaders

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

fun AIScene.printMetadata(from: String, log: (String) -> Unit) {
    val metadata = mMetaData() ?: run {
        log("No scene metadata")
        return
    }

    val keys = metadata.mKeys()
    val values = metadata.mValues()

    for (i in 0 until metadata.mNumProperties()) {
        val key = keys[i].dataString()
        val entry = values[i]
        val value: String = when (entry.mType()) {
            Assimp.AI_BOOL -> (entry.mData(1).get(0) != 0.toByte()).toString()
            Assimp.AI_INT32 -> entry.mData(4).getInt(0).toString()
            Assimp.AI_UINT64 -> entry.mData(8).getLong(0).toString()
            Assimp.AI_FLOAT -> entry.mData(4).getFloat(0).toString()
            Assimp.AI_DOUBLE -> entry.mData(8).getDouble(0).toString()
            else -> "Unknown metadata type"
        }
        log("$from Metadata [$key] = $value")
    }
}
