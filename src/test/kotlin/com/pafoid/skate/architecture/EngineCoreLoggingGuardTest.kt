package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class EngineCoreLoggingGuardTest {

    @Test
    fun EngineCoreLogging_NoPrintlnOrPrintStackTrace() {
        val root = Paths.get(System.getProperty("user.dir")).resolve("src/main/kotlin/com/pafoid/skate/engine/core")
        val offenders = Files.walk(root).use { paths ->
            paths.filter { it.toString().endsWith(".kt") }.toList().flatMap { file ->
                Files.readAllLines(file)
                    .withIndex()
                    .filter { (_, line) -> line.contains("println(") || line.contains("printStackTrace(") }
                    .map { "${file.fileName}:${it.index + 1}" }
            }
        }
        assertTrue(offenders.isEmpty(), "Use LoggerService instead of println/printStackTrace in engine/core: $offenders")
    }
}
