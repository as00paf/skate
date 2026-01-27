package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.Prefabs
import com.pafoid.skate.engine.assets.AssetPool
import com.pafoid.skate.engine.assets.ObjLoader
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.utils.Icons
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
import com.jme3.math.Vector3f as JmeVector3f // Alias for JME Vector3f

class PrefabsWindow {
    private val loader = VAOLoader()

    enum class MaterialType(val displayName: String, val texturePath: String) {
        SIMPLE_CONCRETE("Concrete (Simple)", Texture.CONCRETE_SIMPLE)
    }

    private var selectedMaterial = MaterialType.SIMPLE_CONCRETE

    fun imgui() {
        ImGui.begin("Prefabs")

        if (ImGui.collapsingHeader("${Icons.CUBE} Simulation")) {
            if (ImGui.button("${Icons.PLUS} Spawn Skateboard")) {
                spawnSkateboard()
            }
            if (ImGui.button("${Icons.PLUS} Spawn Player")) {
                // TODO: Implement Player Prefab
            }
        }

        if (ImGui.collapsingHeader("${Icons.PALETTE} Environment")) {
            if (ImGui.button("${Icons.PLUS} Spawn Modular Tile")) {
                spawnTile()
            }
        }

        if (ImGui.collapsingHeader("${Icons.GEAR} Obstacles")) {
            ImGui.text("Material Style:")
            if (ImGui.beginCombo("##material_style", selectedMaterial.displayName)) {
                for (mat in MaterialType.entries) {
                    val isSelected = selectedMaterial == mat
                    if (ImGui.selectable(mat.displayName, isSelected)) {
                        selectedMaterial = mat
                    }
                    if (isSelected) {
                        ImGui.setItemDefaultFocus()
                    }
                }
                ImGui.endCombo()
            }
            ImGui.separator()

            if (ImGui.button("${Icons.PLUS} Spawn Rail")) {
                spawnRail()
            }
            if (ImGui.beginDragDropSource()) {
                ImGui.setDragDropPayload("PREFAB_RAIL", "Rail")
                ImGui.text("${Icons.PLUS} Spawn Rail")
                ImGui.endDragDropSource()
            }

            if (ImGui.button("${Icons.PLUS} Spawn Ledge")) {
                spawnLedge()
            }
            if (ImGui.beginDragDropSource()) {
                ImGui.setDragDropPayload("PREFAB_LEDGE", "Ledge")
                ImGui.text("${Icons.PLUS} Spawn Ledge")
                ImGui.endDragDropSource()
            }

            if (ImGui.button("${Icons.PLUS} Spawn Kicker")) {
                spawnKicker()
            }
            if (ImGui.beginDragDropSource()) {
                ImGui.setDragDropPayload("PREFAB_KICKER", "Kicker")
                ImGui.text("${Icons.PLUS} Spawn Kicker")
                ImGui.endDragDropSource()
            }
        }

        ImGui.end()
    }

    fun spawnSkateboard(position: Vector3f = Vector3f(0f, 5f, 0f)) {
        val scene = SceneManager.getCurrentScene() ?: return
        
        AssetPool.getModelAsync(ObjLoader.SKATEBOARD_GLB, loader) { model ->
            val skate = GameObject("Skateboard")
            skate.transform.translation.set(position)
            skate.transform.scale.set(0.01f, 0.01f, 0.01f)
            skate.addComponent(Entity(model = model))
            skate.addComponent(RigidBody3D(1.0f).apply { friction = 0.1f })
            skate.addComponent(BoxCollider3D(Vector3f(1.5f, 0.1f, 0.4f)))
            skate.addComponent(SkateboardPhysics())
            
            scene.addGameObjectToScene(skate)
        }
    }

    fun spawnTile(position: Vector3f = Vector3f(0f, 0f, 0f)) {
        val scene = SceneManager.getCurrentScene() ?: return
        val tile = GameObject("Tile_${scene.gameObjects.size}")
        tile.transform.translation.set(position)
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

    fun spawnRail(position: Vector3f = Vector3f(0f, 0.5f, 0f)) {
        val scene = SceneManager.getCurrentScene() ?: return
        val rail = GameObject("Rail_${scene.gameObjects.size}")
        rail.transform.translation.set(position) 
        rail.transform.scale.set(1f, 1f, 1f)
        rail.addComponent(Entity(
            model = com.pafoid.skate.engine.models.TexturedModel(
                AssetPool.getRawModel(ObjLoader.RAIL, loader),
                AssetPool.getTexture(selectedMaterial.texturePath)
            )
        ))
        rail.addComponent(RigidBody3D(0f).apply { friction = 0.05f; bodyType = com.pafoid.skate.engine.physics3d.enums.BodyType.Static })
        rail.addComponent(com.pafoid.skate.engine.physics3d.components.CylinderCollider3D(radius = 0.05f, height = 2.0f, axis = 0)) // X-axis aligned
        scene.addGameObjectToScene(rail)
    }

    fun spawnLedge(position: Vector3f = Vector3f(0f, 0.25f, 0f)) {
        val scene = SceneManager.getCurrentScene() ?: return
        val ledge = GameObject("Ledge_${scene.gameObjects.size}")
        ledge.transform.translation.set(position) 
        ledge.transform.scale.set(1f, 1f, 1f)
        ledge.addComponent(Entity(
            model = com.pafoid.skate.engine.models.TexturedModel(
                AssetPool.getRawModel(ObjLoader.LEDGE, loader),
                AssetPool.getTexture(selectedMaterial.texturePath)
            )
        ))
        ledge.addComponent(RigidBody3D(0f).apply { friction = 0.6f; bodyType = com.pafoid.skate.engine.physics3d.enums.BodyType.Static })
        ledge.addComponent(BoxCollider3D(Vector3f(0.5f, 0.25f, 0.5f)))
        scene.addGameObjectToScene(ledge)
    }

    fun spawnKicker(position: Vector3f = Vector3f(0f, 0f, 0f)) {
        val scene = SceneManager.getCurrentScene() ?: return
        val kicker = GameObject("Kicker_${scene.gameObjects.size}")
        kicker.transform.translation.set(position)
        kicker.transform.scale.set(1f, 1f, 1f)
        kicker.addComponent(Entity(
            model = com.pafoid.skate.engine.models.TexturedModel(
                AssetPool.getRawModel(ObjLoader.KICKER, loader),
                AssetPool.getTexture(selectedMaterial.texturePath)
            )
        ))
        kicker.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = com.pafoid.skate.engine.physics3d.enums.BodyType.Static })
        
        val kickerRawModel = AssetPool.getRawModel(ObjLoader.KICKER, loader)
        val jmeVertices = mutableListOf<JmeVector3f>()
        for (i in 0 until kickerRawModel.vertices.size / 3) {
            jmeVertices.add(JmeVector3f(kickerRawModel.vertices[i*3], kickerRawModel.vertices[i*3+1], kickerRawModel.vertices[i*3+2]))
        }
        
        if (jmeVertices.isEmpty()) {
            println("Error: (spawnKicker) Kicker model vertex data is empty. HullCollisionShape cannot be created.")
            scene.addGameObjectToScene(kicker) // Add it anyway so it appears in hierarchy, though physics-less
            return
        }

        val kickerShape = com.jme3.bullet.collision.shapes.HullCollisionShape(jmeVertices)
        kicker.addComponent(com.pafoid.skate.engine.physics3d.components.CustomCollider3D(kickerShape))
        
        scene.addGameObjectToScene(kicker)
    }
}
