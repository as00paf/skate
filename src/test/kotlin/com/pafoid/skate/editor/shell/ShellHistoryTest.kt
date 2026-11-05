package com.pafoid.skate.editor.shell

import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.WindowCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ShellHistoryTest {

    @Test
    fun `collapses consecutive duplicates, ignores blanks and keeps the newest entries`() {
        val h = ShellHistory(capacity = 3)
        listOf("a", "a", " ", "b", "c", "d").forEach { h.add(it) }
        assertEquals(listOf("b", "c", "d"), h.items)
    }

    @Test
    fun `up and down recall like a terminal`() {
        val h = ShellHistory()
        listOf("one", "two", "three").forEach { h.add(it) }
        assertEquals("three", h.previous())
        assertEquals("two", h.previous())
        assertEquals("one", h.previous())
        assertNull(h.previous())
        assertEquals("two", h.next())
        assertEquals("three", h.next())
        assertEquals("", h.next())
        assertNull(h.next())
        h.add("four")
        assertEquals("four", h.previous())
    }

    @Test
    fun `persists across sessions`(@TempDir dir: File) {
        val file = File(dir, "shell_history.txt")
        ShellHistory(file).load().apply { add("gradlew.bat test"); add("echo é") }
        val reloaded = ShellHistory(file).load()
        assertEquals(listOf("gradlew.bat test", "echo é"), reloaded.items)
        assertEquals("echo é", reloaded.previous())
        reloaded.clear()
        assertEquals(emptyList<String>(), ShellHistory(file).load().items)
        assertTrue(ShellHistory(File(dir, "missing.txt")).load().items.isEmpty())
    }

    @Test
    fun `presets cover Gradle and the CLI on the open project`() {
        val none = ShellPresets.all(windows = true)
        assertEquals(listOf("gradlew.bat build", "gradlew.bat test"), none.take(2).map { it.command })
        assertTrue(none.none { it.command.contains("validate") })

        val withProject = ShellPresets.all(windows = false, projectFile = "/tmp/My Game/My Game.skateproject")
        val validate = withProject.single { it.labelKey == "shell.preset.cli_validate" }.command
        assertEquals("./gradlew -q :tools:cli:run --args=\"validate '/tmp/My Game/My Game.skateproject'\"", validate)
        assertEquals(6, withProject.size)
        assertTrue(withProject.all { it.labelKey.startsWith("shell.preset.") })
    }

    @Test
    fun `output buffer keeps the newest lines`() {
        val buffer = ShellOutputBuffer(maxLines = 3)
        buffer.addAll((1..5).map { ShellLine("l$it") })
        assertEquals(3, buffer.size)
        assertEquals("l3", buffer[0].text)
        assertEquals("l3\nl4\nl5", buffer.text())
    }

    @Test
    fun `shell window is listed under Debug`() {
        val w = object : EditorWindow("window.shell") { override fun imgui() {} }
        assertEquals(WindowCategory.DEBUG, WindowCategory.of(w))
    }
}
