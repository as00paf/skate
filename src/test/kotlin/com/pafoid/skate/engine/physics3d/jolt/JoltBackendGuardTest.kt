package com.pafoid.skate.engine.physics3d.jolt

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/** PHY-17 layering: Jolt types stay inside the Jolt backend, and only the factory builds Jolt worlds. */
class JoltBackendGuardTest {

    private val sourceRoot: Path = Paths.get(System.getProperty("user.dir")).toAbsolutePath().resolve("src/main/kotlin")

    private fun kotlinFiles(): List<Path> = Files.walk(sourceRoot).use { paths ->
        paths.filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }.toList()
    }

    @Test
    fun JoltLayering_OnlyJoltBackendPackageImportsJoltJni() {
        val violations = kotlinFiles()
            .filter { !it.invariantSeparatorsPathString.contains("/engine/physics3d/jolt/") }
            .filter { Regex("""\bcom\.github\.stephengold\.joltjni\b""").containsMatchIn(it.readText()) }
            .map { it.invariantSeparatorsPathString.substringAfter("src/main/kotlin/") }
        assertTrue(violations.isEmpty(), "Only engine/physics3d/jolt may reference jolt-jni: $violations")
    }

    @Test
    fun JoltLayering_OnlyPhysics3DFactoryConstructsJoltPhysics() {
        val allowed = setOf("Physics3DFactory.kt", "JoltPhysics3D.kt")
        val violations = kotlinFiles()
            .filter { it.fileName.toString() !in allowed }
            .filter { Regex("""\bJoltPhysics3D\(""").containsMatchIn(it.readText()) }
            .map { it.invariantSeparatorsPathString.substringAfter("src/main/kotlin/") }
        assertTrue(violations.isEmpty(), "Only Physics3DFactory may construct JoltPhysics3D: $violations")
    }
}
