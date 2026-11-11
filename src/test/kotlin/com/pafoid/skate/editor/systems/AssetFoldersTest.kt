package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.AssetFolder
import com.pafoid.skate.engine.assets.ProjectLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** AST-27 (AST-26 §3): the type folder a new file lands in. */
class AssetFoldersTest {
    @TempDir
    lateinit var dir: File

    private fun folder(name: String) = AssetFolders.folderFor(File(dir, name))

    @Test
    fun EveryRowOfTheTable() {
        listOf("a.gltf", "a.glb", "a.obj", "a.dae", "a.mtl", "a.bin", "Board.blend").forEach { assertEquals(AssetFolder.MODELS, folder(it), it) }
        listOf("a.skanim", "skater.controller.json").forEach { assertEquals(AssetFolder.ANIMATIONS, folder(it), it) }
        listOf("a.png", "a.jpg", "a.hdr", "a.tga").forEach { assertEquals(AssetFolder.TEXTURES, folder(it), it) }
        listOf("a.skmat", "a.mat", "a.material").forEach { assertEquals(AssetFolder.MATERIALS, folder(it), it) }
        listOf("a.wav", "a.ogg", "a.mp3").forEach { assertEquals(AssetFolder.SOUNDS, folder(it), it) }
        listOf("a.kts", "a.kt", "a.java").forEach { assertEquals(AssetFolder.SCRIPTS, folder(it), it) }
        assertEquals(AssetFolder.SHADERS, folder("water.glsl"))
        listOf("a.ttf", "a.otf").forEach { assertEquals(AssetFolder.FONTS, folder(it), it) }
        assertEquals(AssetFolder.PREFABS, folder("Crate.prefab"))
        listOf("skater.character.json", "a.json", "a.config", "notes.txt").forEach { assertEquals(AssetFolder.DATA, folder(it), it) }
        assertEquals(AssetFolder.ITEMS, folder("cap.item.json"))
        assertNull(folder(ProjectLayout.STRINGS_FILE), "strings.properties stays at the root of Assets/")
        assertNull(folder("readme.xyz"))
    }

    @Test
    fun Fbx_ByContent() {
        assertEquals(AssetFolder.ANIMATIONS, AssetFolders.folderFor(File("assets/Animations/jump.fbx")))
    }

    @Test
    fun TargetDir_ScenesGoToScenes_UnknownToTheAssetsRoot() {
        val project = File(dir, "P")
        assertEquals(ProjectLayout.scenesDir(project), AssetFolders.targetDir(project, File("Main.scene")))
        assertEquals(File(ProjectLayout.assetsDir(project), "Textures"), AssetFolders.targetDir(project, File("rock.png")))
        assertEquals(ProjectLayout.assetsDir(project), AssetFolders.targetDir(project, File("x.xyz")))
    }
}
