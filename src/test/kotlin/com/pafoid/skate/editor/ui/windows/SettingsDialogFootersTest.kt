package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.DialogFooter
import com.pafoid.skate.editor.imgui.FooterButton
import com.pafoid.skate.editor.imgui.FooterButton.Role
import com.pafoid.skate.editor.ui.windows.ExportWizardWindow.FooterState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-52 E: the standard footers of the Key Bindings and Export dialogs. */
class SettingsDialogFootersTest {

    private fun labels(buttons: List<FooterButton>) = DialogFooter.order(buttons).map { it.labelKey }

    @Test
    fun KeyBindings_ResetToDefaultsOnlyOnTheRebindableEditorTabs() {
        for (tab in KeyBindingsWindow.TAB_ACTIONS..KeyBindingsWindow.TAB_TEST) {
            val expected = if (tab in KeyBindingsWindow.TAB_EDITOR..KeyBindingsWindow.TAB_HIERARCHY) {
                listOf("btn.reset_to_defaults", "btn.close")
            } else listOf("btn.close")
            assertEquals(expected, labels(KeyBindingsWindow.footer(tab, escapeUsedByCapture = false)), "tab $tab")
        }
    }

    @Test
    fun KeyBindings_EscapeClosesUnlessItCancelledACapture() {
        val normal = KeyBindingsWindow.footer(KeyBindingsWindow.TAB_EDITOR, escapeUsedByCapture = false)
        assertTrue(DialogFooter.escapeTarget(normal)!!.closes)
        val capturing = KeyBindingsWindow.footer(KeyBindingsWindow.TAB_EDITOR, escapeUsedByCapture = true)
        val esc = DialogFooter.escapeTarget(capturing)!!
        assertEquals("btn.close", esc.labelKey)
        assertFalse(esc.closes, "the Esc that cancels a capture must not close the dialog")
    }

    @Test
    fun KeyBindings_ResetRunsItsActionAndKeepsTheDialogOpen() {
        var reset = 0
        val reset1 = KeyBindingsWindow.footer(KeyBindingsWindow.TAB_CAMERA, false, onResetToDefaults = { reset++ })
            .single { it.labelKey == "btn.reset_to_defaults" }
        reset1.onClick()
        assertEquals(1, reset)
        assertFalse(reset1.closes)
    }

    @Test
    fun Export_EditingIsCancelThenExport_AndExportFollowsCanStart() {
        val off = ExportWizardWindow.footer(FooterState.EDITING, canStart = false)
        assertEquals(listOf("btn.cancel", "btn.export.start"), labels(off))
        val start = off.single { it.labelKey == "btn.export.start" }
        assertFalse(start.enabled)
        assertFalse(start.closes, "the dialog stays open to show progress")
        assertEquals(Role.PRIMARY, start.role)

        val on = ExportWizardWindow.footer(FooterState.EDITING, canStart = true)
        assertSame(on.single { it.labelKey == "btn.export.start" }, DialogFooter.enterTarget(on))
        assertEquals("btn.cancel", DialogFooter.escapeTarget(on)!!.labelKey)
    }

    @Test
    fun Export_RunningAndNoProjectOnlyClose() {
        assertEquals(listOf("btn.close"), labels(ExportWizardWindow.footer(FooterState.RUNNING)))
        assertEquals(listOf("btn.close"), labels(ExportWizardWindow.footer(FooterState.NO_PROJECT)))
    }

    @Test
    fun Export_FinishedOffersOpenFolderOnlyWhenItExists() {
        assertEquals(
            listOf("btn.export.open_folder", "btn.export.back", "btn.close"),
            labels(ExportWizardWindow.footer(FooterState.FINISHED, hasOutputFolder = true)),
        )
        val noFolder = ExportWizardWindow.footer(FooterState.FINISHED, hasOutputFolder = false)
        assertEquals(listOf("btn.export.back", "btn.close"), labels(noFolder))
        assertTrue(noFolder.single { it.labelKey == "btn.export.back" }.closes.not())
    }
}
