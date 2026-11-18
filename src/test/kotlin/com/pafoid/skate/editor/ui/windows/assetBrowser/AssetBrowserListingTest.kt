package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.engine.assets.AssetFolder
import com.pafoid.skate.engine.assets.AssetId
import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.EngineDefaultResolver
import com.pafoid.skate.engine.assets.EngineDefaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** EDT-69: engine defaults are listed whether copied or not, with their badges (AST-26 §8). */
class AssetBrowserListingTest {

    @TempDir
    lateinit var dir: File

    private val engine get() = File(dir, "engine")
    private val project get() = File(dir, "Game")

    private val cube = EngineDefaults.Models.CUBE
    private val metal = EngineDefaults.Textures.METAL
    private val music = EngineDefaults.Sounds.MUSIC

    private fun write(file: File, text: String) = file.apply { parentFile.mkdirs(); writeText(text) }

    private fun engineFile(reference: String, text: String) = write(File(engine, "assets/" + EngineDefaults.logicalPath(reference)), text)

    /** A project copy as the engine copier makes it, with [text] as its content now. */
    private fun copy(reference: String, copiedText: String, text: String = copiedText): File {
        val logical = EngineDefaults.logicalPath(reference)!!
        val file = write(File(project, "Assets/$logical"), text)
        AssetMeta.write(file, AssetMeta(
            guid = AssetId.generate(),
            settings = mapOf(EngineDefaults.META_SOURCE to logical, EngineDefaults.META_SHA256 to EngineDefaultResolver.sha256(copiedText.toByteArray())),
        ))
        return file
    }

    private fun listing() = AssetBrowserListing(engine, catalog = listOf(cube, metal, music))

    @Test
    fun NotCopied_IsAnEngineEntry_UsedByItsReference() {
        engineFile(cube, "v1"); engineFile(metal, "v1"); engineFile(music, "v1")
        File(project, "Assets/Models").mkdirs()

        val found = listing().build(project)

        val entry = found[AssetFolder.MODELS]!!.single()
        assertEquals(listOf(EngineBadge.ENGINE), entry.badges)
        assertEquals(cube, entry.path, "applying or dragging it stores the engine: reference")
        assertEquals(File(engine, "assets/Models/cube.obj").absoluteFile, entry.file, "previews read the engine's file")
        assertEquals("metal.png", found[AssetFolder.TEXTURES]!!.single().name)
        assertEquals(AssetFolder.SOUNDS, found[AssetFolder.SOUNDS]!!.single().folder, "Sounds/Music/... is a sound")
    }

    @Test
    fun Copied_IsListedOnce_AsAnEngineCopy() {
        engineFile(cube, "v1")
        val file = copy(cube, "v1")

        val entry = listing().build(project)[AssetFolder.MODELS]!!.single()
        assertEquals(file, entry.file)
        assertEquals(listOf(EngineBadge.ENGINE_COPY), entry.badges)
        assertEquals(cube, entry.reference)
        assertEquals(file.path, entry.path, "a copy is used by its project path")
    }

    @Test
    fun EditedCopy_IsModified_ChangedEngineFile_IsAnUpdate() {
        engineFile(cube, "v2")
        copy(cube, copiedText = "v1", text = "mine")
        engineFile(metal, "v1")
        copy(metal, "v1")

        val found = listing().build(project)
        assertEquals(listOf(EngineBadge.ENGINE_COPY, EngineBadge.MODIFIED, EngineBadge.UPDATE_AVAILABLE), found[AssetFolder.MODELS]!!.single().badges)
        assertEquals(listOf(EngineBadge.ENGINE_COPY), found[AssetFolder.TEXTURES]!!.single().badges)
    }

    @Test
    fun AMovedCopy_StillCountsAsCopied() {
        engineFile(cube, "v1")
        val moved = copy(cube, "v1").let { f ->
            val target = File(project, "Assets/Props/cube.obj").apply { parentFile.mkdirs() }
            f.renameTo(target); AssetMeta.sidecarOf(f).renameTo(AssetMeta.sidecarOf(target)); target
        }

        val entry = listing().build(project)[AssetFolder.MODELS]!!.single()
        assertEquals(moved.name, entry.name)
        assertEquals(listOf(EngineBadge.ENGINE_COPY), entry.badges)
    }

    @Test
    fun ProjectFiles_HaveNoBadge_AndModelTexturesAreMarked() {
        write(File(project, "Assets/Textures/grass.png"), "x")
        write(File(project, "Assets/Models/house/textures/wood.png"), "x")

        val textures = listing().build(project)[AssetFolder.TEXTURES]!!
        assertEquals(listOf("grass.png", "wood.png"), textures.map { it.name })
        assertTrue(textures.all { it.badges.isEmpty() })
        assertEquals(listOf(false, true), textures.map { it.modelTexture })
    }

    @Test
    fun WithoutAProject_ListsTheEngineAssetsWithoutBadges() {
        engineFile(cube, "v1")

        val entry = listing().build(null)[AssetFolder.MODELS]!!.single()
        assertTrue(entry.badges.isEmpty())
        assertEquals("cube.obj", entry.name)
    }
}
