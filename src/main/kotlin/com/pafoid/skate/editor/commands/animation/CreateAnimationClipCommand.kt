package com.pafoid.skate.editor.commands.animation

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.engine.animation.edit.SkAnimFormat
import com.pafoid.skate.engine.animation.edit.SkClip
import java.io.File

/**
 * Writes a new `.skanim` clip (video to animation, mocap import: ANI-17). Execute-only: undo never deletes files.
 * The file is always new: callers pick a free name with [freeFile].
 */
class CreateAnimationClipCommand(private val clip: SkClip, val file: File) : ExecuteOnlyCommand {
    override fun execute() {
        file.parentFile?.mkdirs()
        file.writeText(SkAnimFormat.encode(clip))
    }

    override fun undo() {}
    override fun getDisplayName(): String = "Create Animation Clip"
    override fun getTargetName(): String = file.name

    companion object {
        /** `<dir>/<base>.skanim`, or `<base> 2.skanim`, `<base> 3.skanim`, ... when taken. */
        fun freeFile(dir: File, base: String): File {
            val clean = base.replace(Regex("""[\\/:*?"<>|]+"""), "_").trim().ifEmpty { "Clip" }
            var file = File(dir, "$clean.${SkClip.EXTENSION}")
            var n = 2
            while (file.exists()) file = File(dir, "$clean ${n++}.${SkClip.EXTENSION}")
            return file
        }
    }
}
