package com.pafoid.skate.editor.loading

import com.pafoid.skate.editor.ui.handlers.ProjectActionHandler
import com.pafoid.skate.engine.utils.ExportStep
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** EDT-56: every export step has a loading-screen label in English and French. */
class LoadingStringsTest {
    private fun keys(file: String): Set<String> = File("src/main/resources/editor/strings/$file").readLines(Charsets.UTF_8)
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
        .map { it.substringBefore('=').trim() }
        .toSet()

    @Test
    fun EveryExportStep_HasALabel() {
        listOf("core.properties", "core_fr.properties").forEach { file ->
            val keys = keys(file)
            val missing = ExportStep.entries.map(ProjectActionHandler::exportStepKey).filter { it !in keys }
            assertTrue(missing.isEmpty(), "$file is missing $missing")
        }
    }
}
