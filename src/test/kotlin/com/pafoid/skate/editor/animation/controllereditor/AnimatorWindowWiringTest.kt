package com.pafoid.skate.editor.animation.controllereditor

import com.pafoid.skate.engine.animation.edit.SkAnimFormat
import com.pafoid.skate.engine.animation.edit.SkClip
import com.pafoid.skate.engine.animation.graph.AnimationControllerAsset
import com.pafoid.skate.engine.animation.graph.StateDef
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.data.models.animations.SkeletonPose
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.addComponent
import io.mockk.every
import io.mockk.mockk
import org.joml.Matrix4f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AnimatorWindowWiringTest {

    @TempDir
    lateinit var project: File

    @Test
    fun Resolve_ProjectFilesAreEditable_BundledOnesReadOnly() {
        val file = File(project, "Assets/Animations/a.controller.json").apply { parentFile.mkdirs(); writeText("{}") }
        assertEquals(file, AnimatorWindowWiring.resolve(file.absolutePath, project)!!.file)
        assertEquals(file, AnimatorWindowWiring.resolve("Assets/Animations/a.controller.json", project)!!.file, "project-relative")
        val bundled = AnimatorWindowWiring.resolve(Assets.Configs.SKATER_CONTROLLER, project)!!
        assertNull(bundled.file)
        assertTrue(bundled.text.contains("\"skater\""))
        assertNull(AnimatorWindowWiring.resolve("nope.controller.json", project))
    }

    @Test
    fun Resolve_EngineDefault_OpensTheProjectOverrideEditable_ElseTheEngineFileReadOnly() {
        val ref = Assets.Configs.SKATER_CONTROLLER // engine:Animations/skater.controller.json (GPL-26)
        val engine = AnimatorWindowWiring.resolve(ref, project)!!
        assertNull(engine.file, "no project copy: the engine's file is read-only")
        assertTrue(engine.text.contains("\"skater\""))

        com.pafoid.skate.editor.project.EngineAssetCopier().copyEngineDefaults(project, copyAll = false).getOrThrow()
        val copy = File(project, "Assets/Animations/skater.controller.json")
        assertEquals(copy.absoluteFile, AnimatorWindowWiring.resolve(ref, project)!!.file?.absoluteFile, "the project copy is edited")
        assertEquals(copy.absoluteFile, AnimatorWindowWiring.fileFor(ref, project)?.absoluteFile, "saving it reloads skaters using the reference")
    }

    @Test
    fun ClipScanner_UsesTheNameInsideEachFile_AndProjectRelativePaths() {
        val f = File(project, "Assets/Animations/run_v2.skanim").apply { parentFile.mkdirs() }
        f.writeText(SkAnimFormat.encode(SkClip(name = "run", duration = 1f, tracks = emptyList())))
        File(project, "Assets/Animations/broken.skanim").writeText("{")
        var now = 0L
        val scanner = ProjectClipScanner(rescanMillis = 1000L) { project }
        assertEquals(listOf(ProjectClip("run", "Assets/Animations/run_v2.skanim")), scanner.clips(now))
        File(project, "Assets/Animations/walk.skanim").writeText(SkAnimFormat.encode(SkClip(name = "walk", duration = 1f, tracks = emptyList())))
        assertEquals(1, scanner.clips(now + 500).size, "cached between scans")
        now += 1500
        assertEquals(listOf("run", "walk"), scanner.clips(now).map { it.name })
    }

    @Test
    fun Reload_RebuildsOnlyTheAnimatorsUsingTheSavedFile() {
        val file = File(project, "Assets/Animations/a.controller.json").apply {
            parentFile.mkdirs()
            writeText(AnimationControllerAsset("a", "idle", listOf(StateDef("idle", ""))).toJson())
        }
        val skeleton = Skeleton(Bone(0, "root", Matrix4f()), 1)
        fun character(path: String?) = GameObject("c").apply {
            addComponent(SkeletonComponent(SkeletonPose(skeleton)))
            addComponent(Animator(mutableListOf(Animation("idle", emptyList(), 1f, "idle.glb"))).apply { controllerPath = path })
        }
        val user = character("Assets/Animations/a.controller.json")
        val other = character(Assets.Configs.SKATER_CONTROLLER)
        val scene = Scene("s").apply { addChild(GameObject("group").apply { addChild(user) }); addChild(other) }
        val engine = mockk<Engine>(relaxed = true) {
            every { sceneManager.openScenes } returns mutableListOf(scene)
            every { logger } returns LoggerService()
            every { assetsManager.readText(any()) } answers { File(project, firstArg<String>()).takeIf { it.isFile }?.readText() }
        }
        assertEquals(1, AnimatorWindowWiring.reload(engine, project, file))
        assertNotNull(user.components.filterIsInstance<Animator>().single().stateMachine)
        assertNull(other.components.filterIsInstance<Animator>().single().stateMachine)
    }
}
