package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.AssetRegistry
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Filesystem moves that keep an asset's `.meta` sidecar (and so its GUID) next to the file. */
internal object AssetFileOps {
    /** Moves [from] to [to] with its sidecar. Through [registry] when given so its index follows the move. */
    fun move(from: File, to: File, registry: AssetRegistry?): Boolean {
        if (!from.exists() || to.exists()) return false
        if (registry != null && registry.move(from, to)) return true
        if (registry != null) return false
        to.parentFile?.mkdirs()
        val sidecar = AssetMeta.sidecarOf(from)
        val target = AssetMeta.sidecarOf(to)
        if (sidecar.exists() && target.exists()) return false
        if (!moveRaw(from, to)) return false
        if (sidecar.exists()) moveRaw(sidecar, target)
        return true
    }

    private fun moveRaw(from: File, to: File): Boolean {
        if (from.renameTo(to)) return true
        return try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Moves a file and its sidecar to [trash]; returns false (leaving both in place) if the file cannot move. */
    fun trash(file: File, trash: File): Boolean {
        if (!moveRaw(file, trash)) return false
        val sidecar = AssetMeta.sidecarOf(file)
        if (sidecar.exists()) moveRaw(sidecar, AssetMeta.sidecarOf(trash))
        return true
    }

    fun restore(trash: File, file: File, registry: AssetRegistry?): Boolean {
        if (!trash.exists()) return false
        if (!moveRaw(trash, file)) return false
        val sidecar = AssetMeta.sidecarOf(trash)
        if (sidecar.exists()) moveRaw(sidecar, AssetMeta.sidecarOf(file))
        if (file.isFile && registry != null) registry.register(file)
        return true
    }
}
