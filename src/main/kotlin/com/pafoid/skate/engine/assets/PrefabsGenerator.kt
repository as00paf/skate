package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.data.Sprite
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.CylinderCollider3D
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.game.prefabs.MaterialType
import com.pafoid.skate.game.prefabs.Skateboard
import com.pafoid.skate.game.prefabs.Skater
import com.pafoid.skate.game.prefabs.Tile
import org.joml.Vector3f
import java.io.File

class PrefabsGenerator(
    private val engine: Engine
) {
    private val assetsManager = engine.assetsManager
    private val sceneManager = engine.sceneManager
    private val gameObjectManager = engine.gameObjectManager

    /** Root path for engine-bundled assets copied into the project (null = use engine paths) */
    private var engineDefaultsRoot: String? = null

    // TODO: remove
    private val resolvedModelPaths = mutableMapOf<String, String>()
    private val resolvedTexturePaths = mutableMapOf<String, String>()

    /**
     * Set the root path for engine-bundled default assets.
     * Called by ProjectManager after copying assets into the project.
     */
    fun setEngineDefaultsRoot(projectDir: File) {
        engineDefaultsRoot = File(projectDir, "Assets/EngineDefaults").absolutePath
    }

    /** Resolve a model path — use cached result, project copy, or fall back to engine path */
    // TODO: remove
    private fun resolveModelPath(enginePath: String): String {
        resolvedModelPaths[enginePath]?.let { return it }
        val root = engineDefaultsRoot ?: return enginePath.also { resolvedModelPaths[enginePath] = it }
        val fileName = File(enginePath).name
        val result = listOf("Models", "Characters").firstNotNullOfOrNull { dir ->
            val candidate = File(root, "$dir/$fileName")
            if (candidate.exists()) candidate.absolutePath else null
        } ?: enginePath
        resolvedModelPaths[enginePath] = result
        return result
    }

    /** Resolve a texture path — use cached result, project copy, or fall back to engine path */
    private fun resolveTexturePath(enginePath: String): String {
        resolvedTexturePaths[enginePath]?.let { return it }
        val root = engineDefaultsRoot ?: return enginePath.also { resolvedTexturePaths[enginePath] = it }
        val fileName = File(enginePath).name
        val candidate = File(root, "Textures/$fileName")
        val result = if (candidate.exists()) candidate.absolutePath else enginePath
        resolvedTexturePaths[enginePath] = result
        return result
    }

    fun generateSpriteObject(sprite: Sprite, sizeX: Float, sizeY: Float, name: String = "Sprite_Object_Gen"): GameObject {
        val go = gameObjectManager.createGameObject(name)
        go.getComponent<Transform>()?.scale?.set(sizeX, sizeY, 1f)

        val renderer = SpriteRenderer()
        renderer.sprite = sprite
        go.addComponent(renderer)

        return go
    }

    fun spawnSkateboard(): GameObject {
        val modelPath = resolveModelPath(Assets.Models.SKATEBOARD_GLB)
        val model = assetsManager.loadModel(modelPath)
        val skate = Skateboard(model)

        gameObjectManager.addGameObject(skate)
        return skate
    }

    fun spawnSkater(skate: GameObject? = null): GameObject {
        val modelPath = resolveModelPath(Assets.Models.JAMES)
        val model = assetsManager.getModel(modelPath)
            ?: assetsManager.loadModel(modelPath)
        val skater = Skater("Skater", model, skate)
        val skeleton = skater.getComponent<SkeletonComponent>()?.pose?.skeleton
        val animator = skater.getComponent<Animator>()

        if (skeleton != null && animator != null) {
            Skater.DEFAULT_ANIMATIONS.forEach { path ->
                animator.addAnimation(assetsManager.loadAnimationSync(path, skeleton))
            }
        }

        gameObjectManager.addGameObject(skater)
        return skater
    }

    fun spawnFloor(): GameObject {
        val texturePath = resolveTexturePath(Assets.Textures.ASPHALT)
        val modelPath = resolveModelPath(Assets.Models.CUBE)
        val texture = assetsManager.getTexture(texturePath)
        val baseModel = assetsManager.loadModel(modelPath)
        val model = TexturedModel(
            path = modelPath,
            mesh = baseModel.mesh.map { it.copy(material = Material(texture)) }
        )

        val tile = Tile("Tile", model)

        gameObjectManager.addGameObject(tile)
        return tile
    }

    fun spawnRail(position: Vector3f = Vector3f(0f, 0.5f, 0f), material: MaterialType?): GameObject? {
        val scene = sceneManager.currentScene ?: return null
        val rail = GameObject("Rail_${scene.gameObjects.size}")
        val transformComponent = Transform()
        transformComponent.translation.set(position)
        transformComponent.scale.set(1f, 1f, 1f)
        rail.addComponent(transformComponent)
        val mat = material ?: MaterialType.METAL
        val baseModel = assetsManager.loadModel(Assets.Models.RAIL)
        val texture = assetsManager.getTexture(mat.texturePath)
        val model = TexturedModel(
            path = Assets.Models.RAIL,
            mesh = baseModel.mesh.map { it.copy(material = Material(texture)) }
        )
        model.mesh[0].material.baseColorTexture = texture
        val renderComponent = RenderComponent(
            model = model,
            castShadow = true,
            receiveShadow = true,

            )
        rail.addComponent(renderComponent)
        rail.addComponent(RigidBody3D(0f).apply { friction = 0.05f; bodyType = BodyType.Static })
        rail.addComponent(CylinderCollider3D(radius = 0.05f, height = 2.0f, axis = 0))
        gameObjectManager.addGameObject(rail)
        return rail
    }

    fun spawnLedge(position: Vector3f = Vector3f(0f, 0.25f, 0f), material: MaterialType?): GameObject? {
        val scene = sceneManager.currentScene ?: return null
        val mat = material ?: MaterialType.CONCRETE
        val ledge = GameObject("${mat.displayName}_Ledge_${scene.gameObjects.size}")
        val transformComponent = Transform()
        transformComponent.translation.set(position)
        transformComponent.scale.set(1f, 1f, 1f)
        ledge.addComponent(transformComponent)
        val baseModel = assetsManager.loadModel(Assets.Models.LEDGE)
        val texture = assetsManager.getTexture(mat.texturePath)
        val model = TexturedModel(
            path = Assets.Models.LEDGE,
            mesh = baseModel.mesh.map { it.copy(material = Material(texture)) }
        )

        model.mesh[0].material.baseColorTexture = texture
        val renderComponent = RenderComponent(
            model = model,
            castShadow = true,
            receiveShadow = true
        )
        ledge.addComponent(renderComponent)

        ledge.addComponent(RigidBody3D(0f).apply { friction = 0.6f; bodyType = BodyType.Static })
        ledge.addComponent(BoxCollider3D(Vector3f(0.5f, 0.25f, 0.5f)))
        gameObjectManager.addGameObject(ledge)
        return ledge
    }

    fun spawnKicker(position: Vector3f = Vector3f(0f, 0f, 0f), material: MaterialType?): GameObject? {
        val scene = sceneManager.currentScene ?: return null
        val kicker = GameObject("Kicker_${scene.gameObjects.size}")
        val transformComponent = Transform()
        transformComponent.translation.set(position)
        transformComponent.scale.set(1f, 1f, 1f)
        kicker.addComponent(transformComponent)
        val mat = material ?: MaterialType.CONCRETE
        val baseModel = assetsManager.loadModel(Assets.Models.KICKER)
        val texture = assetsManager.getTexture(mat.texturePath)
        val texturedModel = TexturedModel(
            mesh = baseModel.mesh.map { it.copy(material = Material(texture)) },
            path = Assets.Models.KICKER,
        )
        kicker.addComponent(
            RenderComponent(
                model = texturedModel,
                castShadow = true,
                receiveShadow = true
            )
        )
        kicker.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = BodyType.Static })

        gameObjectManager.addGameObject(kicker)
        return kicker
    }

    fun spawnManualPad(position: Vector3f = Vector3f(0f, 0.1f, 0f), material: MaterialType?): GameObject? {
        val scene = sceneManager.currentScene ?: return null
        val go = GameObject("ManualPad_${scene.gameObjects.size}")
        val transformComponent = Transform()
        transformComponent.translation.set(position)
        go.addComponent(transformComponent)
        val mat = material ?: MaterialType.CONCRETE
        val baseModel = assetsManager.loadModel(Assets.Models.MANUAL_PAD)
        val texturedModel = TexturedModel(
            path = Assets.Models.MANUAL_PAD,
            mesh = baseModel.mesh,
            material = Material(baseColorTexture = assetsManager.getTexture(mat.texturePath))
        )
        go.addComponent(
            RenderComponent(
                model = texturedModel,
                castShadow = true,
                receiveShadow = true
            )
        )
        go.addComponent(RigidBody3D(0f).apply { friction = 0.6f; bodyType = BodyType.Static })
        go.addComponent(BoxCollider3D(Vector3f(1f, 0.1f, 1f)))
        gameObjectManager.addGameObject(go)
        return go
    }


    fun spawnBank(position: Vector3f = Vector3f(0f, 0f, 0f), material: MaterialType?): GameObject? {
        val scene = sceneManager.currentScene ?: return null
        val go = GameObject("Bank_${scene.gameObjects.size}")
        val transformComponent = Transform()
        transformComponent.translation.set(position)
        go.addComponent(transformComponent)
        val mat = material ?: MaterialType.CONCRETE
        val baseModel = assetsManager.loadModel(Assets.Models.BANK)
        val texturedModel = TexturedModel(
            path = Assets.Models.BANK,
            mesh = baseModel.mesh,
            material = Material(assetsManager.getTexture(mat.texturePath))
        )
        go.addComponent(
            RenderComponent(
                model = texturedModel,
                castShadow = true,
                receiveShadow = true
            )
        )
        go.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = BodyType.Static })

        gameObjectManager.addGameObject(go)
        return go
    }


    fun spawnQuarterPipe(position: Vector3f = Vector3f(0f, 0f, 0f), material: MaterialType?): GameObject? {
        val scene = sceneManager.currentScene ?: return null
        val go = GameObject("QuarterPipe_${scene.gameObjects.size}")
        val transformComponent = Transform()
        transformComponent.translation.set(position)
        go.addComponent(transformComponent)
        val mat = material ?: MaterialType.CONCRETE
        val baseModel = assetsManager.loadModel(Assets.Models.QUARTER_PIPE)
        val texturedModel = TexturedModel(
            path = Assets.Models.QUARTER_PIPE,
            mesh = baseModel.mesh,
            material = Material(assetsManager.getTexture(mat.texturePath))
        )
        go.addComponent(
            RenderComponent(
                model = texturedModel,
                castShadow = true,
                receiveShadow = true
            )
        )
        go.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = BodyType.Static })

        gameObjectManager.addGameObject(go)
        return go
    }

    /** Spawn the canonical set of defaults for a new project scene synchronously.
     * Returns the list of spawned GameObjects in order: skateboard, skater, floor
     */
    fun spawnDefaultsSync(): List<GameObject> {
        val skate = spawnSkateboard()
        val skater = spawnSkater(skate)
        val floor = spawnFloor()
        return listOfNotNull(skate, skater, floor)
    }
}