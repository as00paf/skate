package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.data.FileType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** ANI-14: double-clicking a `.skanim` clip in the Project window opens the Animation Editor. */
class ProjectWindowDoubleClickTest {
    @Test
    fun Skanim_OpensInTheAnimationEditor() {
        assertTrue(ProjectWindow.opensInAnimationEditor(File("Assets/Animations/Ollie.skanim")))
        assertTrue(ProjectWindow.opensInAnimationEditor(File("kick.SKANIM")))
        assertFalse(ProjectWindow.opensInAnimationEditor(File("rig.fbx")))
        assertTrue("skanim" in FileType.ANIMATION.extensions)
    }
}
