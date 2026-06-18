package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.Sprite
import com.pafoid.skate.engine.assets.data.models.CharacterModel
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.CustomCollider3D
import com.pafoid.skate.engine.physics3d.components.CylinderCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.utils.IJobSystem
import com.pafoid.skate.engine.utils.JmeVector3f
import com.pafoid.skate.game.prefabs.MaterialType
import com.pafoid.skate.game.prefabs.Skateboard
import com.pafoid.skate.game.prefabs.Skater
import com.pafoid.skate.game.prefabs.Tile
import org.joml.Vector3f
import java.io.File

class PrefabsGenerator(
    private val jobSystem: IJobSystem,
    private val resourceManager: ResourceManager,
    private val sceneManager: SceneManager,
    private val systemManager: SystemManager
) {
    private val gameObjectManager: GameObjectManager by lazy {
        systemManager.getSystem<GameObjectManager>() ?: throw RuntimeException("GameObjectManager not initialized")
    }
    /** Root path for engine-bundled assets copied into the project (null = use engine paths) */
    private var engineDefaultsRoot: String? = null

    /** Cache for resolved model paths to avoid repeated File.exists() syscalls */
    private val resolvedModelPaths = mutableMapOf<String, String>()
    private val resolvedTexturePaths = mutableMapOf<String, String>()
    private val resolvedAnimationPaths = mutableMapOf<String, String>()

    /**
     * Set the root path for engine-bundled default assets.
     * Called by ProjectManager after copying assets into the project.
     */
    fun setEngineDefaultsRoot(projectDir: File) {
        engineDefaultsRoot = File(projectDir, "Assets/EngineDefaults").absolutePath
    }

    /** Resolve a model path — use cached result, project copy, or fall back to engine path */
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

    /** Resolve an animation path — use cached result, project copy, or fall back to engine path */
    private fun resolveAnimationPath(enginePath: String): String {
        resolvedAnimationPaths[enginePath]?.let { return it }
        val root = engineDefaultsRoot ?: return enginePath.also { resolvedAnimationPaths[enginePath] = it }
        val fileName = File(enginePath).name
        val candidate = File(root, "Characters/animations/$fileName")
        val result = if (candidate.exists()) candidate.absolutePath else enginePath
        resolvedAnimationPaths[enginePath] = result
        return result
    }
    fun generateSpriteObject(sprite: Sprite, sizeX: Float, sizeY: Float, name: String = "Sprite_Object_Gen"): GameObject {
        val go = gameObjectManager.createGameObject(name)
        go.getComponent<Transform>()?.scale?.set(sizeX, sizeY, 1f)

        val renderer = SpriteRenderer()
        renderer.setSprite(sprite)
        go.addComponent(renderer)

        return go
    }

    fun spawnSkateboard() {
        jobSystem.runAsync {
            val model = resourceManager.loadModel(Assets.Models.SKATEBOARD_GLB)
            jobSystem.runOnMain {
                val skate = Skateboard(model as TexturedModel)
                gameObjectManager.addGameObject(skate)
            }
        }
    }

    /**
     * Synchronous version for default scene creation.
     * Blocks until the skateboard is added to the scene.
     */
    fun spawnSkateboardSync() {
        val modelPath = resolveModelPath(Assets.Models.SKATEBOARD_GLB)
        val model = resourceManager.loadModelSync(modelPath)
        val skate = Skateboard(model as TexturedModel)
        gameObjectManager.addGameObjectImmediate(skate)
    }

    fun spawnSkater(skate: GameObject? = null) {
        jobSystem.runAsync {
            val model = resourceManager.getModel(Assets.Models.JAMES) as CharacterModel
            val skater = Skater("Skater", model, skate)

            animations.forEach { path ->
                val animation = resourceManager.loadAnimation(path, skater.skeletonComponent.pose.skeleton)
                skater.animator.addAnimation(animation)
            }

            jobSystem.runOnMain {
                gameObjectManager.addGameObject(skater)
            }
        }
    }

    /**
     * Synchronous version for default scene creation.
     * Blocks until the skater is added to the scene.
     * Loads animations synchronously to ensure they're in the scene before serialization.
     */
    fun spawnSkaterSync(skate: GameObject? = null) {
        val modelPath = resolveModelPath(Assets.Models.JAMES)
        val model = resourceManager.getModel(modelPath) as CharacterModel?
            ?: resourceManager.loadModelSync(modelPath) as CharacterModel
        val skater = Skater("Skater", model, skate)

        val skeleton = skater.skeletonComponent.pose.skeleton

        animations.forEach { path ->
            try {
                val animPath = resolveAnimationPath(path)
                val animation = resourceManager.loadAnimationSync(animPath, skeleton)
                skater.animator.addAnimation(animation)
            } catch (e: Exception) {
                // Skip missing animations during scene creation
            }
        }

        gameObjectManager.addGameObjectImmediate(skater)
    }

    fun spawnFloor() {
        jobSystem.runAsync {
            val texture = resourceManager.loadTexture(Assets.Textures.ASPHALT)
            val baseModel = resourceManager.loadModel(Assets.Models.CUBE)
            val texturedModel = TexturedModel(baseModel.mesh[0].rawModel, texture)
            texturedModel.mesh[0].material.baseColorPath = Assets.Textures.ASPHALT

            jobSystem.runOnMain {
                val tile = Tile("Tile", texturedModel)
                gameObjectManager.addGameObject(tile)
            }
        }
    }

    /**
     * Synchronous version for default scene creation.
     * Blocks until the floor tile is added to the scene.
     */
    fun spawnFloorSync() {
        val texturePath = resolveTexturePath(Assets.Textures.ASPHALT)
        val modelPath = resolveModelPath(Assets.Models.CUBE)
        val texture = resourceManager.loadTextureSync(texturePath)
        val baseModel = resourceManager.loadModelSync(modelPath)
        val texturedModel = TexturedModel(baseModel.mesh[0].rawModel, texture)
        texturedModel.sourcePath = modelPath
        texturedModel.mesh[0].material.baseColorPath = texturePath

        val tile = Tile("Tile", texturedModel)
        gameObjectManager.addGameObjectImmediate(tile)
    }

    fun spawnRail(position: Vector3f = Vector3f(0f, 0.5f, 0f), material: MaterialType?): GameObject? {
        val scene = sceneManager.currentScene ?: return null
        val rail = GameObject("Rail_${scene.gameObjects.size}")
        val transformComponent = Transform()
        transformComponent.translation.set(position)
        transformComponent.scale.set(1f, 1f, 1f)
        rail.addComponent(transformComponent)
        val mat = material ?: MaterialType.METAL
        val baseModel = resourceManager.loadModelSync(Assets.Models.RAIL)
        val texturedModel = TexturedModel(
            baseModel.mesh[0].rawModel,
            resourceManager.loadTextureSync(mat.texturePath)
        )
        rail.addComponent(
            RenderComponent(
                model = texturedModel,
                castShadow = true,
                receiveShadow = true
            )
        )
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
        val baseModel = resourceManager.loadModelSync(Assets.Models.LEDGE)
        val texturedModel = TexturedModel(
            baseModel.mesh[0].rawModel,
            resourceManager.loadTextureSync(mat.texturePath)
        )
        ledge.addComponent(
            RenderComponent(
                model = texturedModel,
                castShadow = true,
                receiveShadow = true
            )
        )
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
        val baseModel = resourceManager.loadModelSync(Assets.Models.KICKER)
        val texturedModel = TexturedModel(
            baseModel.mesh[0].rawModel,
            resourceManager.loadTextureSync(mat.texturePath)
        )
        kicker.addComponent(
            RenderComponent(
                model = texturedModel,
                castShadow = true,
                receiveShadow = true
            )
        )
        kicker.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = BodyType.Static })

        val kickerRawModel = resourceManager.loadModelSync(Assets.Models.KICKER).mesh[0].rawModel
        val jmeVertices = mutableListOf<JmeVector3f>()
        for (i in 0 until kickerRawModel.vertices.size / 3) {
            jmeVertices.add(
                com.pafoid.skate.engine.utils.JmeVector3f(
                    kickerRawModel.vertices[i * 3],
                    kickerRawModel.vertices[i * 3 + 1],
                    kickerRawModel.vertices[i * 3 + 2]
                )
            )
        }

        if (jmeVertices.isNotEmpty()) {
            val hullPoints = jmeVertices.map { Vector3f(it.x, it.y, it.z) }
            kicker.addComponent(CustomCollider3D(hullPoints))
        }

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
        val baseModel = resourceManager.loadModelSync(Assets.Models.MANUAL_PAD)
        val texturedModel = TexturedModel(
            baseModel.mesh[0].rawModel,
            resourceManager.loadTextureSync(mat.texturePath)
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
        val baseModel = resourceManager.loadModelSync(Assets.Models.BANK)
        val texturedModel = TexturedModel(
            baseModel.mesh[0].rawModel,
            resourceManager.loadTextureSync(mat.texturePath)
        )
        go.addComponent(
            RenderComponent(
                model = texturedModel,
                castShadow = true,
                receiveShadow = true
            )
        )
        go.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = BodyType.Static })

        val rawModel = resourceManager.loadModelSync(Assets.Models.BANK).mesh[0].rawModel
        val jmeVertices = mutableListOf<JmeVector3f>()
        for (i in 0 until rawModel.vertices.size / 3) {
            jmeVertices.add(
                com.pafoid.skate.engine.utils.JmeVector3f(
                    rawModel.vertices[i * 3],
                    rawModel.vertices[i * 3 + 1],
                    rawModel.vertices[i * 3 + 2]
                )
            )
        }
        go.addComponent(CustomCollider3D(jmeVertices.map { Vector3f(it.x, it.y, it.z) }))

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
        val baseModel = resourceManager.loadModelSync(Assets.Models.QUARTER_PIPE)
        val texturedModel = TexturedModel(
            baseModel.mesh[0].rawModel,
            resourceManager.loadTextureSync(mat.texturePath)
        )
        go.addComponent(
            RenderComponent(
                model = texturedModel,
                castShadow = true,
                receiveShadow = true
            )
        )
        go.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = BodyType.Static })

        val rawModel = resourceManager.loadModelSync(Assets.Models.QUARTER_PIPE).mesh[0].rawModel
        val jmeVertices = mutableListOf<JmeVector3f>()
        for (i in 0 until rawModel.vertices.size / 3) {
            jmeVertices.add(
                com.pafoid.skate.engine.utils.JmeVector3f(
                    rawModel.vertices[i * 3],
                    rawModel.vertices[i * 3 + 1],
                    rawModel.vertices[i * 3 + 2]
                )
            )
        }
        go.addComponent(CustomCollider3D(jmeVertices.map { Vector3f(it.x, it.y, it.z) }))

        gameObjectManager.addGameObject(go)
        return go
    }

    private val animations = listOf(
        Assets.Animations.IDLE_0,
        Assets.Animations.IDLE_1,
        Assets.Animations.JUMP,
        Assets.Animations.WALKING,
        Assets.Animations.RUNNING,
        Assets.Animations.LEFT_TURN,
        Assets.Animations.LEFT_TURN_90,
        Assets.Animations.LEFT_STRAFE,
        Assets.Animations.LEFT_STRAFE_WALKING,
        Assets.Animations.RIGHT_TURN,
        Assets.Animations.RIGHT_TURN_90,
        Assets.Animations.RIGHT_STRAFE,
        Assets.Animations.RIGHT_STRAFE_WALKING,
    )
}