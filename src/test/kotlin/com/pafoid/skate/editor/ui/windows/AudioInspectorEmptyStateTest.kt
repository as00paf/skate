package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.ui.windows.AudioInspectorWindow.SourceState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-52H: which shared empty state the Audio Inspector's "Selected object" section shows. */
class AudioInspectorEmptyStateTest {

    @Test
    fun SourceState_NoScene_WinsOverEverythingElse() {
        assertEquals(SourceState.NO_SCENE, AudioInspectorWindow.sourceState(hasScene = false, hasSelection = false, hasAudio = false))
    }

    @Test
    fun SourceState_SceneWithoutSelection_IsNothingSelected() {
        assertEquals(SourceState.NOTHING_SELECTED, AudioInspectorWindow.sourceState(hasScene = true, hasSelection = false, hasAudio = false))
    }

    @Test
    fun SourceState_SelectionWithoutAudio_OffersToAddOne() {
        assertEquals(SourceState.NO_AUDIO_COMPONENT, AudioInspectorWindow.sourceState(hasScene = true, hasSelection = true, hasAudio = false))
    }

    @Test
    fun SourceState_SelectionWithAudio_ShowsTheSource() {
        assertEquals(SourceState.SOURCE, AudioInspectorWindow.sourceState(hasScene = true, hasSelection = true, hasAudio = true))
    }

    @Test
    fun EmptySection_LeavesRoomForTheActionButton() {
        assertTrue(AudioMixerPanel.emptySectionLines(withAction = true) > AudioMixerPanel.emptySectionLines(withAction = false))
    }
}
