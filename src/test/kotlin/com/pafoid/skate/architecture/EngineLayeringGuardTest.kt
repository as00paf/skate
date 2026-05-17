package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Paths
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
}
