package com.pafoid.skate.editor.search.providers

import com.pafoid.skate.editor.search.SearchEngine
import com.pafoid.skate.editor.search.data.SearchCategory
import com.pafoid.skate.engine.core.LoggerService
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** EDT-61: Search Anywhere finds the project's asset files by name and reveals them in the Project window. */
class AssetSearchProviderTest {
    @TempDir
    lateinit var project: File

    private fun file(path: String) = File(project, path).also { it.parentFile.mkdirs(); it.writeText("x") }

    private fun provider(revealed: MutableList<String> = ArrayList()) =
        AssetSearchProvider(mockk<LoggerService>(relaxed = true), { project }) { revealed += it }

    private fun fileResults(query: String) = runBlocking { provider().search(query) }.filter { it.metadata["inProject"] == true }

    @Test
    fun FindsAModelByFileNameWithoutTheExtension() {
        file("Assets/models/characters/james.glb")
        val result = fileResults("james").single()
        assertEquals("james.glb", result.displayName)
        assertEquals(SearchCategory.ASSET_MODEL, result.category)
        assertEquals("Assets/models/characters/james.glb", result.description)
        assertEquals(1.0f, result.relevanceScore, "exact name without extension")
    }

    @Test
    fun FindsEveryAssetKindAnywhereInTheProject() {
        val expected = mapOf(
            "Assets/models/ramp.obj" to SearchCategory.ASSET_MODEL,
            "Assets/textures/ramp_albedo.png" to SearchCategory.ASSET_TEXTURE,
            "Assets/animations/ramp_idle.fbx" to SearchCategory.ASSET_ANIMATION,
            "Assets/sounds/ramp_grind.ogg" to SearchCategory.ASSET_SOUND,
            "Assets/prefabs/ramp.prefab" to SearchCategory.ASSET_PREFAB,
            "Assets/scripts/RampScript.kts" to SearchCategory.ASSET_SCRIPT,
            "elsewhere/deep/ramp_extra.wav" to SearchCategory.ASSET_SOUND,
        )
        expected.keys.forEach { file(it) }
        file("Assets/ramp_notes.txt") // not an asset kind

        val found = fileResults("ramp").associate { it.description to it.category }
        assertEquals(expected, found)
    }

    @Test
    fun MatchesCaseInsensitivelyAndInsideNames() {
        file("Assets/models/JamesBoard.glb")
        assertEquals(listOf("JamesBoard.glb"), fileResults("board").map { it.displayName })
        assertEquals(listOf("JamesBoard.glb"), fileResults("JAMES").map { it.displayName })
    }

    @Test
    fun SkipsBuildAndVcsFolders() {
        file("build/james.glb")
        file(".git/james.glb")
        file("Assets/james.glb")
        assertEquals(listOf("Assets/james.glb"), fileResults("james").map { it.description })
    }

    @Test
    fun NoProject_FindsNoFiles() {
        val provider = AssetSearchProvider(mockk(relaxed = true), { null })
        assertTrue(runBlocking { provider.search("james") }.none { it.metadata["inProject"] == true })
    }

    @Test
    fun Navigate_RevealsTheFileInTheProjectWindow() {
        val glb = file("Assets/james.glb")
        val revealed = ArrayList<String>()
        val p = provider(revealed)
        p.navigate(runBlocking { p.search("james") }.first { it.metadata["inProject"] == true })
        assertEquals(listOf(glb.absolutePath), revealed)
    }

    @Test
    fun AllAssetCategories_RouteToThisProvider() {
        val p = provider()
        listOf(
            SearchCategory.ASSET_MODEL, SearchCategory.ASSET_TEXTURE, SearchCategory.ASSET_ANIMATION,
            SearchCategory.ASSET_SOUND, SearchCategory.ASSET_PREFAB, SearchCategory.ASSET_SCRIPT,
        ).forEach { assertTrue(it in p.categories, "$it") }
    }

    @Test
    fun SearchEngine_NavigatesATextureResultToTheAssetProvider() {
        // Before EDT-61 only ASSET_MODEL results reached the provider (routing used its single category).
        val png = file("Assets/textures/james_face.png")
        val engine = SearchEngine(mockk(relaxed = true), mockk(relaxed = true), { project })
        val result = runBlocking { engine.search("james_face") }.getValue(SearchCategory.ASSET_TEXTURE).single()
        assertEquals(png.absolutePath, result.metadata["path"])
        assertTrue(engine.navigate(result))
        assertTrue(engine.hasProvider(SearchCategory.ASSET_SCRIPT))
    }
}
