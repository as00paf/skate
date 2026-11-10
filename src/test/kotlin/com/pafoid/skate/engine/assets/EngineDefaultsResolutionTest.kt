package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.getComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** AST-27 (AST-26 §4.2, §4.4, §5.2): `engine:` references, overrides and the derived GUIDs. */
class EngineDefaultsResolutionTest {
    @TempDir
    lateinit var dir: File

    private val engine get() = File(dir, "engine")
    private val project get() = File(dir, "Game").also { ProjectLayout.create(it) }

    private fun engineFile(logical: String, text: String = "engine $logical") =
        File(engine, "assets/$logical").apply { parentFile.mkdirs(); writeText(text) }

    private fun override(logical: String, at: String = logical, text: String = "mine", copiedSha: String? = null): File {
        val file = File(ProjectLayout.assetsDir(project), at).apply { parentFile.mkdirs(); writeText(text) }
        val settings = mapOf(EngineDefaults.META_SOURCE to logical) + (copiedSha?.let { mapOf(EngineDefaults.META_SHA256 to it) } ?: emptyMap())
        AssetMeta.write(file, AssetMeta(guid = AssetId.generate(), settings = settings))
        return file
    }

    private fun paths(): AssetPaths = AssetPaths().apply {
        projectRoot = project
        engineDefaults = EngineDefaultResolver(engine) { projectRoot }
    }

    @Test
    fun Reference_RulesRejectEscapesDrivesAndBackslashes() {
        assertEquals("Models/cube.obj", EngineDefaults.logicalPath("engine:Models/cube.obj"))
        listOf("engine:", "engine:../x.png", "engine:/abs.png", "engine:C:/x.png", "engine:Models\\cube.obj", "engine:a//b", "Models/cube.obj")
            .forEach { assertNull(EngineDefaults.logicalPath(it), it) }
    }

    @Test
    fun Resolve_EngineFileWithoutOverride_ThenTheOverride_EvenMoved() {
        val source = engineFile("Models/cube.obj")
        val paths = paths()
        assertEquals(source.absolutePath, paths.absolute(EngineDefaults.Models.CUBE))

        val copy = override("Models/cube.obj")
        paths.engineDefaults.invalidate()
        assertEquals(copy.absolutePath, paths.absolute(EngineDefaults.Models.CUBE))

        val moved = File(ProjectLayout.assetsDir(project), "Models/props/my cube.obj")
        assertTrue(AssetRegistry(project).move(copy, moved))
        assertEquals(moved.absolutePath, paths.absolute(EngineDefaults.Models.CUBE), "a moved override stays in use")

        moved.delete()
        AssetMeta.sidecarOf(moved).delete()
        assertEquals(source.absolutePath, paths.absolute(EngineDefaults.Models.CUBE), "deleting the copy falls back")
    }

    @Test
    fun Relativize_MapsEngineFilesAndOverridesBackToTheReference() {
        val source = engineFile("Textures/asphalt.png")
        val copy = override("Models/cube.obj", at = "Models/cube (engine).obj")
        val paths = paths()

        assertEquals(EngineDefaults.Textures.ASPHALT, paths.relativize(source.absolutePath))
        assertEquals(EngineDefaults.Models.CUBE, paths.relativize(copy.absolutePath))
        assertEquals(EngineDefaults.Models.CUBE, paths.relativize(EngineDefaults.Models.CUBE))
        val mine = File(ProjectLayout.assetsDir(project), "Textures/rock.png").apply { parentFile.mkdirs(); writeText("x") }
        assertEquals("Assets/Textures/rock.png", paths.relativize(mine.absolutePath))
    }

    @Test
    fun SceneRoundTrip_KeepsEngineReferences() {
        engineFile("Models/cube.obj")
        val serializer = Serializer(paths())
        val scene = Scene("S").apply { addChild(GameObject("Box").addComponent(RenderComponent(`3dModel`(EngineDefaults.Models.CUBE)))) }

        val text = serializer.encodeFile(FileKind.SCENE, scene)
        assertTrue("\"engine:Models/cube.obj\"" in text, text)
        val loaded = serializer.decodeFile<Scene>(FileKind.SCENE, text)
        val path = loaded.children.single().getComponent<RenderComponent>()!!.model!!.path
        assertEquals(File(engine, "assets/Models/cube.obj").absolutePath, path, "loaded as the resolved file")
        assertTrue("\"engine:Models/cube.obj\"" in serializer.encodeFile(FileKind.SCENE, loaded), "saved back as the reference")
    }

    @Test
    fun UnchangedShaderCopy_FollowsTheEngine_ModifiedCopyOverrides() {
        val source = engineFile("Shaders/skybox.glsl", "// engine")
        val copy = override("Shaders/skybox.glsl", text = "// engine", copiedSha = EngineDefaultResolver.sha256("// engine".toByteArray()))
        val paths = paths()
        assertEquals(source.absolutePath, paths.absolute("engine:Shaders/skybox.glsl"), "unchanged copy is not an override")

        copy.writeText("// mine")
        assertEquals(copy.absolutePath, paths.absolute("engine:Shaders/skybox.glsl"))
    }

    @Test
    fun ClasspathFallback_ForShadersAndTheAppIcon() {
        val resolver = EngineDefaultResolver(File(dir, "no-engine"))
        assertTrue(resolver.resolve("engine:Shaders/shader_3d_default.glsl")!!.readText().isNotEmpty())
        assertTrue(resolver.resolve(EngineDefaults.Textures.APP_ICON)!!.length() > 0)
        assertNull(resolver.resolve(EngineDefaults.Models.CUBE))
    }

    @Test
    fun DuplicateOverrides_TheFirstByPathWins() {
        val a = override("Models/cube.obj", at = "Models/a/cube.obj")
        override("Models/cube.obj", at = "Models/b/cube.obj")
        assertEquals(a.absoluteFile, EngineDefaultResolver(engine) { project }.resolve(EngineDefaults.Models.CUBE))
    }

    @Test
    fun Registry_DerivedGuidIsStableAndResolvesBack() {
        val registry = AssetRegistry(project)
        val id = registry.idOf(EngineDefaults.Models.CUBE)!!
        assertEquals(id, AssetRegistry(File(dir, "other")).idOf(EngineDefaults.Models.CUBE))
        assertEquals(EngineDefaults.idOf(EngineDefaults.Models.CUBE), id)
        assertNotEquals(id, registry.idOf(EngineDefaults.Models.SPHERE))
        assertEquals(EngineDefaults.Models.CUBE, registry.pathOf(id))
        assertEquals(EngineDefaults.Models.CUBE, registry.resolve(id.value))
        assertNull(registry.fileOf(id))
    }

    @Test
    fun NoFileIsBuiltForAnUnresolvableReference() {
        val paths = AssetPaths().apply { engineDefaults = EngineDefaultResolver(File(dir, "no-engine")) }
        assertEquals(EngineDefaults.Models.CUBE, paths.localPath(EngineDefaults.Models.CUBE))
        assertNull(paths.readBytes(EngineDefaults.Models.CUBE))
        assertFalse(File(dir, "engine").exists())
    }
}
