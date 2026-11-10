package com.pafoid.skate.engine.assets

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
    fun engineDefaults_LiveInsideTheAssetsFolder() {
        assertEquals(File(File(dir, "Assets"), "EngineDefaults"), ProjectLayout.engineDefaultsDir(dir))
    }

    @Test
    fun assetPath_IsProjectRelativeWithSlashes() {
        assertEquals("Assets/EngineDefaults/Textures/metal.png", ProjectLayout.assetPath("EngineDefaults\\Textures\\metal.png"))
    }

    @Test
    fun duplicateAssetFolders_EmptyForTheStandardLayout() {
        ProjectLayout.create(dir)
        File(ProjectLayout.engineDefaultsDir(dir), "Textures").mkdirs()

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
