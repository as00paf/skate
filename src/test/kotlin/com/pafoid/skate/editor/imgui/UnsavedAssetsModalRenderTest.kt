package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.theme.ThemeLibrary
import com.pafoid.skate.editor.systems.CloseGuard
import com.pafoid.skate.editor.systems.CloseTarget
import com.pafoid.skate.editor.systems.UnsavedAsset
import com.pafoid.skate.editor.systems.UnsavedAssetRegistry
import com.pafoid.skate.editor.systems.UnsavedKind
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/** EDT-82: the unsaved-assets prompt renders (real ImGui frames, no GPU) with its list and a failed save. */
class UnsavedAssetsModalRenderTest {
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
        ImGuiStyleManager.sync("dark")
    }

    @AfterEach
    fun teardown() {
        ImGuiStyleManager.library = ThemeLibrary(emptyList())
        ImGui.destroyContext()
    }

    private fun frames(modal: UnsavedAssetsModal, n: Int = 2) = repeat(n) {
        ImGui.newFrame()
        modal.render()
        ImGui.render()
    }

    @Test
    fun Prompt_RendersTheListAndAFailedSave() {
        // The real string tables, so every key the modal reads must exist (EN).
        val strings = StringManager(mockk(relaxed = true), "en").apply { loadStrings(Assets.Strings.EDITOR_STRINGS) }
        var sceneSaves = false
        var sceneDirty = true
        var clipDirty = true
        val registry = UnsavedAssetRegistry().also { r ->
            r.register {
                listOfNotNull(
                    UnsavedAsset(UnsavedKind.SCENE, "scene:Main", "Main", save = { if (sceneSaves) sceneDirty = false }).takeIf { sceneDirty },
                    UnsavedAsset(UnsavedKind.ANIMATION_CLIP, "walk.skanim", "walk.skanim", save = { clipDirty = false }).takeIf { clipDirty },
                    UnsavedAsset(UnsavedKind.ANIMATOR_CONTROLLER, "hero.controller.json", "hero.controller.json", save = {}),
                )
            }
        }
        val guard = CloseGuard(registry)
        val modal = UnsavedAssetsModal(strings, guard)

        frames(modal) // nothing pending: nothing drawn
        var closed = false
        guard.request(CloseTarget.EDITOR) { closed = true }
        frames(modal, 3) // the prompt with three entries
        guard.saveAll()
        frames(modal, 2) // the scene and controller failed: listed as not saved
        assertEquals(listOf("Main", "hero.controller.json"), guard.failed.map { it.name })
        guard.cancel()
        frames(modal)
        assertTrue(!closed)
        for (k in UnsavedKind.entries) assertTrue(strings.hasString("lbl.unsaved_assets.kind.${k.key}"), k.key)
    }
}
