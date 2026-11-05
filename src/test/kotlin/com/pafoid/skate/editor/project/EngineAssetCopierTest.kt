package com.pafoid.skate.editor.project

import com.pafoid.skate.engine.assets.Assets
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class EngineAssetCopierTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun BundledAssets_EverySourceExistsInTheRepo() {
        val missing = Assets.Bundled.bundledAssets.map { it.second }.filterNot { File(it).isFile }
        assertTrue(missing.isEmpty(), "bundled assets without a source file (new projects would log missing-asset errors): $missing")
    }

    @Test
    fun BundledAssets_DestinationsAreUnique() {
        val destinations = Assets.Bundled.bundledAssets.map { it.first.replace('\\', '/').lowercase() }
        assertEquals(destinations.size, destinations.toSet().size, "two bundled assets copy to the same file")
    }

    @Test
    fun BundledAssets_MetalTextureIsShippedForRails() {
        assertTrue(Assets.Bundled.bundledAssets.any { it.first == Assets.Bundled.METAL && File(it.second).isFile })
    }

    @Test
    fun Copier_CopiesEverythingFromTheRepo() {
        val result = EngineAssetCopier().copyBundledAssets(dir)
        assertTrue(result.isSuccess, result.exceptionOrNull()?.message)
        val metal = File(dir, "Assets/" + Assets.Bundled.METAL.replace('\\', '/'))
        assertTrue(metal.isFile && metal.length() > 0)
    }

    @Test
    fun Copier_FailsListingAMissingSourceButCopiesTheRest() {
        val root = File(dir, "engine")
        val (destination, source) = Assets.Bundled.bundledAssets.first { it.first == Assets.Bundled.METAL }
        File(root, source).also { it.parentFile.mkdirs(); it.writeText("png") }
        val copier = EngineAssetCopier(root)
        val project = File(dir, "Game")

        val result = copier.copyBundledAssets(project)

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()!!.message!!
        assertTrue(Assets.Bundled.bundledAssets.filter { it.second != source }.all { it.second in message })
        assertFalse(source in copier.missingSources())
        assertTrue(File(project, "Assets/" + destination.replace('\\', '/')).isFile, "present sources are still copied")
    }
}
