package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.engine.assets.Assets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AssetDiscoveryTest {

    @TempDir
    lateinit var dir: File

    private fun touch(path: String) = File(dir, path).apply { parentFile.mkdirs(); writeText("x") }

    private fun names(type: BrowserAssetType) = AssetDiscovery.discover(File(dir, "Assets"), type).map { it.name }

    private fun newProjectLayout() {
        touch("Assets/EngineDefaults/Characters/animations/idle_0.fbx")
        touch("Assets/EngineDefaults/Characters/james.glb")
        touch("Assets/EngineDefaults/Textures/metal.png")
        touch("Assets/EngineDefaults/Textures/Rock.JPG")
        touch("Assets/EngineDefaults/Sounds/Music/song.mp3")
        touch("Assets/EngineDefaults/Models/cube.obj")
        touch("Assets/EngineDefaults/Shaders/shader.glsl")
        touch("Assets/strings.properties")
    }

    @Test
    fun discover_ListsEachTypeOfANewProject_LoadedOrNot() {
        newProjectLayout()

        assertEquals(listOf("idle_0.fbx"), names(BrowserAssetType.ANIMATION))
        assertEquals(listOf("metal.png", "Rock.JPG"), names(BrowserAssetType.TEXTURE))
        assertEquals(listOf("song.mp3"), names(BrowserAssetType.SOUND))
    }

    @Test
    fun discover_MatchesExtensionsInAnyCase() {
        touch("Assets/a.PNG"); touch("Assets/b.Wav"); touch("Assets/c.FBX")

        assertEquals(listOf("a.PNG"), names(BrowserAssetType.TEXTURE))
        assertEquals(listOf("b.Wav"), names(BrowserAssetType.SOUND))
        assertEquals(listOf("c.FBX"), names(BrowserAssetType.ANIMATION))
    }

    @Test
    fun discover_SkipsHiddenFolders() {
        touch("Assets/.cache/thumb.png")
        touch("Assets/Textures/real.png")

        assertEquals(listOf("real.png"), names(BrowserAssetType.TEXTURE))
    }

    @Test
    fun discover_MissingRoot_IsEmpty() {
        assertTrue(AssetDiscovery.discover(File(dir, "nope"), BrowserAssetType.TEXTURE).isEmpty())
    }

    @Test
    fun root_IsProjectAssetsFolder_ElseEngineAssetsFolder() {
        val project = File(dir, "MyGame")
        val engine = File(dir, "engine")

        assertEquals(File(project, "Assets"), AssetDiscovery.root(project, engine))
        assertEquals(File(engine, "assets"), AssetDiscovery.root(null, engine))
    }

    @Test
    fun bundledAssets_GiveANewProjectAnimationsTexturesAndSounds() {
        newProjectLayout()

        // Verify the discovery finds at least one file per asset type
        var foundAnimation = false
        var foundTexture = false
        var foundSound = false
        BrowserAssetType.entries.forEach { type ->
            val files = AssetDiscovery.discover(File(dir, "Assets"), type)
            if (files.isNotEmpty()) when (type) {
                BrowserAssetType.ANIMATION -> foundAnimation = true
                BrowserAssetType.TEXTURE -> foundTexture = true
                BrowserAssetType.SOUND -> foundSound = true
            }
        }
        assertTrue(foundAnimation, "a new project has no ANIMATION to list")
        assertTrue(foundTexture, "a new project has no TEXTURE to list")
        assertTrue(foundSound, "a new project has no SOUND to list")
    }
}
