package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.ParticleEmitter
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.ScriptComponent
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.material.MaterialAsset
import com.pafoid.skate.engine.render.material.MaterialSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AssetPathsTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun AssetPaths_RelativizesOnlyPathsInsideTheProject() {
        val root = File(dir, "Game").also { it.mkdirs() }
        val inside = File(root, "Assets/Textures/rock.png").absolutePath
        assertEquals("Assets/Textures/rock.png", AssetPaths.relativize(inside, root))
        assertEquals("Assets/Textures/rock.png", AssetPaths.relativize(File(root, "Assets/../Assets/Textures/rock.png").path, root))
        val outside = File(dir, "Other/rock.png").absolutePath
        assertEquals(outside, AssetPaths.relativize(outside, root))
        assertEquals("assets/models/cube.obj", AssetPaths.relativize("assets/models/cube.obj", root), "already relative")
        assertEquals(inside, AssetPaths.relativize(inside, null), "no project open")
    }

    @Test
    fun AssetPaths_AbsoluteOnlyForFilesThatExistInTheProject() {
        val root = File(dir, "Game")
        val rock = File(root, "Assets/rock.png").also { it.parentFile.mkdirs(); it.writeText("x") }
        assertEquals(rock.absoluteFile.normalize().path, AssetPaths.absolute("Assets/rock.png", root))
        assertEquals(rock.absoluteFile.normalize().path, AssetPaths.absolute("Assets\\rock.png", root))
        assertEquals("idle", AssetPaths.absolute("idle", root), "plain strings are not paths")
        assertEquals("assets/models/cube.obj", AssetPaths.absolute("assets/models/cube.obj", root), "engine paths keep working")
        assertEquals("C:\\elsewhere\\a.png", AssetPaths.absolute("C:\\elsewhere\\a.png", root))
    }

    @Test
    fun AssetPaths_IsAbsoluteRecognisesWindowsAndUnixForms() {
        listOf("C:\\a\\b.png", "c:/a/b.png", "/home/a.png", "\\\\server\\share\\a.png").forEach { assertTrue(AssetPaths.isAbsolute(it), it) }
        listOf("Assets/a.png", "a.png", "..\\a.png", "").forEach { assertFalse(AssetPaths.isAbsolute(it), it) }
    }

    @Test
    fun AssetPaths_RewriteTouchesPathKeysOnlyAndRecursesIntoObjectsAndLists() {
        val json = Json.parseToJsonElement(
            """{"name":"C:/p/x.png","children":[{"components":[{"path":"C:/p/m.obj","clips":["C:/p/a.ogg","C:/p/b.ogg"]}]}],
               "skyTexture":{"filePath":"C:/p/sky.png","width":2}}"""
        )
        val out = AssetPaths.rewrite(json) { it.removePrefix("C:/p/") }.jsonObject
        assertEquals("C:/p/x.png", out["name"]!!.jsonPrimitive.content, "not a path key")
        val c = out["children"]!!.jsonArray[0].jsonObject["components"]!!.jsonArray[0].jsonObject
        assertEquals("m.obj", c["path"]!!.jsonPrimitive.content)
        assertEquals(listOf("a.ogg", "b.ogg"), c["clips"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("sky.png", out["skyTexture"]!!.jsonObject["filePath"]!!.jsonPrimitive.content)
        assertEquals(2, out["skyTexture"]!!.jsonObject["width"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun AssetPaths_ReadsProjectFilesThroughRelativePaths() {
        val root = File(dir, "Game")
        File(root, "Configs/skater.json").also { it.parentFile.mkdirs(); it.writeText("{}") }
        val paths = AssetPaths().apply { projectRoot = root }
        assertEquals("{}", paths.readText("Configs/skater.json"))
        assertNull(paths.readText("Configs/missing.json"))
        assertEquals(File(root, "Configs/skater.json").absoluteFile.normalize().path, paths.localPath("Configs/skater.json"))
    }

    /** Every kind of saved reference, pointing at real files inside [root]. */
    private fun files(root: File): Map<String, File> = listOf(
        "model" to "Assets/Models/board.obj",
        "grip" to "Assets/Textures/grip.png",
        "sky" to "Assets/Textures/sky.png",
        "spark" to "Assets/Textures/spark.png",
        "song" to "Assets/Music/song.ogg",
        "sfx" to "Assets/Sounds/pop.wav",
        "clip" to "Assets/Animations/idle.fbx",
        "controller" to "Assets/Controllers/skater.controller.json",
        "material" to "Assets/Materials/rock.skmat",
        "script" to "Scripts/spin.kts",
        "config" to "Configs/skater.json",
    ).associate { (k, rel) -> k to File(root, rel).also { it.parentFile.mkdirs(); it.writeText(k) } }

    private fun scene(f: Map<String, File>): Scene {
        val p = { key: String -> f.getValue(key).absolutePath }
        val board = GameObject("Board")
            .addComponent(RenderComponent(`3dModel`(p("model")), Material(baseColorTexture = Texture(filePath = p("grip"))), materialPath = p("material")))
            .addComponent(Animator(mutableListOf(Animation("idle", duration = 1f, path = p("clip")))).apply { controllerPath = p("controller") })
            .addComponent(AudioComponent(soundFilePath = p("song")).apply { clips = listOf(p("sfx")) })
            .addComponent(ScriptComponent(path = p("script")))
            .addComponent(ParticleEmitter().apply { texturePath = p("spark") })
            .addComponent(PlayerController(configPath = p("config")))
        return Scene("Main").apply {
            addComponent(EnvironmentComponent(skyTexture = Texture(filePath = p("sky"))))
            addChild(board)
        }
    }

    @Test
    fun SavedScene_SurvivesMovingTheProjectFolder() {
        val before = File(dir, "Before/Game")
        val f = files(before)
        MaterialAsset(
            name = "Rock",
            params = mapOf(MaterialSchema.Companion.Param.BASE_COLOR to listOf(0.2f, 0.4f, 0.6f, 1f)),
            textures = mapOf(MaterialSchema.Companion.Tex.BASE_COLOR to "Assets/Textures/grip.png"),
        ).save(f.getValue("material"))

        val saved = Serializer(AssetPaths().apply { projectRoot = before }).encodeFile(FileKind.SCENE, scene(f))
        assertFalse(saved.contains(before.absolutePath.replace("\\", "\\\\")), "no absolute project paths in the saved scene")
        File(before, "Scenes/Main.scene").also { it.parentFile.mkdirs() }.writeText(saved)

        val after = File(dir, "Moved/Somewhere/Game")
        before.copyRecursively(after)
        before.parentFile.deleteRecursively()

        val paths = AssetPaths().apply { projectRoot = after }
        val loaded = Serializer(paths).decodeFile<Scene>(FileKind.SCENE, File(after, "Scenes/Main.scene").readText())
        val inAfter = { path: String? ->
            assertNotNull(path)
            val file = File(path!!)
            assertTrue(file.isAbsolute && file.isFile && file.startsWith(after.absoluteFile), "$path is not a file in the moved project")
        }
        val board = loaded.children.single()
        val render = board.getComponent<RenderComponent>()!!
        inAfter(render.model?.path); inAfter(render.material?.baseColorTexture?.filePath); inAfter(render.materialPath)
        val animator = board.getComponent<Animator>()!!
        inAfter(animator.controllerPath); inAfter(animator.animations.single().path)
        val audio = board.getComponent<AudioComponent>()!!
        inAfter(audio.soundFilePath); audio.clips.forEach(inAfter)
        inAfter(board.getComponent<ScriptComponent>()!!.path)
        inAfter(board.getComponent<ParticleEmitter>()!!.texturePath)
        inAfter(board.getComponent<PlayerController>()!!.configPath)
        inAfter(loaded.getComponent<EnvironmentComponent>()!!.skyTexture?.filePath)

        // The linked .skmat and the controller load from the moved folder, textures included.
        assertNull(render.resolveMaterialAsset(paths::readText) { Texture(filePath = paths.absolute(it)) })
        inAfter(render.material?.baseColorTexture?.filePath)
        assertEquals(0.4f, render.material!!.baseColor.y, 1e-6f)
        assertEquals("controller", paths.readText(animator.controllerPath!!))
    }

    @Test
    fun SavedScene_JsonStillParsesWithoutAProject() {
        val root = File(dir, "Game")
        val saved = Serializer(AssetPaths().apply { projectRoot = root }).encodeFile(FileKind.SCENE, scene(files(root)))
        val loaded = Serializer().decodeFile<Scene>(FileKind.SCENE, saved)
        assertEquals("Assets/Models/board.obj", loaded.children.single().getComponent<RenderComponent>()!!.model?.path,
            "an exported game keeps references relative and reads them from the pack")
        val element = Json.parseToJsonElement(saved)
        assertTrue(element is JsonObject)
    }
}
