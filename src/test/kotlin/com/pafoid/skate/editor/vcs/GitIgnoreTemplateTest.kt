package com.pafoid.skate.editor.vcs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GitIgnoreTemplateTest {
    @Test
    fun MergeInto_AddsTheBlockOnceAndKeepsUserLines() {
        assertEquals(GitIgnoreTemplate.gitignoreBlock, GitIgnoreTemplate.mergeInto(null))
        val merged = GitIgnoreTemplate.mergeInto("mine.txt")
        assertTrue(merged.startsWith("mine.txt\n\n"))
        assertEquals(merged, GitIgnoreTemplate.mergeInto(merged))
    }

    @Test
    fun Template_NeverIgnoresMetaScenesOrProjects() {
        val rules = GitIgnoreTemplate.gitignoreBlock.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        val protected = listOf("Assets/a.png.meta", "Scenes/Main.scene", "Game.skateproject", "Assets/p/Rail.prefab")
        protected.forEach { path ->
            val name = path.substringAfterLast('/')
            rules.forEach { rule ->
                val glob = Regex(rule.removeSuffix("/").replace(".", "\\.").replace("*", ".*"))
                assertTrue(!glob.matches(name) && !path.startsWith(rule), "$rule would ignore $path")
            }
        }
        assertTrue(GitIgnoreTemplate.gitattributes.contains("*.meta text eol=lf"))
        assertTrue(GitIgnoreTemplate.gitattributes.contains("*.png binary"))
    }
}
