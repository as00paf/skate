package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText

/** INP-10: the engine input layer stays game-agnostic (docs/design/INP-10.md). */
class InputGameAgnosticGuardTest {

    private val root: Path = Paths.get(System.getProperty("user.dir")).resolve("src/main/kotlin/com/pafoid/skate")

    private val files: List<Path> by lazy {
        val input = Files.walk(root.resolve("engine/input")).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() }
        val extra = listOf(
            "engine/ecs/systems/InputSystem.kt",
            "engine/ecs/components/InputStateComponent.kt",
            "engine/events/InputEvents.kt",
        ).map { root.resolve(it) }
        extra.forEach { assertTrue(Files.exists(it), "Guarded input file moved or deleted, update the guard: $it") }
        input + extra
    }

    private fun rel(p: Path) = root.relativize(p).invariantSeparatorsPathString

    @Test
    fun InputLayering_DoesNotImportGame() {
        val offenders = files.filter { it.readText().contains("com.pafoid.skate.game.") }.map(::rel)
        assertTrue(offenders.isEmpty(), "engine input must not reference the game package: $offenders")
    }

    @Test
    fun InputLayering_HasNoSkateVocabulary() {
        val violations = files.flatMap { f -> words(f.readText()).filter { it in DENY }.distinct().map { "${rel(f)}: $it" } }
        assertTrue(violations.isEmpty(), "Skateboarding concepts belong in game/** (INP-10): $violations")
    }

    @Test
    fun InputLayering_ContextsAreOpen() {
        val src = root.resolve("engine/input/ActionMap.kt").readText()
        assertFalse(Regex("""enum\s+class\s+InputContext\b""").containsMatchIn(src), "InputContext must stay an open type")
    }

    @Test
    fun InputLayering_TokenizerSplitsIdentifiersAndSkipsComments() {
        val w = words("package com.pafoid.skate.x\nimport com.pafoid.skate.y\n// a skate comment\nval keyboardKey = \"flick_it\" /* trick */")
        assertTrue("keyboard" in w && "flick" in w)
        assertFalse("skate" in w || "trick" in w || "board" in w)
    }

    private companion object {
        val DENY = setOf(
            "skate", "skater", "skateboard", "board", "flick", "trick", "ollie", "kickflip", "heelflip", "grind",
            "manual", "stance", "goofy", "mongo", "deck", "truck", "grab", "revert", "bail",
        )

        private val blockComment = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        private val lineComment = Regex("""//[^\n]*""")
        private val token = Regex("""[A-Za-z]+""")
        private val camel = Regex("""[A-Z]?[a-z]+|[A-Z]+(?![a-z])""")

        /** Lower-case words of identifiers and string literals, excluding comments and package/import lines. */
        fun words(source: String): List<String> {
            val code = source.replace(blockComment, " ").replace(lineComment, " ")
                .lines().filterNot { it.trimStart().startsWith("package ") || it.trimStart().startsWith("import ") }
                .joinToString("\n")
            return token.findAll(code).flatMap { t -> camel.findAll(t.value).map { it.value.lowercase() } }.toList()
        }
    }
}
