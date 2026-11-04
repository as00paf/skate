package com.pafoid.skate.editor.scripting

/** Template and file-name rules for new `.kts` scripts. */
object ScriptTemplates {
    const val EXTENSION = "kts"
    const val SCRIPTS_DIRECTORY = "Assets/Scripts"

    /** Returns the file name with the `.kts` extension, or null when [input] is not a valid script name. */
    fun fileName(input: String): String? {
        val base = input.trim().removeSuffix(".$EXTENSION")
        if (base.isEmpty() || !base.matches(Regex("[A-Za-z_][A-Za-z0-9_\\-]*"))) return null
        return "$base.$EXTENSION"
    }

    fun template(): String = """
        // Attach this script to an entity with a Script component. Saving the file hot-reloads it while playing.
        onCreate { ctx ->
            ctx.log.info("script created")
        }

        onUpdate { ctx, dt ->
        }
    """.trimIndent() + "\n"
}
