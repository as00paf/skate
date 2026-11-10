package com.pafoid.skate.editor.ui.windows

import java.io.File

/**
 * The project file the Properties window inspects instead of the selected object (EDT-66): set when a file with
 * import settings is selected in the Project window, dropped as soon as the scene selection changes, another file
 * is selected or the file is gone. Pure state; main thread.
 */
class InspectedFile(private val exists: (File) -> Boolean = File::isFile) {
    private var file: File? = null
    private var objectAtSelect: Any? = null

    /** [currentObject] is the scene selection at the time of the click; changing it later ends the inspection. */
    fun select(file: File, currentObject: Any?) {
        this.file = file
        objectAtSelect = currentObject
    }

    fun clear() {
        file = null
        objectAtSelect = null
    }

    /** The file to inspect this frame, or null to show [currentObject] as usual. */
    fun current(currentObject: Any?): File? {
        val f = file ?: return null
        if (currentObject !== objectAtSelect || !exists(f)) {
            clear()
            return null
        }
        return f
    }
}

/** A Properties window section for one kind of project file (EDT-66). */
interface FileInspector {
    fun handles(file: File): Boolean

    fun render(file: File)
}
