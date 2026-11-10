package com.pafoid.skate.editor.assetimport.blender

import com.pafoid.skate.editor.ui.windows.assetBrowser.BrowserAssetType
import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.loaders.AnimationLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BlendWatchTest {
    @TempDir
    lateinit var dir: File

    private val a = File("a.blend")
    private fun stamp(t: Long) = BlendWatch.Stamp(t, 10)

    @Test
    fun ReportsAFileOnceItIsStableForTwoScans() {
        val watch = BlendWatch()
        assertTrue(watch.observe(mapOf(a to stamp(1))).isEmpty(), "first sight: wait for a second scan")
        assertEquals(listOf(a), watch.observe(mapOf(a to stamp(1))))
        assertTrue(watch.observe(mapOf(a to stamp(1))).isEmpty(), "unchanged: nothing to do")
    }

    @Test
    fun ASaveInProgressWaits() {
        val watch = BlendWatch()
        watch.observe(mapOf(a to stamp(1))); watch.observe(mapOf(a to stamp(1)))

        assertTrue(watch.observe(mapOf(a to stamp(2))).isEmpty())
        assertTrue(watch.observe(mapOf(a to stamp(3))).isEmpty(), "still changing")
        assertEquals(listOf(a), watch.observe(mapOf(a to stamp(3))))
    }

    @Test
    fun Seed_SkipsImportOnOpen() {
        val watch = BlendWatch()
        watch.seed(mapOf(a to stamp(1)))
        assertTrue(watch.observe(mapOf(a to stamp(1))).isEmpty())
    }

    @Test
    fun Scan_IgnoresBlenderSideFilesAndHiddenFolders() {
        listOf("Assets/cap.blend", "Assets/cap.blend1", "Assets/cap.blend@", "Assets/cap.blend12", "Assets/.cache/x.blend", "Assets/cap.blend.import/cap.glb")
            .forEach { File(dir, it).apply { parentFile.mkdirs(); writeText("x") } }

        assertEquals(listOf("cap.blend"), BlendWatch.scan(File(dir, "Assets")).keys.map { it.name })
    }

    @Test
    fun ModelDrop_BlendSpawnsItsImportedGlb() {
        val blend = File(dir, "Assets/cap.blend").apply { parentFile.mkdirs(); writeText("x") }
        assertNull(ModelDrop.modelFor(blend.path), "not imported yet")

        val glb = File(BlenderOutputs.dir(blend), "cap.glb").apply { parentFile.mkdirs(); writeText("glb") }

        assertEquals(glb.path, ModelDrop.modelFor(blend.path))
        assertEquals("x/y.gltf", ModelDrop.modelFor("x/y.gltf"))
        assertNull(ModelDrop.modelFor("x/y.png"))
    }

    @Test
    fun ActionClips_AreAnimationsNamedAfterTheAction() {
        assertEquals("Ollie", AnimationLoader.clipName("C:\\p\\Assets\\cap.blend.import\\board@Ollie.glb"))
        assertEquals("walking", AnimationLoader.clipName("assets/walking.fbx"))
        assertTrue(BrowserAssetType.ANIMATION.matches(File("board@Ollie.glb")))
        assertFalse(BrowserAssetType.ANIMATION.matches(File("board.glb")))
        assertEquals("blender", AssetMeta.importerFor(File("cap.blend")))
        assertTrue(BlenderOutputs.isBlenderSideFile(File("cap.blend1")))
        assertFalse(BlenderOutputs.isBlenderSideFile(File("cap.blend")))
    }
}
