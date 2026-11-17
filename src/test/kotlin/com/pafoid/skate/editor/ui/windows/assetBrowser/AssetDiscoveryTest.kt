package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.engine.assets.AssetFolder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** EDT-69: every type folder's files are found, by type over the whole `Assets/` tree (AST-26 §8). */
class AssetDiscoveryTest {

    @TempDir
    lateinit var dir: File

    private val assets get() = File(dir, "Assets")

    private fun touch(path: String) = File(dir, path).apply { parentFile.mkdirs(); writeText("x") }

    private fun names(folder: AssetFolder) = AssetDiscovery.discover(assets, folder).map { it.name }

    @Test
    fun EveryTypeFolder_HasItsFilesListed() {
        touch("Assets/Models/cube.obj")
        touch("Assets/Animations/walk.skanim")
        touch("Assets/Animations/skater.controller.json")
        touch("Assets/Textures/metal.png")
        touch("Assets/Materials/steel.skmat")
        touch("Assets/Sounds/Music/song.mp3")
        touch("Assets/Scripts/Player.kts")
        touch("Assets/Shaders/water.glsl")
        touch("Assets/Fonts/Roboto.ttf")
        touch("Assets/Data/skater.character.json")
        touch("Assets/Prefabs/ramp.prefab")
        touch("Assets/Items/board.item.json")

        val expected = mapOf(
            AssetFolder.MODELS to listOf("cube.obj"),
            AssetFolder.ANIMATIONS to listOf("skater.controller.json", "walk.skanim"),
            AssetFolder.TEXTURES to listOf("metal.png"),
            AssetFolder.MATERIALS to listOf("steel.skmat"),
            AssetFolder.SOUNDS to listOf("song.mp3"),
            AssetFolder.SCRIPTS to listOf("Player.kts"),
            AssetFolder.SHADERS to listOf("water.glsl"),
            AssetFolder.FONTS to listOf("Roboto.ttf"),
            AssetFolder.DATA to listOf("skater.character.json"),
            AssetFolder.PREFABS to listOf("ramp.prefab"),
            AssetFolder.ITEMS to listOf("board.item.json"),
        )
        assertEquals(AssetFolder.entries.toSet(), expected.keys, "the test covers every type folder")
        val found = AssetDiscovery.discover(assets)
        expected.forEach { (folder, files) -> assertEquals(files, found[folder].orEmpty().map { it.name }, "$folder") }
    }

    @Test
    fun ListsByType_NotByFolder() {
        touch("Assets/Misc/moved.png")
        touch("Assets/Models/Board@Ollie.glb")
        touch("Assets/Models/board.glb")

        assertEquals(listOf("moved.png"), names(AssetFolder.TEXTURES))
        assertEquals(listOf("Board@Ollie.glb"), names(AssetFolder.ANIMATIONS))
        assertEquals(listOf("board.glb"), names(AssetFolder.MODELS))
    }

    @Test
    fun SkipsSidecarsCompanionsStringsAndHiddenFolders() {
        touch("Assets/Models/house/house.gltf")
        touch("Assets/Models/house/house.bin")
        touch("Assets/Models/cube.obj.meta")
        touch("Assets/Models/cube.mtl")
        touch("Assets/strings.properties")
        touch("Assets/.cache/thumb.png")

        assertEquals(listOf("house.gltf"), names(AssetFolder.MODELS))
        assertTrue(names(AssetFolder.TEXTURES).isEmpty())
        assertTrue(names(AssetFolder.DATA).isEmpty())
    }

    @Test
    fun MatchesExtensionsInAnyCase() {
        touch("Assets/a.PNG"); touch("Assets/b.Wav"); touch("Assets/c.FBX")

        assertEquals(listOf("a.PNG"), names(AssetFolder.TEXTURES))
        assertEquals(listOf("b.Wav"), names(AssetFolder.SOUNDS))
        assertEquals(listOf("c.FBX"), names(AssetFolder.ANIMATIONS))
    }

    @Test
    fun ModelTextures_AreImagesInsideAModelFolder() {
        val inModel = touch("Assets/Models/house/textures/wood.png")
        val loose = touch("Assets/Textures/wood.png")
        val nextToModels = touch("Assets/Models/cover.png")

        assertTrue(AssetDiscovery.isModelTexture(assets, inModel))
        assertFalse(AssetDiscovery.isModelTexture(assets, loose))
        assertFalse(AssetDiscovery.isModelTexture(assets, nextToModels))
    }

    @Test
    fun MissingRoot_IsEmpty() {
        assertTrue(AssetDiscovery.discover(File(dir, "nope")).isEmpty())
    }

    @Test
    fun Root_IsProjectAssetsFolder_ElseEngineAssetsFolder() {
        val project = File(dir, "MyGame")
        val engine = File(dir, "engine")

        assertEquals(File(project, "Assets"), AssetDiscovery.root(project, engine))
        assertEquals(File(engine, "assets"), AssetDiscovery.root(null, engine))
    }
}
