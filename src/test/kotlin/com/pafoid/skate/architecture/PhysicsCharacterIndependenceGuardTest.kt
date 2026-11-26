package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.io.path.readText

/** GPL-40a: ground probing belongs to `GroundProbeSystem`; the physics system knows nothing about characters. */
class PhysicsCharacterIndependenceGuardTest {
    private val systems = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        .resolve("src/main/kotlin/com/pafoid/skate/engine/ecs/systems")

    @Test
    fun PhysicsSystem_DoesNotKnowPlayerController() {
        val source = systems.resolve("PhysicsSystem.kt").readText()
        assertTrue(!source.contains("PlayerController"), "PhysicsSystem must not reference PlayerController")
        assertTrue(!source.contains("GroundDetector"), "Ground probing lives in GroundProbeSystem")
    }
}
