package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.AmbientLightComponent
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.CylinderCollider3D
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.GridLines
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.ui.UIButtonComponent
import com.pafoid.skate.engine.ui.UICanvasComponent
import com.pafoid.skate.engine.ui.UIElementComponent
import com.pafoid.skate.engine.ui.UITextComponent
import com.pafoid.skate.game.prefabs.Floor
import com.pafoid.skate.game.prefabs.MaterialType
import com.pafoid.skate.game.prefabs.Skateboard
import com.pafoid.skate.game.prefabs.Skater
import org.joml.Vector3f
import java.io.File

class PrefabsGenerator(
    engine: Engine,
) {
    private val logger = engine.logger
    private val assetsManager = engine.assetsManager
    private val sceneManager = engine.sceneManager

    var projectAssetsDir: String = ""

    fun spawnSkateboard(): GameObject {
        val path = projectAssetsDir + Assets.Bundled.SKATEBOARD_GLB
        return Skateboard(assetsManager.loadModel(path))
    }

    fun spawnSkater(skate: GameObject? = null): Skater {
        val path = projectAssetsDir + Assets.Bundled.JAMES
        val model = assetsManager.loadModel(path)
        val animations = Skater.DEFAULT_ANIMATIONS.map { path ->
            assetsManager.loadAnimationSync(projectAssetsDir + path)
        }
        val skater = Skater("Skater", model, skate, animations = animations)
        skater.animator.resolveController(logger)
        return skater
    }

    fun spawnFloor(): GameObject {
        val texturePath = projectAssetsDir + Assets.Bundled.ASPHALT
        val modelPath = projectAssetsDir + Assets.Bundled.CUBE
        val texture = assetsManager.getTexture(texturePath)
        val material = Material(texture)
        val baseModel = assetsManager.loadModel(modelPath)
        val model = `3dModel`(path = modelPath, mesh = baseModel.mesh)
        return Floor("Floor", model, material)
    }

    fun spawnRail(position: Vector3f = Vector3f(), material: MaterialType?): GameObject {
        val modelPath = projectAssetsDir + Assets.Bundled.RAIL

        val mat = material ?: MaterialType.METAL
        val rail = GameObject("Rail")
        rail.addComponent(Transform(position))
        val baseModel = assetsManager.loadModel(modelPath)
        val texture = assetsManager.getTexture(projectAssetsDir + mat.texturePath)
        val model = `3dModel`(
            path = modelPath,
            mesh = baseModel.mesh.map { it.copy(material = Material(texture)) }
        )
        rail.addComponent(RenderComponent(model))
        rail.addComponent(RigidBody3D(0f, bodyType = BodyType.Static).apply { friction = 0.05f })
        rail.addComponent(CylinderCollider3D(radius = 0.05f, height = 2.0f, axis = 0))
        return rail
    }

    fun spawnLedge(position: Vector3f = Vector3f(0f, 0.25f, 0f), material: MaterialType?): GameObject {
        val modelPath = projectAssetsDir + Assets.Bundled.LEDGE
        val mat = material ?: MaterialType.CONCRETE
        val ledge = GameObject("${mat.displayName}_Ledge")
        ledge.addComponent(Transform(position))
        val baseModel = assetsManager.loadModel(modelPath)
        val texture = assetsManager.getTexture(projectAssetsDir + mat.texturePath)
        val model = `3dModel`(
            path = modelPath,
            mesh = baseModel.mesh.map { it.copy(material = Material(texture)) }
        )
        ledge.addComponent(RenderComponent(model = model))
        ledge.addComponent(RigidBody3D(0f).apply { friction = 0.6f; bodyType = BodyType.Static })
        ledge.addComponent(BoxCollider3D(Vector3f(0.5f, 0.25f, 0.5f)))
        return ledge
    }

    fun spawnKicker(position: Vector3f = Vector3f(), material: MaterialType?): GameObject {
        val modelPath = projectAssetsDir + Assets.Bundled.KICKER
        val mat = material ?: MaterialType.CONCRETE
        val kicker = GameObject("Kicker")
        kicker.addComponent(Transform(position))
        val baseModel = assetsManager.loadModel(modelPath)
        val texture = assetsManager.getTexture(projectAssetsDir + mat.texturePath)
        val model = `3dModel`(
            mesh = baseModel.mesh.map { it.copy(material = Material(texture)) },
            path = modelPath,
        )
        kicker.addComponent(RenderComponent(model = model))
        kicker.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = BodyType.Static })
        return kicker
    }

    fun spawnManualPad(position: Vector3f = Vector3f(), material: MaterialType?): GameObject {
        val modelPath = projectAssetsDir + Assets.Bundled.MANUAL_PAD
        val mat = material ?: MaterialType.CONCRETE
        val go = GameObject("ManualPad")
        go.addComponent(Transform(position))
        val baseModel = assetsManager.loadModel(modelPath)
        val model = `3dModel`(path = modelPath, mesh = baseModel.mesh)
        go.addComponent(RenderComponent(model, Material(baseColorTexture = assetsManager.getTexture(projectAssetsDir + mat.texturePath))))
        go.addComponent(RigidBody3D(0f).apply { friction = 0.6f; bodyType = BodyType.Static })
        go.addComponent(BoxCollider3D(Vector3f(1f, 0.1f, 1f)))
        return go
    }

    fun spawnBank(position: Vector3f = Vector3f(0f, 0f, 0f), material: MaterialType?): GameObject {
        val modelPath = projectAssetsDir + Assets.Bundled.BANK
        val go = GameObject("Bank")
        go.addComponent(Transform(position))
        val mat = material ?: MaterialType.CONCRETE
        val baseModel = assetsManager.loadModel(modelPath)
        val model = `3dModel`(path = modelPath, mesh = baseModel.mesh)
        go.addComponent(
            RenderComponent(
                model = model,
                Material(assetsManager.getTexture(projectAssetsDir + mat.texturePath)),
                castShadow = true,
                receiveShadow = true
            )
        )
        go.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = BodyType.Static })

        return go
    }

    fun spawnQuarterPipe(position: Vector3f = Vector3f(0f, 0f, 0f), material: MaterialType?): GameObject {
        val modelPath = projectAssetsDir + Assets.Bundled.QUARTER_PIPE
        val mat = material ?: MaterialType.CONCRETE
        val go = GameObject("QuarterPipe")
        go.addComponent(Transform(position))
        val baseModel = assetsManager.loadModel(modelPath)
        val model = `3dModel`(path = modelPath, mesh = baseModel.mesh)
        go.addComponent(RenderComponent(model, Material(assetsManager.getTexture(projectAssetsDir + mat.texturePath))))
        go.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = BodyType.Static })
        return go
    }

    /**
     * The scenes of a new project: a single `MainScene` with sky, sun, day/night cycle, the skater and board on an
     * asphalt floor, a concrete ledge to grind and a HUD canvas. See docs/samples.md ("Default project").
     */
    fun createDefaultScenes(sceneDir: File): List<Scene> {
        val scene = sceneManager.createNewScene(DEFAULT_SCENE, sceneDir.path)
        val skyTexture = assetsManager.getTexture(projectAssetsDir + Assets.Bundled.SKY)
        scene.addComponent(ScenePhysicsComponent())
            .addComponent(GridLines())
            .addComponent(EnvironmentComponent(skyTexture = skyTexture))
            .addComponent(AmbientLightComponent())
            .addComponent(DayNightCycleComponent())
            .addComponent(DirectionalLightComponent())

        spawnDefaultsSync().forEach { scene.addChild(it) }
        sceneManager.saveScene(scene, sceneDir.path)

        // Not opened here: ProjectManager.loadDefaultScenes reopens the saved files (opening twice duplicated bodies).
        return listOf(scene)
    }

    fun spawnDefaultsSync(): List<GameObject> {
        val camera = GameObject("Camera")
            .addComponent(CameraComponent(isDefault = true))
            .addComponent(Transform(Vector3f(0f, 3f, 10f)))
        val skater = spawnSkater()
        val skate = spawnSkateboard()
        val floor = spawnFloor()
        val ledge = spawnLedge(Vector3f(3f, 0.25f, -3f), MaterialType.CONCRETE)
        return listOf(camera, skate, skater, floor, ledge, spawnHud())
    }

    /** In-game (non-ImGui) HUD: a score line top-left and a restart button top-right. */
    fun spawnHud(): GameObject {
        val canvas = GameObject("HUD").addComponent(UICanvasComponent())
        canvas.addChild(
            GameObject("ScoreText")
                .addComponent(UIElementComponent().placed(0f, 0f, 40f, 30f, 500f, 60f))
                .addComponent(UITextComponent("hud.score", listOf("0"), fontSize = 44f))
        )
        canvas.addChild(
            GameObject("RestartButton")
                .addComponent(UIElementComponent().placed(1f, 0f, -40f, 30f, 260f, 70f))
                .addComponent(UIButtonComponent("pause.restart"))
        )
        return canvas
    }

    companion object {
        const val DEFAULT_SCENE = "MainScene"
    }
}
