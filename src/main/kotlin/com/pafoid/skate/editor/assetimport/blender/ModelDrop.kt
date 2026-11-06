package com.pafoid.skate.editor.assetimport.blender

import com.pafoid.skate.editor.data.FileType
import java.io.File

/** What a file dropped on the viewport spawns (AST-19): a model as-is, a `.blend` as its imported GLB. */
object ModelDrop {
    /** The model file to load for [path], or null when it is not a model or the `.blend` is not imported yet. */
    fun modelFor(path: String): String? {
        val file = File(path)
        return when {
            BlenderOutputs.isBlend(file) -> BlenderOutputs.mainFile(file)?.path
            file.extension.lowercase() in FileType.MODEL_3D.extensions -> path
            else -> null
        }
    }
}
