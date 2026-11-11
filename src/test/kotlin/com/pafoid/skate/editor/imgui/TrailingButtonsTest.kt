package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.theme.ThemeLibrary
import com.pafoid.skate.editor.settings.AppearanceSettings
import com.pafoid.skate.editor.settings.Density
import com.pafoid.skate.editor.settings.EditorSettings
import com.pafoid.skate.editor.settings.Spacing
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.Texture
import imgui.ImGui
import imgui.type.ImInt
import imgui.type.ImString
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * EDT-86: buttons after a field in the inspector (texture Delete, a component's Edit/Refresh, a list item's Remove,
 * a property's reset) must never be cut off or pushed out of the window, at any density preset, font size or width.
 * The guard renders real ImGui frames (editor fonts and themes, no GPU) through the shared rows.
 */
class TrailingButtonsTest {

    // --- Layout maths ---

    @Test
    fun Layout_FieldGetsWhatTheButtonsLeave() {
        val layout = TrailingButtons.layout(available = 300f, buttonWidths = listOf(40f, 60f), labelWidth = 50f, itemSpacingX = 8f, minFieldWidth = 78f)
        assertFalse(layout.wrapped)
        assertEquals(300f - 50f - (40f + 8f) - (60f + 8f), layout.fieldWidth)
    }

    @Test
    fun Layout_TooNarrow_ButtonsWrapAndTheFieldTakesTheLine() {
        val layout = TrailingButtons.layout(available = 160f, buttonWidths = listOf(40f, 60f), labelWidth = 0f, itemSpacingX = 8f, minFieldWidth = 78f)
        assertTrue(layout.wrapped)
        assertEquals(160f, layout.fieldWidth)
    }

    @Test
    fun Layout_NoButtons_FullWidth() {
        assertEquals(TrailingButtons.Layout(200f, false), TrailingButtons.layout(200f, emptyList(), 0f, 8f, 78f))
    }

    @Test
    fun ButtonWidth_GrowsWithFramePadding() {
        // Compact vs Spacious frame padding (EDT-76): the reserved room follows the style, it is not a constant.
        assertEquals(22f, TrailingButtons.buttonWidth(14f, 4f))
        assertEquals(38f, TrailingButtons.buttonWidth(14f, 12f))
    }

    @Test
    fun FitsOnLine_WrapsWhatWouldPassTheRightEdge() {
        assertTrue(TrailingButtons.fitsOnLine(lineEndX = 100f, buttonWidth = 40f, itemSpacingX = 8f, rightX = 148f))
        assertFalse(TrailingButtons.fitsOnLine(lineEndX = 100f, buttonWidth = 40f, itemSpacingX = 8f, rightX = 147f))
    }

    // --- Guard: real frames at every density, font size and width ---

    private val assets = mockk<AssetsManager>(relaxed = true) {
        every { getTextures() } returns listOf(
            Texture(filePath = "Assets/Textures/concrete_ground_with_a_very_long_name_albedo.png"),
            Texture(filePath = "Assets/Textures/brick.png"),
        )
    }
    private val listText = ImString("ui.default.text.with.a.long.key", 256)
    private val combo = ImInt(0)

    @BeforeEach
    fun setup() {
        ImGui.createContext()
        val io = ImGui.getIO()
        io.iniFilename = null
        io.loadFonts(javaClass.getResourceAsStream(Assets.Fonts.FONTS_FILE)!!.readAllBytes())
        io.setDisplaySize(1600f, 1000f)
        io.deltaTime = 1f / 60f
        ImGuiStyleManager.invalidate()
        ImGuiStyleManager.library = ThemeLibrary.load(File(Assets.Themes.DIR))
    }

    @AfterEach
    fun teardown() {
        TrailingButtons.placed = null
        ImGuiStyleManager.uiScale = { 1f }
        ImGuiStyleManager.appearance = { AppearanceSettings.THEME_ONLY }
        ImGuiStyleManager.library = ThemeLibrary(emptyList())
        ImGui.destroyContext()
    }

    /** The inspector rows with trailing buttons, as the Properties window draws them. */
    private fun inspector(actual: MutableList<Pair<Float, Float>>) {
        MImGui.textureDropdown("Base Color Texture", null, assets, "Select…")
        MImGui.propertyRow("Roughness", onReset = {}) { ImGui.sliderFloat("##roughness", floatArrayOf(0.5f), 0f, 1f) }
        // The Animator's controller row (Edit + Refresh after a labelled combo, as on integration/33b).
        val edit = "Edit##controller_edit"
        val refresh = "Refresh##controller_refresh"
        val row = TrailingButtons.begin(edit, refresh, fieldLabel = "Controller##controller")
        ImGui.combo("Controller##controller", combo, arrayOf("skater.controller", "none"), 2)
        row.next(); ImGui.button(edit); actual += ImGui.getItemRectMinX() to ImGui.getItemRectMaxX()
        row.next(); ImGui.button(refresh); actual += ImGui.getItemRectMinX() to ImGui.getItemRectMaxX()
        // A list item and its Remove (UI components).
        val remove = "Remove"
        val listRow = TrailingButtons.begin(remove)
        ImGui.inputText("##item", listText)
        listRow.next(); ImGui.button(remove); actual += ImGui.getItemRectMinX() to ImGui.getItemRectMaxX()
        // Icon-only Delete (the texture row's glyph, as a separate check of the font's icon width).
        val trash = "${Icons.TRASH}##probe_trash"
        val trashRow = TrailingButtons.begin(trash)
        ImGui.inputText("##probe_field", listText)
        trashRow.next(); ImGui.button(trash); actual += ImGui.getItemRectMinX() to ImGui.getItemRectMaxX()
    }

    private fun render(width: Float, placed: MutableList<TrailingButtons.Placed>, actual: MutableList<Pair<Float, Float>>) {
        repeat(2) {
            placed.clear(); actual.clear()
            ImGui.newFrame()
            ImGui.setNextWindowPos(100.4f, 60.6f)
            ImGui.setNextWindowSize(width, 700f)
            ImGui.begin("Properties##probe")
            inspector(actual)
            ImGui.end()
            ImGui.render()
        }
    }

    @Test
    fun TrailingButtons_AlwaysFit_AtEveryDensityFontSizeAndWidth() {
        val failures = mutableListOf<String>()
        val densest = AppearanceSettings(Density.CUSTOM, Spacing(Spacing.MAX, Spacing.MAX, Spacing.MAX, Spacing.MAX, Spacing.MAX, Spacing.MAX))
        val appearances = Density.entries.filter { it != Density.CUSTOM }.map { AppearanceSettings(density = it) } + densest
        var cases = 0
        for (appearance in appearances) for (scale in listOf(1f, 1.5f, EditorSettings.MAX_UI_SCALE)) for (width in listOf(240f, 420f, 700f)) {
            ImGuiStyleManager.appearance = { appearance }
            ImGuiStyleManager.uiScale = { scale }
            ImGuiStyleManager.invalidate()
            ImGuiStyleManager.sync("dark")
            val placed = mutableListOf<TrailingButtons.Placed>()
            val actual = mutableListOf<Pair<Float, Float>>()
            TrailingButtons.placed = { placed += it }
            render(width, placed, actual)
            val case = "${appearance.density}${if (appearance === densest) "(max)" else ""} @${(scale * 100).toInt()}% width=$width"
            if (placed.size != 6) failures += "$case: ${placed.size} buttons placed, expected 6"
            placed.forEachIndexed { i, p ->
                cases++
                if (p.minX < p.clipMinX - 0.5f || p.maxX > p.clipMaxX + 0.5f) failures += "$case: button $i cut off: $p"
            }
            // The recorded position is where ImGui really drew the button.
            actual.forEachIndexed { i, (minX, maxX) ->
                val p = placed[placed.size - actual.size + i]
                if (kotlin.math.abs(p.minX - minX) > 0.5f || kotlin.math.abs(p.maxX - maxX) > 0.5f) failures += "$case: probe $i at $p but drawn at $minX..$maxX"
            }
        }
        assertTrue(cases > 0)
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
