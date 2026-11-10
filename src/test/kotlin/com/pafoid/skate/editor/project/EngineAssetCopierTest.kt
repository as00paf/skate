package com.pafoid.skate.editor.project

import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.EngineDefaultResolver
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.ProjectLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** AST-27 (AST-26 §4.3, §4.5, §5.2): the engine-default catalog and the copier's two modes. */
class EngineAssetCopierTest {
    @TempDir
    lateinit var dir: File

    private val project get() = File(dir, "Game").also { ProjectLayout.create(it) }
    private val assets get() = ProjectLayout.assetsDir(project)

    @Test
    fun Catalog_EverySourceExistsInTheRepo_AndLogicalPathsAreUnique() {
        val missing = EngineDefaults.CATALOG.filterNot { File("assets/" + EngineDefaults.logicalPath(it)).isFile }
        assertEquals(emptyList<String>(), missing, "catalog entries without a source in assets/")
        val logical = EngineDefaults.CATALOG.map { EngineDefaults.logicalPath(it)!!.lowercase() }
        assertEquals(logical.size, logical.toSet().size, "two catalog entries share a logical path")
    }

    @Test
    fun Catalog_EveryRepoShaderIsBasicOrInternal() {
        val repo = File("assets/Shaders").listFiles { f -> f.extension == "glsl" }!!.map { EngineDefaults.reference("Shaders/" + it.name) }.toSet()
        val basic = EngineDefaults.Shaders.BASIC.toSet()
        val internal = EngineDefaults.Shaders.INTERNAL.toSet()
        assertTrue((basic intersect internal).isEmpty())
        assertEquals(repo, basic + internal)
        assertTrue(EngineDefaults.Shaders.INTERNAL.none { it in EngineDefaults.CATALOG }, "internal shaders are never copied")
    }

    @Test
    fun CopyMode_CopiesEveryEntryWithItsEngineMeta() {
        val report = EngineAssetCopier().copyEngineDefaults(project, copyAll = true).getOrThrow()

        assertEquals(EngineDefaults.CATALOG.size, report.copied.size)
        for (ref in EngineDefaults.CATALOG) {
            val logical = EngineDefaults.logicalPath(ref)!!
            val copy = File(assets, logical)
            assertTrue(copy.isFile, "$logical copied")
            val meta = AssetMeta.read(copy)!!
            assertEquals(logical, meta.settings[EngineDefaults.META_SOURCE])
            assertEquals(EngineDefaultResolver.sha256(copy), meta.settings[EngineDefaults.META_SHA256])
        }
        assertFalse(File(assets, "EngineDefaults").exists())
    }

    @Test
    fun ReferenceMode_CopiesOnlyTheBasicShaders() {
        val report = EngineAssetCopier().copyEngineDefaults(project, copyAll = false).getOrThrow()

        assertEquals(EngineDefaults.Shaders.BASIC.size, report.copied.size)
        assertTrue(report.copied.all { it.parentFile.name == "Shaders" })
        assertFalse(File(assets, "Models/cube.obj").exists())
    }

    @Test
    fun NameClash_KeepsTheUsersFile_AndTheEngineCopyBecomesTheOverride() {
        val mine = File(assets, "Models/cube.obj").apply { parentFile.mkdirs(); writeText("my cube") }

        val copy = EngineAssetCopier().copy(project, EngineDefaults.Models.CUBE)!!

        assertEquals("cube (engine).obj", copy.name)
        assertEquals("my cube", mine.readText())
        assertEquals(copy.absoluteFile, EngineDefaultResolver { project }.resolve(EngineDefaults.Models.CUBE))
    }

    @Test
    fun AnExistingOverride_IsNotCopiedAgain() {
        EngineAssetCopier().copy(project, EngineDefaults.Models.CUBE)!!
        assertNull(EngineAssetCopier().copy(project, EngineDefaults.Models.CUBE))
        assertFalse(File(assets, "Models/cube (engine).obj").exists())
    }

    @Test
    fun MissingSource_IsAFailureListingIt() {
        val emptyEngine = File(dir, "engine").apply { mkdirs() }
        val result = EngineAssetCopier(emptyEngine).copyEngineDefaults(project, copyAll = true)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Models/cube.obj"))
        // Shaders and the app icon still come from the classpath
        assertTrue(File(assets, "Shaders/shader_3d_default.glsl").isFile)
        assertTrue(File(assets, "Textures/app_icon.png").isFile)
    }

    @Test
    fun UnchangedShaderCopies_FollowTheEngine_ModifiedOnesAreKept() {
        val engine = File(dir, "engine")
        val engineShaders = File(engine, "assets/Shaders").apply { mkdirs() }
        EngineDefaults.Shaders.BASIC.forEach { File(engineShaders, it.substringAfterLast('/')).writeText("// v1 $it") }
        val copier = EngineAssetCopier(engine)
        copier.copyEngineDefaults(project, copyAll = false).getOrThrow()
        val skybox = File(assets, "Shaders/skybox.glsl")
        val bloom = File(assets, "Shaders/post_bloom.glsl").apply { writeText("// my bloom") }

        File(engineShaders, "skybox.glsl").writeText("// v2 skybox")
        File(engineShaders, "post_bloom.glsl").writeText("// v2 bloom")
        val refreshed = copier.refreshUnchangedShaders(project)

        assertEquals(listOf(skybox.absoluteFile), refreshed.map { it.absoluteFile })
        assertEquals("// v2 skybox", skybox.readText())
        assertEquals(EngineDefaultResolver.sha256(skybox), AssetMeta.read(skybox)!!.settings[EngineDefaults.META_SHA256])
        assertEquals("// my bloom", bloom.readText())
    }

    @Test
    fun CopyStatus_UnchangedModifiedAndUpdateAvailable() {
        val engine = File(dir, "engine")
        listOf("Models/cube.obj", "Models/sphere.obj", "Models/bank.obj", "Models/bench.obj").forEach {
            File(engine, "assets/$it").apply { parentFile.mkdirs(); writeText("v1 $it") }
        }
        val copier = EngineAssetCopier(engine)
        listOf(EngineDefaults.Models.CUBE, EngineDefaults.Models.SPHERE, EngineDefaults.Models.BANK, EngineDefaults.Models.BENCH)
            .forEach { copier.copy(project, it)!! }
        File(assets, "Models/sphere.obj").writeText("mine")
        File(engine, "assets/Models/bank.obj").writeText("v2")
        File(assets, "Models/bench.obj").writeText("mine")
        File(engine, "assets/Models/bench.obj").writeText("v2")

        val states = copier.copyStatuses(project).associate { it.reference to it.state }

        assertEquals(EngineAssetCopier.CopyState.UNCHANGED, states[EngineDefaults.Models.CUBE])
        assertEquals(EngineAssetCopier.CopyState.MODIFIED, states[EngineDefaults.Models.SPHERE])
        assertEquals(EngineAssetCopier.CopyState.UPDATE_AVAILABLE, states[EngineDefaults.Models.BANK])
        assertEquals(EngineAssetCopier.CopyState.MODIFIED_AND_UPDATED, states[EngineDefaults.Models.BENCH])
        assertEquals(4, states.size, "only copies are listed")
    }
}
