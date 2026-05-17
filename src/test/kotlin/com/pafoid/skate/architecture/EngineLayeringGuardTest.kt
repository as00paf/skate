package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

class EngineLayeringGuardTest {

    @Test
    fun EngineLayering_SceneDoesNotConstructBulletPhysicsDirectly() {
        val projectRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        val sceneFile = projectRoot.resolve("src/main/kotlin/com/pafoid/skate/engine/ecs/Scene.kt")
        val contents = sceneFile.readText()

        assertFalse(
            contents.contains("import com.pafoid.skate.engine.physics3d.BulletPhysics3D"),
            "Scene.kt must not directly import BulletPhysics3D. Use Physics3DFactory instead."
        )
        assertFalse(
            contents.contains("BulletPhysics3D("),
            "Scene.kt must not directly construct BulletPhysics3D. Use Physics3DFactory instead."
        )
    }

    @Test
    fun EngineLayering_BulletPhysicsUsesNoServiceLocatorInjection() {
        val projectRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        val physicsFile = projectRoot.resolve("src/main/kotlin/com/pafoid/skate/engine/physics3d/BulletPhysics3D.kt")
        val contents = physicsFile.readText()

        assertFalse(
            contents.contains("KoinComponent"),
            "BulletPhysics3D.kt must not implement KoinComponent. Use constructor DI."
        )
        assertFalse(
            contents.contains("by inject("),
            "BulletPhysics3D.kt must not use property injection. Use constructor DI."
        )
    }

    @Test
    fun EngineLayering_CriticalEnginePathsDoNotImportEditorPackage() {
        val projectRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        val engineRoot = projectRoot.resolve("src/main/kotlin/com/pafoid/skate/engine")
        assertTrue(Files.exists(engineRoot), "Expected engine source root to exist")
        val guardedFileNames = setOf("InputSystem.kt", "AudioSystem.kt", "SceneManager.kt")

        val guardedFiles = mutableListOf<java.nio.file.Path>()
        val violations = mutableListOf<String>()
        Files.walk(engineRoot).use { paths ->
            paths
                .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                .sorted()
                .forEach { file ->
                    if (file.fileName.toString() !in guardedFileNames) {
                        return@forEach
                    }
                    guardedFiles.add(file)
                    file.readText()
                        .lineSequence()
                        .forEachIndexed { index, line ->
                            val trimmed = line.trimStart()
                            val isComment = trimmed.startsWith("//") || trimmed.startsWith("*")
                            if (isComment) return@forEachIndexed
                            if (trimmed.startsWith("import com.pafoid.skate.editor.")) {
                                val relativePath = projectRoot.relativize(file).invariantSeparatorsPathString
                                violations.add("$relativePath:${index + 1} -> ${line.trim()}")
                            }
                        }
                }
        }

        assertTrue(
            guardedFiles.size == guardedFileNames.size,
            "Expected to guard files ${guardedFileNames.sorted()} but found ${guardedFiles.map { it.fileName.toString() }.sorted()}"
        )
        assertTrue(
            violations.isEmpty(),
            "Engine/editor layering violation detected in guarded critical engine paths.\n" +
                violations.joinToString("\n")
        )
    }
}
