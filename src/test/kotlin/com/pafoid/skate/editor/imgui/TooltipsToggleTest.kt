package com.pafoid.skate.editor.imgui

import imgui.ImGui
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * EDT-65: tooltips for hovers that are not ImGui items (the Render Graph canvas, the Script Editor gutter) follow the
 * Show tooltips setting like every other tooltip: help hides when it is off, explanations and data always show.
 */
class TooltipsToggleTest {

    @BeforeEach
    fun setup() {
        ImGui.createContext()
        val io = ImGui.getIO()
        io.iniFilename = null
        io.fonts.addFontDefault()
        io.fonts.build()
        io.setDisplaySize(800f, 600f)
        io.deltaTime = 1f / 60f
        ImGui.newFrame()
    }

    @AfterEach
    fun teardown() {
        Tooltips.enabled = { true }
        ImGui.render()
        ImGui.destroyContext()
    }

    @Test
    fun Help_ShowsOnlyWhenTooltipsAreOn() {
        Tooltips.enabled = { true }
        assertTrue(Tooltips.set(Tooltips.Kind.HELP, "Click to turn this pass off"))
        Tooltips.enabled = { false }
        assertFalse(Tooltips.set(Tooltips.Kind.HELP, "Click to turn this pass off"))
    }

    @Test
    fun ExplanationsAndData_AlwaysShow() {
        Tooltips.enabled = { false }
        assertTrue(Tooltips.set(Tooltips.Kind.ALWAYS, "Line 3: unresolved reference"))
    }
}
