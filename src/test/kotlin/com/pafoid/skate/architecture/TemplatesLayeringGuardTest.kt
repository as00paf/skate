package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

/** EDT-59 §11: the New Project templates build on the engine, never the other way round. */
class TemplatesLayeringGuardTest {

    @Test
    fun TemplatesLayering_EngineDoesNotImportTemplates() {
        val root = Paths.get(System.getProperty("user.dir")).resolve("src/main/kotlin/com/pafoid/skate/engine")
        val offenders = Files.walk(root).use { paths ->
            paths.filter { it.toString().endsWith(".kt") }.toList().filter { f ->
                Files.readAllLines(f).any { it.trimStart().startsWith("import com.pafoid.skate.templates.") }
            }.map { it.fileName.toString() }
        }
        assertTrue(offenders.isEmpty(), "engine must not import templates: $offenders")
    }
}
