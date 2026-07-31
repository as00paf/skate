package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.CylinderCollider3D
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.GridLines
import com.pafoid.skate.engine.ecs.components.LightingStateComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.render.CameraComponent
import com.pafoid.skate.game.prefabs.MaterialType
import com.pafoid.skate.game.prefabs.Skateboard
import com.pafoid.skate.game.prefabs.Skater
import com.pafoid.skate.game.prefabs.Tile
import org.joml.Vector3f
import java.io.File

class PrefabsGenerator(
    engine: Engine
) {
    private val assetsManager = engine.assetsManager
    private val sceneManager = engine.sceneManager

    fun spawnSkateboard(): GameObject {
        return Skateboard(assetsManager.loadModel(Assets.Models.SKATEBOARD_GLB))
    }

    fun spawnSkater(skate: GameObject? = null): GameObject {
        val model = assetsManager.loadModel(Assets.Models.JAMES)
        val skater = Skater("Skater", model, skate)
        val skeleton = skater.getComponent<SkeletonComponent>()?.pose?.skeleton
        val animator = skater.getComponent<Animator>()
        if (skeleton != null && animator != null) {
            Skater.DEFAULT_ANIMATIONS.forEach { path ->
                animator.addAnimation(assetsManager.loadAnimationSync(path, skeleton))
            }
        }
        return skater
    }

    fun spawnFloor(): GameObject {
        val texture = assetsManager.getTexture(Assets.Textures.ASPHALT)
        val baseModel = assetsManager.loadModel(Assets.Models.CUBE)
        val model = TexturedModel(
            path = Assets.Models.CUBE,
            mesh = baseModel.mesh.map { it.copy(material = Material(texture)) }
        )
        return Tile("Tile", model)
    }

    fun spawnRail(position: Vector3f = Vector3f(), material: MaterialType?): GameObject {
        val mat = material ?: MaterialType.METAL
        val rail = GameObject("Rail")
        rail.addComponent(Transform(position))
        val baseModel = assetsManager.loadModel(Assets.Models.RAIL)
        val texture = assetsManager.getTexture(mat.texturePath)
        val model = TexturedModel(
            path = Assets.Models.RAIL,
            mesh = baseModel.mesh.map { it.copy(material = Material(texture)) }
        )
        rail.addComponent(RenderComponent(model))
        rail.addComponent(RigidBody3D(0f).apply { friction = 0.05f; bodyType = BodyType.Static })
        rail.addComponent(CylinderCollider3D(radius = 0.05f, height = 2.0f, axis = 0))
        return rail
    }

    fun spawnLedge(position: Vector3f = Vector3f(0f, 0.25f, 0f), material: MaterialType?): GameObject {
        val mat = material ?: MaterialType.CONCRETE
        val ledge = GameObject("${mat.displayName}_Ledge")
        ledge.addComponent(Transform(position))
        val baseModel = assetsManager.loadModel(Assets.Models.LEDGE)
        val texture = assetsManager.getTexture(mat.texturePath)
        val model = TexturedModel(
            path = Assets.Models.LEDGE,
            mesh = baseModel.mesh.map { it.copy(material = Material(texture)) }
        )
        ledge.addComponent(RenderComponent(model = model))
        ledge.addComponent(RigidBody3D(0f).apply { friction = 0.6f; bodyType = BodyType.Static })
        ledge.addComponent(BoxCollider3D(Vector3f(0.5f, 0.25f, 0.5f)))
        return ledge
    }

    fun spawnKicker(position: Vector3f = Vector3f(), material: MaterialType?): GameObject {
        val mat = material ?: MaterialType.CONCRETE
        val kicker = GameObject("Kicker")
        kicker.addComponent(Transform(position))
        val baseModel = assetsManager.loadModel(Assets.Models.KICKER)
        val texture = assetsManager.getTexture(mat.texturePath)
        val texturedModel = TexturedModel(
            mesh = baseModel.mesh.map { it.copy(material = Material(texture)) },
            path = Assets.Models.KICKER,
        )
        kicker.addComponent(RenderComponent(model = texturedModel))
        kicker.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = BodyType.Static })
        return kicker
    }

    fun spawnManualPad(position: Vector3f = Vector3f(), material: MaterialType?): GameObject {
        val mat = material ?: MaterialType.CONCRETE
        val go = GameObject("ManualPad")
        go.addComponent(Transform(position))
        val baseModel = assetsManager.loadModel(Assets.Models.MANUAL_PAD)
        val texturedModel = TexturedModel(
            path = Assets.Models.MANUAL_PAD,
            mesh = baseModel.mesh,
            material = Material(baseColorTexture = assetsManager.getTexture(mat.texturePath))
        )
        go.addComponent(RenderComponent(model = texturedModel))
        go.addComponent(RigidBody3D(0f).apply { friction = 0.6f; bodyType = BodyType.Static })
        go.addComponent(BoxCollider3D(Vector3f(1f, 0.1f, 1f)))
        return go
    }

    fun spawnBank(position: Vector3f = Vector3f(0f, 0f, 0f), material: MaterialType?): GameObject {
        val go = GameObject("Bank")
        go.addComponent(Transform(position))
        val mat = material ?: MaterialType.CONCRETE
        val baseModel = assetsManager.loadModel(Assets.Models.BANK)
        val texturedModel = TexturedModel(
            path = Assets.Models.BANK,
            mesh = baseModel.mesh,
            material = Material(assetsManager.getTexture(mat.texturePath))
        )
        go.addComponent(
            RenderComponent(model = texturedModel, castShadow = true, receiveShadow = true)
        )
        go.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = BodyType.Static })

        return go
    }


    fun spawnQuarterPipe(position: Vector3f = Vector3f(0f, 0f, 0f), material: MaterialType?): GameObject {
        val mat = material ?: MaterialType.CONCRETE
        val go = GameObject("QuarterPipe")
        go.addComponent(Transform(position))
        val baseModel = assetsManager.loadModel(Assets.Models.QUARTER_PIPE)
        val texturedModel = TexturedModel(
            path = Assets.Models.QUARTER_PIPE,
            mesh = baseModel.mesh,
            material = Material(assetsManager.getTexture(mat.texturePath))
        )
        go.addComponent(RenderComponent(model = texturedModel))
        go.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = BodyType.Static })

        return go
    }

    fun createDefaultScene(sceneDir: File) {
        val scene = sceneManager.createNewScene("MainScene", sceneDir.path)
        scene.addComponent(ScenePhysicsComponent())
            .addComponent(GridLines())
            .addComponent(EnvironmentComponent())
            .addComponent(LightingStateComponent())
            .addComponent(DayNightCycleComponent())
            .addComponent(DirectionalLightComponent())
            .addComponent(CameraComponent().also { it.position.set(Vector3f(0f, 5f, 20f)) })
        scene.gameObjects.addAll(spawnDefaultsSync())
        sceneManager.saveScene(scene, sceneDir.path)
        sceneManager.openScene(scene)
    }

    fun spawnDefaultsSync(): List<GameObject> {
        val skate = spawnSkateboard()
        val skater = spawnSkater()
        val floor = spawnFloor()
        return listOfNotNull(skate, skater, floor)
    }
}