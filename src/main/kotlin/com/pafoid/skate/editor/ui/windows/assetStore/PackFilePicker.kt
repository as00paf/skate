package com.pafoid.skate.editor.ui.windows.assetStore

import com.pafoid.skate.engine.core.StringManager
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.io.File

/** Asks the user for a downloaded pack zip ("Import downloaded pack…", AST-16 slice d). */
fun interface PackFilePicker {
    fun pick(title: String): File?
}

/** Native open dialog filtered on `*.zip`; null when cancelled. */
class TinyPackFilePicker(private val strings: StringManager) : PackFilePicker {
    override fun pick(title: String): File? {
        val pattern = MemoryUtil.memUTF8("*.zip")
        val filters = MemoryUtil.memAllocPointer(1)
        filters.put(0, pattern)
        return try {
            TinyFileDialogs.tinyfd_openFileDialog(title, null, filters, strings.getString("assetstore.pack.dialog_filter"), false)
                ?.let(::File)?.takeIf { it.isFile }
        } finally {
            MemoryUtil.memFree(pattern)
            MemoryUtil.memFree(filters)
        }
    }
}
