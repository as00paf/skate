package com.pafoid.skate.engine.assets

import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.editor.systems.FileTypeResolver
import com.pafoid.skate.editor.ui.windows.assetBrowser.BrowserAssetType
import com.pafoid.skate.engine.animation.edit.SkAnimFormat
import com.pafoid.skate.engine.animation.edit.SkClip
import com.pafoid.skate.engine.animation.edit.SkKey
import com.pafoid.skate.engine.animation.edit.SkTrack
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.assets.loaders.AnimationLoader
import com.pafoid.skate.engine.assets.pack.PackBuilder
import com.pafoid.skate.engine.assets.pack.PackInput
import com.pafoid.skate.engine.assets.pack.ReferenceCollector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer

/** AST-21: `.skanim` clips are animation assets in the editor, the registry and exported packs. */
class SkAnimAssetTypeTest {
    @TempDir
    lateinit var dir: File

    private val clipText = SkAnimFormat.encode(
        SkClip(name = "wave", duration = 1f, tracks = listOf(SkTrack("Hips", AnimationPath.TRANSLATION, keys = listOf(SkKey(0f, listOf(0f, 1f, 0f)))))),
    )

    private fun file(path: String, text: String) = File(dir, path).also { it.parentFile.mkdirs(); it.writeText(text) }

    @Test
    fun SkAnim_IsAnAnimationInTheBrowserAndSearch() {
        val clip = file("Assets/anims/wave.skanim", clipText)
        assertTrue("skanim" in FileType.ANIMATION.extensions)
        assertEquals(FileType.ANIMATION, FileTypeResolver.resolve(clip))
        assertTrue(BrowserAssetType.ANIMATION.matches(clip))
        assertTrue(BrowserAssetType.ANIMATION.matches(File("Wave.SKANIM")), "extension match is case-insensitive")
    }

    @Test
    fun SkAnim_RegistryGivesItAnAnimationImporter() {
        assertEquals("animation", AssetMeta.importerFor(File("wave.skanim")))
        val clip = file("Assets/wave.skanim", clipText)
        AssetRegistry(dir).scan()
        assertEquals("animation", AssetMeta.read(clip)!!.importer)
    }

    @Test
    fun SkAnim_ReferencedClipIsPackedAndLoadsFromThePack() {
        file("Assets/anims/wave.skanim", clipText)
        file("Assets/anims/unused.skanim", clipText)
        val scene = file("Scenes/main.scene", """{"animator":{"animations":[{"path":"Assets/anims/wave.skanim"}]}}""")

        val found = ReferenceCollector(dir).collect(listOf(scene))
        assertEquals(setOf("main.scene", "wave.skanim"), found.map { it.name }.toSet())

        val clip = found.single { it.extension == "skanim" }
        val pack = PackBuilder.build("p", listOf(PackInput(clip.path, "Assets/anims/wave.skanim", "ANIMATION", clip.readBytes())))
        val entry = pack.manifest.entries.single()
        val bytes = PackBuilder.extract(pack.data, entry.position, entry.storedSize, entry.compressed)
        val loaded = AnimationLoader().loadAnimations(ByteBuffer.wrap(bytes), entry.path).single()
        assertEquals("wave", loaded.name)
        assertEquals(1, loaded.channels.size)
    }
}
