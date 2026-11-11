package com.pafoid.skate.engine.assets

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ProjectLayoutTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun create_MakesAssetsScenesAndBuilds() {
        val assets = ProjectLayout.create(dir)

        assertEquals(File(dir, "Assets"), assets)
        assertEquals(setOf("Assets", "Scenes", "Builds"), dir.listFiles()!!.filter { it.isDirectory }.map { it.name }.toSet())
    }

    @Test
    fun create_MakesEveryTypeFolderAndNoEngineDefaultsFolder() {
        val assets = ProjectLayout.create(dir)

        assertEquals(
            setOf("Models", "Animations", "Textures", "Materials", "Sounds", "Scripts", "Shaders", "Fonts", "Data", "Prefabs", "Items"),
            assets.listFiles()!!.filter { it.isDirectory }.map { it.name }.toSet(),
        )
        assertEquals(File(assets, "Textures"), ProjectLayout.typeDir(dir, AssetFolder.TEXTURES))
        assertFalse(File(assets, "EngineDefaults").exists())
    }

    @Test
    fun assetPath_IsProjectRelativeWithSlashes() {
        assertEquals("Assets/Textures/metal.png", ProjectLayout.assetPath("Textures\\metal.png"))
    }

    @Test
    fun duplicateAssetFolders_EmptyForTheStandardLayout() {
        ProjectLayout.create(dir)

        assertTrue(ProjectLayout.duplicateAssetFolders(dir).isEmpty())
    }

    @Test
    fun duplicateAssetFolders_FindsASecondAssetsFolder() {
        ProjectLayout.create(dir)
        val stray = File(dir, "Builds/assets").apply { mkdirs() }

        assertEquals(listOf(stray), ProjectLayout.duplicateAssetFolders(dir))
    }

    @Test
    fun duplicateAssetFolders_IgnoresHiddenFolders() {
        ProjectLayout.create(dir)
        File(dir, ".skate/cache/assets").mkdirs()

        assertTrue(ProjectLayout.duplicateAssetFolders(dir).isEmpty())
    }
}
