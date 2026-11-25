package com.pafoid.skate.templates

import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.ecs.SceneSun
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.ProjectLayout
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.*
import org.joml.Vector3f
import org.joml.Vector4f
import com.pafoid.skate.engine.ui.UICanvasComponent
import com.pafoid.skate.engine.ui.UIElementComponent
import com.pafoid.skate.engine.ui.UITextComponent
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * The "Tetris" template (TLG-03, EDT-59): a 3D Tetris whose rules live in a Kotlin script
 * (`Assets/Scripts/tetris.kts`) driving a pool of cube entities, and a game-over scene. See docs/samples.md.
 */
object TetrisTemplate : ProjectTemplate {
    const val ID = "tetris"
    const val SCENE_NAME = "Game"
    const val SCRIPT_RESOURCE = "/samples/tetris/tetris.kts"
    const val SCRIPT_FILE = "tetris.kts"
    const val GAME_OVER_SCENE = "GameOver"
    const val GAME_OVER_RESOURCE = "/samples/tetris/gameover.kts"
    const val GAME_OVER_SCRIPT_FILE = "gameover.kts"
    private val SFX = mapOf("SfxLock" to 330.0, "SfxClear" to 660.0, "SfxOver" to 110.0)
    const val POOL_SIZE = 10 * 20 + 4

    override val id = ID
    override val category = TemplateCategory.SAMPLE
    override val icon = Icons.CUBE
    override val sceneNames = listOf(SCENE_NAME, GAME_OVER_SCENE)
    override val ownFiles = listOf("Assets/Scripts/$SCRIPT_FILE", "Assets/Scripts/$GAME_OVER_SCRIPT_FILE") +
        SFX.keys.map { "Assets/Sounds/$it.wav" }

    private val strings = listOf(
        "tetris.hud=Score %s   Lines %s   Level %s",
        "tetris.gameover.title=GAME OVER",
        "tetris.gameover.hint=Press R to play again",
    )

    override fun writeFiles(ctx: TemplateContext) {
        val scriptsDir = scriptsDir(ctx)
        copyResource(SCRIPT_RESOURCE, File(scriptsDir, SCRIPT_FILE))
        copyResource(GAME_OVER_RESOURCE, File(scriptsDir, GAME_OVER_SCRIPT_FILE))
        File(ctx.assetsDir, ProjectLayout.STRINGS_FILE).appendText(strings.joinToString("\n", postfix = "\n"))
        SFX.forEach { (name, hz) -> writeTone(File(ctx.assetsDir, "Sounds/$name.wav").also { it.parentFile.mkdirs() }, hz) }
    }

    override fun buildScenes(ctx: TemplateContext, prefabs: PrefabsGenerator): List<Scene> {
        val scriptsDir = scriptsDir(ctx)
        val assetsPrefix = ctx.assetsDir.absolutePath + File.separator
        return listOf(
            game(File(scriptsDir, SCRIPT_FILE).absolutePath, assetsPrefix),
            gameOver(File(scriptsDir, GAME_OVER_SCRIPT_FILE).absolutePath),
        )
    }

    override fun configure(project: Project) {
        project.description = "Tetris driven by a Kotlin script"
    }

    private fun scriptsDir(ctx: TemplateContext) = File(ctx.assetsDir, "Scripts").also { it.mkdirs() }

    private fun copyResource(resource: String, target: File) {
        val input = TetrisTemplate::class.java.getResourceAsStream(resource) ?: error("Missing resource $resource")
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
    }

    private fun cube(name: String, pos: Vector3f, half: Vector3f, color: Vector4f) = GameObject(name)
        .addComponent(Transform(pos, half))
        .addComponent(RenderComponent(`3dModel`(EngineDefaults.Models.CUBE), Material(baseColor = color)))

    private fun canvas(name: String) = GameObject(name).addComponent(UICanvasComponent())

    private fun text(name: String, key: String, y: Float, size: Float, args: List<String> = emptyList()) =
        GameObject(name)
            .addComponent(UIElementComponent().placed(0.5f, 0f, 0f, y, 1000f, size * 1.4f))
            .addComponent(UITextComponent(key, args, Vector4f(1f), size, alignX = 0.5f))

    private fun hud() = canvas("Hud").also {
        it.addChild(text("ScoreText", "tetris.hud", 40f, 48f, listOf("0", "0", "0")))
    }

    private fun gameOver(scriptPath: String): Scene {
        val scene = Scene(GAME_OVER_SCENE)
        scene.children.add(
            GameObject("Camera").addComponent(CameraComponent(isDefault = true)).addComponent(Transform(Vector3f(0f, 0f, 10f)))
        )
        scene.children.add(canvas("Menu").also {
            it.addChild(text("Title", "tetris.gameover.title", 380f, 120f))
            it.addChild(text("Hint", "tetris.gameover.hint", 560f, 56f))
        })
        scene.children.add(GameObject("GameOverController").addComponent(ScriptComponent(scriptPath)))
        return scene
    }

    private fun writeTone(file: File, hz: Double) {
        val rate = 22050
        val n = rate / 4
        val data = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(n) { data.putShort((sin(2 * PI * hz * it / rate) * 8000 * (1.0 - it.toDouble() / n)).toInt().toShort()) }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + data.capacity()).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(data.capacity())
        file.writeBytes(header.array() + data.array())
    }

    private fun game(scriptPath: String, assetsPrefix: String): Scene {
        val scene = Scene(SCENE_NAME)
        // Ambient lives in the environment and the sun is an entity (REN-26); no sky or fog, as before
        scene.addComponent(EnvironmentComponent(renderSky = false, renderFog = false, ambientIntensity = 0.4f))
        scene.children.add(SceneSun.create())
        scene.children.add(
            GameObject("Camera").addComponent(CameraComponent(isDefault = true))
                .addComponent(Transform(Vector3f(0f, 9.5f, 30f)))
        )
        val frame = Vector4f(0.3f, 0.3f, 0.35f, 1f)
        scene.children.add(cube("WallLeft", Vector3f(-5.9f, 9.5f, 0f), Vector3f(0.4f, 10.5f, 0.45f), frame))
        scene.children.add(cube("WallRight", Vector3f(5.9f, 9.5f, 0f), Vector3f(0.4f, 10.5f, 0.45f), frame))
        scene.children.add(cube("Floor", Vector3f(0f, -1f, 0f), Vector3f(6.3f, 0.4f, 0.45f), frame))

        val block = Vector4f(0.2f, 0.8f, 0.9f, 1f)
        repeat(POOL_SIZE) { scene.children.add(cube("Cell_$it", Vector3f(0f, -1000f, 0f), Vector3f(0.45f), block)) }

        SFX.forEach { (name, _) ->
            scene.children.add(
                GameObject(name).addComponent(Transform())
                    .addComponent(AudioComponent(assetsPrefix + "Sounds/$name.wav", is3D = false, volume = 0.6f, playOnAwake = false))
            )
        }
        scene.children.add(hud())
        scene.children.add(GameObject("TetrisController").addComponent(ScriptComponent(scriptPath)))
        return scene
    }
}
