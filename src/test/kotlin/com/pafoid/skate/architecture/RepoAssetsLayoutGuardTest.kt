package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * AST-27 (AST-26 §4.1): the engine repo's `assets/` uses the typed project layout. No source or tool may name one of
 * the old history-shaped folders (`assets/obj`, `assets/characters`, lowercase `assets/textures`...).
 */
class RepoAssetsLayoutGuardTest {
    private val oldFolder = Regex("""assets[/\\]+(obj|characters|textures|sounds|fonts|strings[/\\]+game)\b""")

    @Test
    fun NoSourceNamesAnOldAssetsFolder() {
        val hits = listOf("src/main", "src/test", "tools").map(::File).filter { it.isDirectory }.flatMap { root ->
            root.walkTopDown().onEnter { it.name != "build" && it.name != ".gradle" }
                .filter { it.isFile && it.extension in setOf("kt", "kts", "sh", "py", "json", "properties") && it.name != "RepoAssetsLayoutGuardTest.kt" }
                .flatMap { file -> file.readLines().mapIndexedNotNull { i, line -> if (oldFolder.containsMatchIn(line)) "${file.invariantSeparatorsPath}:${i + 1}" else null } }
                .toList()
        }
        assertEquals(emptyList<String>(), hits)
    }

    @Test
    fun RepoAssetsUseTheTypedFolders() {
        for (dir in listOf("Models", "Animations", "Textures", "Sounds", "Fonts", "Data")) {
            assertTrue(File("assets", dir).isDirectory, "assets/$dir")
        }
        val old = listOf("obj", "characters").filter { File("assets", it).exists() }
        assertEquals(emptyList<String>(), old)
    }
}
