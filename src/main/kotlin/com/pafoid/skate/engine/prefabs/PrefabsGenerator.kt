package com.pafoid.skate.engine.prefabs

import com.jme3.bullet.collision.shapes.HullCollisionShape
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.Sprite
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.models.RawModel
import com.pafoid.skate.engine.models.TexturedModel
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.CustomCollider3D
import com.pafoid.skate.engine.physics3d.components.CylinderCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.components.ModularTile
import com.pafoid.skate.engine.scenes.components.SpriteRenderer
import com.pafoid.skate.engine.utils.JmeVector3f
import com.pafoid.skate.engine.utils.JobSystem
import org.joml.Vector3f
import org.koin.core.component.KoinComponent

class PrefabsGenerator(
    private val resourceManager: ResourceManager,
    private val sceneManager: SceneManager,
) : KoinComponent {
    fun generateSpriteObject(sprite: Sprite, sizeX: Float, sizeY: Float, name: String = "Sprite_Object_Gen"): GameObject {
        val scene = sceneManager.currentScene ?: throw IllegalStateException("No active scene")
        val go = scene.createGameObject(name)
        go.transform.scale.set(sizeX, sizeY, 1f)

        val renderer = SpriteRenderer()
        renderer.setSprite(sprite)
        go.addComponent(renderer)

        return go
    }

    fun generateEntityObject(model: RawModel, texture: Texture, name: String = "Entity_Object_Gen"): GameObject {
        val scene = sceneManager.currentScene ?: throw IllegalStateException("No active scene")
        val go = scene.createGameObject(name)

        val texturedModel = TexturedModel(model, texture)
        val entity = Entity(texturedModel)
        go.addComponent(entity)

        // Link entity transform to game object transform for consistency
        entity.transform.copyFrom(go.transform)

        return go
    }

    fun generateTileObject(sizeX: Float, sizeY: Float, texture: Texture, name: String = "Tile_Gen"): GameObject {
        val scene = sceneManager.currentScene ?: throw IllegalStateException("No active scene")
        val go = scene.createGameObject(name)

        val tile = ModularTile()
        tile.size.set(sizeX, sizeY, 1f)
        go.addComponent(tile)

        // We use a cube as the base model for tiles
        val cubeModel = resourceManager.loadModelSync(Assets.Models.CUBE).parts[0].rawModel

        val texturedModel = TexturedModel(cubeModel, texture)
        val entity = Entity(texturedModel)
        go.addComponent(entity)

        go.transform.scale.set(sizeX, sizeY, 1f)
        entity.transform.copyFrom(go.transform)

        return go
    }

    fun spawnSkateboard():Skateboard? {
        val scene = sceneManager.currentScene ?: return null

        var skate: Skateboard? = null
        JobSystem.runAsync {
            val model = resourceManager.loadModel(Assets.Models.SKATEBOARD_GLB)
            JobSystem.runOnMain {
                skate = Skateboard(model)
                scene.addGameObjectToScene(skate)
            }
        }

        return skate
    }

    fun spawnSkater(skate: GameObject? = null): Skater? {
        val scene = sceneManager.currentScene ?: return null
        val skater: Skater? = null
        JobSystem.runAsync {
            val model = resourceManager.loadModel(Assets.Models.JAMES)
            JobSystem.runOnMain {
                val skater = Skater("Skater", model, skate)
                //skater.getComponent<Entity>()?.model?.animations.addAll()
                scene.addGameObjectToScene(skater)
            }
        }

        return skater
    }

    fun spawnFloor(): Floor? {
        val scene = sceneManager.currentScene ?: return null
        var floor: Floor? = null

        JobSystem.runAsync {
            val texture = resourceManager.loadTexture(Assets.Textures.ASPHALT)
            val texturedModel = TexturedModel(resourceManager.loadModel(Assets.Models.CUBE).parts[0].rawModel, texture)
            texturedModel.parts[0].material.baseColorPath = Assets.Textures.ASPHALT

            JobSystem.runOnMain {
                floor = Floor(texturedModel)
                scene.addGameObjectToScene(floor)
            }
        }

        return floor
    }

    fun spawnTile(): Tile? {
        val scene = sceneManager.currentScene ?: return null

        var tile:Tile? = null

        JobSystem.runAsync {
            val texture = resourceManager.loadTexture(Assets.Textures.CONCRETE_SIMPLE)
            val texturedModel = TexturedModel(resourceManager.loadModel(Assets.Models.CUBE).parts[0].rawModel, texture)
            texturedModel.parts[0].material.baseColorPath = Assets.Textures.CONCRETE_SIMPLE

            JobSystem.runOnMain {
                tile = Tile("Tile_${scene.gameObjects.size}", texturedModel)
                scene.addGameObjectToScene(tile)
            }
        }

        return tile
    }

    fun spawnRail(position: Vector3f = Vector3f(0f, 0.5f, 0f), material: MaterialType?) {
        val scene = sceneManager.currentScene ?: return
        val rail = GameObject("Rail_${scene.gameObjects.size}")
        rail.transform.translation.set(position)
        rail.transform.scale.set(1f, 1f, 1f)
        val mat = material ?: MaterialType.METAL
        rail.addComponent(
            Entity(
                model = TexturedModel(
                    resourceManager.loadModelSync(Assets.Models.RAIL).parts[0].rawModel,
                    resourceManager.loadTextureSync(mat.texturePath)
                )
            )
        )
        rail.addComponent(RigidBody3D(0f).apply { friction = 0.05f; bodyType = BodyType.Static })
        rail.addComponent(CylinderCollider3D(radius = 0.05f, height = 2.0f, axis = 0)) //Should depend on rail type
        scene.addGameObjectToScene(rail)
    }

    fun spawnLedge(position: Vector3f = Vector3f(0f, 0.25f, 0f), material: MaterialType?) {
        val scene = sceneManager.currentScene ?: return
        val mat = material ?: MaterialType.CONCRETE
        val ledge = GameObject("${mat.displayName}_Ledge_${scene.gameObjects.size}")
        ledge.transform.translation.set(position)
        ledge.transform.scale.set(1f, 1f, 1f)
        ledge.addComponent(
            Entity(
                model = TexturedModel(
                    resourceManager.loadModelSync(Assets.Models.LEDGE).parts[0].rawModel,
                    resourceManager.loadTextureSync(mat.texturePath)
                )
            )
        )
        ledge.addComponent(RigidBody3D(0f).apply { friction = 0.6f; bodyType = BodyType.Static })
        ledge.addComponent(BoxCollider3D(Vector3f(0.5f, 0.25f, 0.5f)))
        scene.addGameObjectToScene(ledge)
    }

    fun spawnKicker(position: Vector3f = Vector3f(0f, 0f, 0f), material: MaterialType?) {
        val scene = sceneManager.currentScene ?: return
        val kicker = GameObject("Kicker_${scene.gameObjects.size}")
        kicker.transform.translation.set(position)
        kicker.transform.scale.set(1f, 1f, 1f)
        val mat = material ?: MaterialType.CONCRETE
        kicker.addComponent(
            Entity(
                model = TexturedModel(
                    resourceManager.loadModelSync(Assets.Models.KICKER).parts[0].rawModel,
                    resourceManager.loadTextureSync(mat.texturePath)
                )
            )
        )
        kicker.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = BodyType.Static })

        val kickerRawModel = resourceManager.loadModelSync(Assets.Models.KICKER).parts[0].rawModel
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
        val go = GameObject("ManualPad_${scene.gameObjects.size}")
        go.transform.translation.set(position)
        val mat = material ?: MaterialType.CONCRETE
        go.addComponent(
            Entity(
                model = TexturedModel(
                    resourceManager.loadModelSync(Assets.Models.MANUAL_PAD).parts[0].rawModel,
                    resourceManager.loadTextureSync(mat.texturePath)
                )
            )
        )
        go.addComponent(RigidBody3D(0f).apply { friction = 0.6f; bodyType = BodyType.Static })
        go.addComponent(BoxCollider3D(Vector3f(1f, 0.1f, 1f)))
        scene.addGameObjectToScene(go)
    }


    fun spawnBank(position: Vector3f = Vector3f(0f, 0f, 0f), material: MaterialType?) {
        val scene = sceneManager.currentScene ?: return
        val go = GameObject("Bank_${scene.gameObjects.size}")
        go.transform.translation.set(position)
        val mat = material ?: MaterialType.CONCRETE
        go.addComponent(
            Entity(
                model = TexturedModel(
                    resourceManager.loadModelSync(Assets.Models.BANK).parts[0].rawModel,
                    resourceManager.loadTextureSync(mat.texturePath)
                )
            )
        )
        go.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = BodyType.Static })

        val rawModel = resourceManager.loadModelSync(Assets.Models.BANK).parts[0].rawModel
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
        val go = GameObject("QuarterPipe_${scene.gameObjects.size}")
        go.transform.translation.set(position)
        val mat = material ?: MaterialType.CONCRETE
        go.addComponent(
            Entity(
                model = TexturedModel(
                    resourceManager.loadModelSync(Assets.Models.QUARTER_PIPE).parts[0].rawModel,
                    resourceManager.loadTextureSync(mat.texturePath)
                )
            )
        )
        go.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = BodyType.Static })

        val rawModel = resourceManager.loadModelSync(Assets.Models.QUARTER_PIPE).parts[0].rawModel
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

}