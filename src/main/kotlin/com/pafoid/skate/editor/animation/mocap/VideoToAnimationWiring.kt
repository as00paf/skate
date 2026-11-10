package com.pafoid.skate.editor.animation.mocap

import com.pafoid.skate.app.Editor
import com.pafoid.skate.editor.commands.animation.CreateAnimationClipCommand
import com.pafoid.skate.editor.events.FileSystemEvent
import com.pafoid.skate.editor.systems.EditorPaths
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.loaders.AnimationLoader
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.events.Event
import com.pafoid.skate.engine.getComponent
import java.io.File

/** A new clip was written (video to animation, mocap import). The animation editor (ANI-14) opens it from here. */
data class AnimationClipCreated(val path: String) : Event("animation.clip_created")

/** Builds the controller of the Video to Animation window with the editor's services (ANI-17). */
object VideoToAnimationWiring {
    fun create(engine: Engine, editor: Editor, projectManager: ProjectManager): VideoToAnimationController {
        val settings = editor.settingsManager
        // The downloaded pose model is per user, shared by all projects (ANI-18).
        val models = PoseModelStore({ File(EditorPaths.dir, "mocap") })
        val backend = MediaPipeBackend(MocapProcessRunner(), { settings.editorSettings.mocap }, models)
        return VideoToAnimationController(
            backend = backend,
            workDir = { File(System.getProperty("java.io.tmpdir"), "skate-mocap").apply { mkdirs() } },
            animationsDir = { projectManager.getProjectDirectory()?.let { File(it, "Assets${File.separator}Animations") } },
            targetSkeleton = { rig -> skeletonFor(engine, rig) },
            loadAnimationFile = { file ->
                val animation = AnimationLoader().loadAnimations(file.absolutePath).first()
                // Files with a skinned mesh carry their rig; animation-only files fall back to name matching.
                val skeleton = runCatching { engine.assetsManager.loadModel(file.absolutePath).skeleton }.getOrNull()
                animation to skeleton
            },
            saveClip = { clip, file -> editor.undoRedoManager.executeCommand(CreateAnimationClipCommand(clip, file)) },
            onCreated = { file ->
                engine.eventSystem.publish(FileSystemEvent.FileSystemChangedEvent(file.parentFile?.absolutePath))
                engine.eventSystem.publish(FileSystemEvent.RevealInProject(file.absolutePath))
                engine.eventSystem.publish(AnimationClipCreated(file.absolutePath))
            },
            background = { work -> engine.jobSystem.runAsync { work() } },
            main = { work -> engine.jobSystem.runOnMain { work() } },
        )
    }

    /** The rig for [rig]: the skater model, or the selected object's skeleton. Shared with the animation editor (ANI-14). */
    internal fun skeletonFor(engine: Engine, rig: TargetRig) = when (rig) {
        TargetRig.SKATER -> (engine.assetsManager.getModel(Assets.Models.JAMES) ?: engine.assetsManager.loadModel(Assets.Models.JAMES)).skeleton
        TargetRig.SELECTED -> engine.sceneManager.currentScene?.selectedGameObject?.getComponent<SkeletonComponent>()?.pose?.skeleton
    }
}
