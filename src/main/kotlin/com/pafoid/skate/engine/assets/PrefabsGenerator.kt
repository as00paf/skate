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
import com.pafoid.skate.game.prefabs.Floor
import com.pafoid.skate.game.prefabs.MaterialType
import com.pafoid.skate.game.prefabs.Skateboard
import com.pafoid.skate.game.prefabs.Skater
import org.joml.Vector3f
import java.io.File

class PrefabsGenerator(
    engine: Engine,
) {
    private val assetsManager = engine.assetsManager
    private val sceneManager = engine.sceneManager

    var projectAssetsDir: String = ""

    fun spawnSkateboard(): GameObject {
        val path = projectAssetsDir + Assets.Bundled.SKATEBOARD_GLB
        return Skateboard(assetsManager.loadModel(path))
    }

    fun spawnSkater(skate: GameObject? = null): GameObject {
        val path = projectAssetsDir + Assets.Bundled.JAMES
        val model = assetsManager.loadModel(path)
        val animations = Skater.DEFAULT_ANIMATIONS.map { path ->
            assetsManager.loadAnimationSync(projectAssetsDir + path)
        }
        val skater = Skater("Skater", model, skate, animations = animations)
        return skater
    }

    fun spawnFloor(): GameObject {
        val texturePath = projectAssetsDir + Assets.Bundled.ASPHALT
        val modelPath = projectAssetsDir + Assets.Bundled.CUBE
        val texture = assetsManager.getTexture(texturePath)
        val material = Material(texture)
        val baseModel = assetsManager.loadModel(modelPath)
        val model = `3dModel`(path = modelPath, mesh = baseModel.mesh)
        return Floor("Tile", model, material)
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
        val texture = assetsManager.getTexture(mat.texturePath)
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
        val texture = assetsManager.getTexture(mat.texturePath)
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
        go.addComponent(RenderComponent(model, Material(baseColorTexture = assetsManager.getTexture(mat.texturePath))))
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
                Material(assetsManager.getTexture(mat.texturePath)),
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
        go.addComponent(RenderComponent(model, Material(assetsManager.getTexture(mat.texturePath))))
        go.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = BodyType.Static })
        return go
    }

    fun createDefaultScenes(sceneDir: File): List<Scene> {
        val scene = sceneManager.createNewScene("MainScene", sceneDir.path)
        val skyTexture = assetsManager.getTexture(projectAssetsDir + Assets.Bundled.SKY)
        scene.addComponent(ScenePhysicsComponent())
            .addComponent(GridLines())
            .addComponent(EnvironmentComponent(skyTexture = skyTexture))
            .addComponent(AmbientLightComponent())
            .addComponent(DayNightCycleComponent())
            .addComponent(DirectionalLightComponent())
            .addComponent(CameraComponent(Vector3f(0f, 5f, 20f)))
        scene.gameObjects.addAll(spawnDefaultsSync())
        sceneManager.saveScene(scene, sceneDir.path)

        val openGlScene = sceneManager.createNewScene("OpenGLTest", sceneDir.path)
        openGlScene
            .addComponent(GridLines())
            .addComponent(AmbientLightComponent())
        sceneManager.saveScene(openGlScene, sceneDir.path)

        val result = listOf(scene, openGlScene)
        sceneManager.openScenes(result)

        return result
    }

    fun spawnDefaultsSync(): List<GameObject> {
        val skate = spawnSkateboard()
        val skater = spawnSkater()
        val floor = spawnFloor()
        return listOfNotNull(skate, skater, floor)
    }
}