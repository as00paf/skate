package com.pafoid.skate.engine.utils

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.RenderComponent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** AST-19: exports pack the GLB generated from a `.blend`, never the `.blend` (the runtime never sees Blender files). */
class BlendNotPackedTest {
    @TempDir
    lateinit var dir: File

    private fun project(): Project {
        val blend = File(dir, "Assets/Props/cap.blend").apply { parentFile.mkdirs(); writeText("blend") }
        File(dir, "Assets/Props/cap.blend1").writeText("backup")
        val glb = File(dir, "Assets/Props/cap.blend.import/cap.glb").apply { parentFile.mkdirs(); writeText("glb") }
        val scene = Scene("Main").apply { addChild(GameObject("Cap").addComponent(RenderComponent(`3dModel`(glb.absolutePath)))) }
        val sceneFile = File(dir, "Scenes/Main.scene").apply { parentFile.mkdirs() }
        sceneFile.writeText(Serializer(AssetPaths().apply { projectRoot = dir.absoluteFile }).encodeFile(FileKind.SCENE, scene))
        val file = File(dir, "Game.skateproject").apply { writeText("{}") }
        check(blend.isFile)
        return Project(name = "Game", projectPath = file.absolutePath, defaultScene = sceneFile.absolutePath, scenesPath = listOf(sceneFile.absolutePath))
    }

    @Test
    fun ReferencedPack_HasTheGlbNotTheBlend() {
        val names = AssetsPacker(LoggerService()).selectFiles(project()).map { it.name }

        assertTrue("cap.glb" in names, "$names")
        assertFalse(names.any { it.startsWith("cap.blend") }, "$names")
    }

    @Test
    fun FullPack_StillSkipsBlendFiles() {
        val names = AssetsPacker(LoggerService()).selectFiles(project(), onlyReferenced = false).map { it.name }

        assertFalse(names.any { it.endsWith(".blend") || it.endsWith(".blend1") }, "$names")
    }
}
