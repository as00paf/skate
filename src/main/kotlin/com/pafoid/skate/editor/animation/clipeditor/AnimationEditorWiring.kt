package com.pafoid.skate.editor.animation.clipeditor

import com.pafoid.skate.app.Editor
import com.pafoid.skate.editor.animation.mocap.AnimationClipCreated
import com.pafoid.skate.editor.animation.mocap.VideoToAnimationWiring
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.engine.assets.loaders.AnimationLoader
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.events.Event
import java.io.File

/** Opens a clip in the Animation editor and shows the window (Project window, Video to Animation, ...). */
data class OpenAnimationClip(val path: String) : Event("animation.open_clip")

/** Builds the Animation editor's controller with the editor's services (ANI-14). */
object AnimationEditorWiring {
    const val WINDOW = "window.animation_editor"

    fun create(engine: Engine, editor: Editor): AnimationEditorController {
        val controller = AnimationEditorController(
            execute = { editor.undoRedoManager.executeCommand(it) },
            rigFor = { VideoToAnimationWiring.skeletonFor(engine, it) },
            loadAnimationFile = { file -> AnimationLoader().loadAnimations(file.absolutePath).first() },
        )
        fun openAndShow(path: String) {
            controller.open(File(path))
            engine.eventSystem.publish(WindowAction.Show(WINDOW))
        }
        engine.eventSystem.subscribe<OpenAnimationClip> { openAndShow(it.path) }
        // A clip made by Video to Animation opens here right away (ANI-17).
        engine.eventSystem.subscribe<AnimationClipCreated> { openAndShow(it.path) }
        return controller
    }
}
