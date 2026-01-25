package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.Prefabs
import com.pafoid.skate.engine.assets.AssetPool
import com.pafoid.skate.engine.assets.ObjLoader
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.components.SkateboardPhysics
import com.pafoid.skate.engine.utils.JobSystem
import imgui.ImGui
import org.joml.Vector3f

class PrefabsWindow {
    private val loader = VAOLoader()

    fun imgui() {
        ImGui.begin("Prefabs")

        if (ImGui.collapsingHeader("Simulation")) {
            if (ImGui.button("Spawn Skateboard")) {
                spawnSkateboard()
            }
            if (ImGui.button("Spawn Player")) {
                // TODO: Implement Player Prefab
            }
        }

        if (ImGui.collapsingHeader("Environment")) {
            if (ImGui.button("Spawn Modular Tile")) {
                spawnTile()
            }
        }

        if (ImGui.collapsingHeader("Obstacles")) {
            if (ImGui.button("Spawn Rail")) {
                // TODO: Implement Rail Prefab
            }
            if (ImGui.button("Spawn Ledge")) {
                // TODO: Implement Ledge Prefab
            }
        }

        ImGui.end()
    }

    private fun spawnSkateboard() {
        val scene = SceneManager.getCurrentScene() ?: return
        
        AssetPool.getModelAsync(ObjLoader.SKATEBOARD_GLB, loader) { model ->
            val skate = GameObject("Skateboard")
            skate.transform.translation.set(0f, 5f, 0f)
            skate.transform.scale.set(0.01f, 0.01f, 0.01f)
            skate.addComponent(Entity(model = model))
            skate.addComponent(RigidBody3D(1.0f).apply { friction = 0.1f })
            skate.addComponent(BoxCollider3D(Vector3f(1.5f, 0.1f, 0.4f)))
            skate.addComponent(SkateboardPhysics())
            
            scene.addGameObjectToScene(skate)
        }
    }

    private fun spawnTile() {
        val scene = SceneManager.getCurrentScene() ?: return
        val tile = GameObject("Tile_${scene.gameObjects.size}")
        tile.transform.translation.set(0f, 0f, 0f)
        tile.addComponent(Entity(
            model = com.pafoid.skate.engine.models.TexturedModel(
                AssetPool.getRawModel(ObjLoader.CUBE, loader), 
                AssetPool.getTexture(Texture.WHITE)
            )
        ))
        tile.addComponent(com.pafoid.skate.engine.scenes.components.ModularTile())
        tile.addComponent(RigidBody3D(0f).apply { bodyType = com.pafoid.skate.engine.physics3d.enums.BodyType.Static })
        tile.addComponent(BoxCollider3D(Vector3f(1f, 1f, 1f)))
        scene.addGameObjectToScene(tile)
    }
}
