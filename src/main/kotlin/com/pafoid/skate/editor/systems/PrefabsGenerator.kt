package com.pafoid.skate.editor.systems

import com.jme3.bullet.collision.shapes.HullCollisionShape
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.Sprite
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.CharacterModel
import com.pafoid.skate.engine.assets.data.models.RawModel
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.ModularTile
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.scene.addGameObjectToScene
import com.pafoid.skate.engine.ecs.scene.createGameObject
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.CustomCollider3D
import com.pafoid.skate.engine.physics3d.components.CylinderCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.utils.JmeVector3f
import com.pafoid.skate.engine.utils.JobSystem
import com.pafoid.skate.game.prefabs.MaterialType
import com.pafoid.skate.game.prefabs.Skateboard
import com.pafoid.skate.game.prefabs.Skater
import com.pafoid.skate.game.prefabs.Tile
import org.joml.Vector3f
import org.koin.core.component.KoinComponent

class PrefabsGenerator(
    private val resourceManager: ResourceManager,
    private val sceneManager: SceneManager,
) : KoinComponent {
    fun generateSpriteObject(sprite: Sprite, sizeX: Float, sizeY: Float, name: String = "Sprite_Object_Gen"): GameObject {
        val scene = sceneManager.currentScene ?: throw IllegalStateException("No active scene")
        val go = scene.createGameObject(name)
        go.getComponent<Transform>()?.scale?.set(sizeX, sizeY, 1f)

        val renderer = SpriteRenderer()
        renderer.setSprite(sprite)
        go.addComponent(renderer)

        return go
    }

    fun generateEntityObject(model: RawModel, texture: Texture, name: String = "Entity_Object_Gen"): GameObject {
        val scene = sceneManager.currentScene ?: throw IllegalStateException("No active scene")
        val go = scene.createGameObject(name)

        val texturedModel = TexturedModel(model, texture)
        go.addComponent(RenderComponent(model = texturedModel))

        // Add a default Transform component
        val transformComponent = Transform()
        go.addComponent(transformComponent)

        return go
    }

    fun generateTileObject(sizeX: Float, sizeY: Float, texture: Texture, name: String = "Tile_Gen"): GameObject {
        val scene = sceneManager.currentScene ?: throw IllegalStateException("No active scene")
        val go = scene.createGameObject(name)

        val tile = ModularTile()
        tile.size.set(sizeX, sizeY, 1f)
        go.addComponent(tile)

        // We use a cube as the base model for tiles
        val cubeBaseModel = resourceManager.loadModelSync(Assets.Models.CUBE)
        val cubeModel = cubeBaseModel.mesh[0].rawModel

        val texturedModel = TexturedModel(cubeModel, texture)
        go.addComponent(RenderComponent(model = texturedModel))


        // Add a default Transform component
        val transformComponent = Transform()
        transformComponent.scale.set(sizeX, sizeY, 1f)
        go.addComponent(transformComponent)

        return go
    }

    fun spawnSkateboard(): Skateboard? {
        var skate: Skateboard? = null
        JobSystem.runAsync {
            val model = resourceManager.loadModel(Assets.Models.SKATEBOARD_GLB)
            JobSystem.runOnMain {
                skate = Skateboard(model as TexturedModel)
                sceneManager.currentScene?.addGameObjectToScene(skate)
            }
        }

        return skate
    }

    fun spawnSkater(skate: GameObject? = null): Skater? {
        var skater: Skater? = null

        JobSystem.runAsync {
            val model = resourceManager.getModel(Assets.Models.JAMES) as CharacterModel
            skater = Skater("Skater", model, skate)

            animations.forEach { path ->
                val animation = resourceManager.loadAnimation(path, skater.skeletonComponent.pose.skeleton)
                skater.animator.addAnimation(animation)
            }

            JobSystem.runOnMain {
                sceneManager.currentScene?.addGameObjectToScene(skater)
            }
        }

        return skater
    }

    fun spawnFloor(): List<Tile?> {
        var tile: Tile? = null

        JobSystem.runAsync {
            val texture = resourceManager.loadTexture(Assets.Textures.ASPHALT)
            val baseModel = resourceManager.loadModel(Assets.Models.CUBE)
            val texturedModel = TexturedModel(baseModel.mesh[0].rawModel, texture)
            texturedModel.mesh[0].material.baseColorPath = Assets.Textures.ASPHALT

            JobSystem.runOnMain {
                tile = Tile("Tile", texturedModel)
                sceneManager.currentScene?.addGameObjectToScene(tile)
            }
        }

        return listOf(tile)
    }

    fun spawnRail(position: Vector3f = Vector3f(0f, 0.5f, 0f), material: MaterialType?) {
        val scene = sceneManager.currentScene ?: return
        val rail = GameObject("Rail_${scene.gameObjectManager.gameObjects.size}")
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
            RenderComponent(model = texturedModel)
        )
        rail.addComponent(RigidBody3D(0f).apply { friction = 0.05f; bodyType = BodyType.Static })
        rail.addComponent(CylinderCollider3D(radius = 0.05f, height = 2.0f, axis = 0)) //Should depend on rail type
        scene.addGameObjectToScene(rail)
    }

    fun spawnLedge(position: Vector3f = Vector3f(0f, 0.25f, 0f), material: MaterialType?) {
        val scene = sceneManager.currentScene ?: return
        val mat = material ?: MaterialType.CONCRETE
        val ledge = GameObject("${mat.displayName}_Ledge_${scene.gameObjectManager.gameObjects.size}")
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
            RenderComponent(model = texturedModel)
        )
        ledge.addComponent(RigidBody3D(0f).apply { friction = 0.6f; bodyType = BodyType.Static })
        ledge.addComponent(BoxCollider3D(Vector3f(0.5f, 0.25f, 0.5f)))
        scene.addGameObjectToScene(ledge)
    }

    fun spawnKicker(position: Vector3f = Vector3f(0f, 0f, 0f), material: MaterialType?) {
        val scene = sceneManager.currentScene ?: return
        val kicker = GameObject("Kicker_${scene.gameObjectManager.gameObjects.size}")
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
            RenderComponent(model = texturedModel)
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
            val kickerShape = HullCollisionShape(jmeVertices)
            kicker.addComponent(CustomCollider3D(kickerShape))
        }

        scene.addGameObjectToScene(kicker)
    }

    fun spawnManualPad(position: Vector3f = Vector3f(0f, 0.1f, 0f), material: MaterialType?) {
        val scene = sceneManager.currentScene ?: return
        val go = GameObject("ManualPad_${scene.gameObjectManager.gameObjects.size}")
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
            RenderComponent(model = texturedModel)
        )
        go.addComponent(RigidBody3D(0f).apply { friction = 0.6f; bodyType = BodyType.Static })
        go.addComponent(BoxCollider3D(Vector3f(1f, 0.1f, 1f)))
        scene.addGameObjectToScene(go)
    }


    fun spawnBank(position: Vector3f = Vector3f(0f, 0f, 0f), material: MaterialType?) {
        val scene = sceneManager.currentScene ?: return
        val go = GameObject("Bank_${scene.gameObjectManager.gameObjects.size}")
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
            RenderComponent(model = texturedModel)
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
        val shape = HullCollisionShape(jmeVertices)
        go.addComponent(CustomCollider3D(shape))

        scene.addGameObjectToScene(go)
    }


    fun spawnQuarterPipe(position: Vector3f = Vector3f(0f, 0f, 0f), material: MaterialType?) {
        val scene = sceneManager.currentScene ?: return
        val go = GameObject("QuarterPipe_${scene.gameObjectManager.gameObjects.size}")
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
            RenderComponent(model = texturedModel)
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
        val shape = HullCollisionShape(jmeVertices)
        go.addComponent(CustomCollider3D(shape))

        scene.addGameObjectToScene(go)
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