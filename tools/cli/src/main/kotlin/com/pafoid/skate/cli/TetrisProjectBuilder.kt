package com.pafoid.skate.cli

import com.pafoid.skate.editor.project.EngineAssetCopier
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.*
import org.joml.Vector2i
import org.joml.Vector3f
import org.joml.Vector4f
import java.io.File

/**
 * Builds the "Tetris" sample project (TLG-03): a 3D Tetris whose rules live in a Kotlin script
 * (`Assets/Scripts/tetris.kts`) driving a pool of cube entities. See docs/samples.md.
 */
class TetrisProjectBuilder(private val serializer: Serializer = Serializer()) {

    companion object {
        const val PROJECT_NAME = "Tetris"
        const val SCENE_NAME = "Game"
        const val SCRIPT_RESOURCE = "/samples/tetris/tetris.kts"
        const val SCRIPT_FILE = "tetris.kts"
        const val POOL_SIZE = 10 * 20 + 4
    }

    private lateinit var assetsPrefix: String

    fun build(parent: File): File {
        val projectDir = File(parent, PROJECT_NAME).also { it.mkdirs() }
        val assetsDir = File(projectDir, "Assets").also { it.mkdirs() }
        val scenesDir = File(projectDir, "Scenes").also { it.mkdirs() }
        File(projectDir, "Builds").mkdirs()
        EngineAssetCopier().copyBundledAssets(projectDir)
        assetsPrefix = assetsDir.absolutePath + File.separator

        val scriptsDir = File(assetsDir, "Scripts").also { it.mkdirs() }
        val script = TetrisProjectBuilder::class.java.getResourceAsStream(SCRIPT_RESOURCE)
            ?: error("Missing resource $SCRIPT_RESOURCE")
        File(scriptsDir, SCRIPT_FILE).outputStream().use { script.copyTo(it) }

        val scenePath = File(scenesDir, "$SCENE_NAME.scene")
            .also { it.writeText(serializer.encode(game(File(scriptsDir, SCRIPT_FILE).absolutePath))) }.path
        val projectFile = File(projectDir, "$PROJECT_NAME.skateproject")
        projectFile.writeText(serializer.encode(Project(
            name = PROJECT_NAME,
            projectPath = projectFile.path,
            description = "Tetris driven by a Kotlin script",
            defaultScene = scenePath,
            scenesPath = listOf(scenePath),
            resolution = Vector2i(1920, 1080),
        )))
        return projectFile
    }

    private fun cube(name: String, pos: Vector3f, half: Vector3f, color: Vector4f) = GameObject(name)
        .addComponent(Transform(pos, half))
        .addComponent(RenderComponent(`3dModel`(assetsPrefix + Assets.Bundled.CUBE), Material(baseColor = color)))

    private fun game(scriptPath: String): Scene {
        val scene = Scene(SCENE_NAME)
        scene.addComponent(AmbientLightComponent(intensity = 0.4f)).addComponent(DirectionalLightComponent())
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

        scene.children.add(GameObject("TetrisController").addComponent(ScriptComponent(scriptPath)))
        return scene
    }
}
