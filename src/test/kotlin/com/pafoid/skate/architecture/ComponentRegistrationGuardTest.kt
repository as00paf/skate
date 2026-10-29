package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

class ComponentRegistrationGuardTest {

    private val root = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        .resolve("src/main/kotlin/com/pafoid/skate")

    // Serializable components that are intentionally not part of the polymorphic Component module.
    private val notPolymorphicComponents = setOf("SceneComponent")

    @Test
    fun Serialization_EveryConcreteSerializableComponentIsRegistered() {
        val registry = root.resolve("engine/assets/serialization/Serializer.kt").readText()
        val registered = Regex("""subclass\((\w+)::class\)""").findAll(registry).map { it.groupValues[1] }.toSet()
        val declaration = Regex("""@Serializable\s+(?:data\s+)?class\s+(\w+)[^{@]*?\)\s*:\s*Component\(\)""")

        val missing = Files.walk(root.resolve("engine")).use { paths ->
            paths.filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                .toList()
                .flatMap { declaration.findAll(it.readText()).map { m -> m.groupValues[1] }.toList() }
        }.filter { it !in registered && it !in notPolymorphicComponents }

        assertTrue(missing.isEmpty(), "Components missing from Serializer polymorphic module: $missing")
    }
}
