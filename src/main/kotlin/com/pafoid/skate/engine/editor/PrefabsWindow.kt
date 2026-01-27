package com.pafoid.skate.engine.editor

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
import imgui.ImGui
import org.joml.Vector3f
import com.jme3.math.Vector3f as JmeVector3f // Alias for JME Vector3f

class PrefabsWindow {
    private val loader = VAOLoader()

    enum class MaterialType(val displayName: String, val texturePath: String) {
        SIMPLE_CONCRETE("Concrete (Simple)", Texture.CONCRETE_SIMPLE),
        SKATELITE("Skatelite (Wood)", "assets/textures/skatelite.png")
    }

    private var selectedMaterial = MaterialType.SIMPLE_CONCRETE
    private var searchText = imgui.type.ImString("")

    fun imgui() {
        ImGui.begin("Prefabs")

        ImGui.inputTextWithHint("##search", "${Icons.SEARCH} Search...", searchText)
        ImGui.separator()

        if (ImGui.collapsingHeader("${Icons.CUBE} Simulation", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
            renderSimulationPrefabs()
        }

        if (ImGui.collapsingHeader("${Icons.PALETTE} Environment", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
            renderEnvironmentPrefabs()
        }

        if (ImGui.collapsingHeader("${Icons.GEAR} Obstacles", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
            renderObstaclePrefabs()
        }

        ImGui.end()
    }

    private fun renderSimulationPrefabs() {
        val items = listOf(
            Triple("Skateboard", ObjLoader.SKATEBOARD_GLB, { spawnSkateboard() }),
            Triple("Player", null, { /* TODO */ })
        ).filter { it.first.contains(searchText.get(), ignoreCase = true) }

        if (items.isNotEmpty()) {
            if (ImGui.beginTable("SimulationTable", 2, imgui.flag.ImGuiTableFlags.SizingFixedFit)) {
                for (item in items) {
                    ImGui.tableNextColumn()
                    renderPrefabItem(item.first, item.second, onSpawn = item.third)
                }
                ImGui.endTable()
            }
        }
    }

    private fun renderEnvironmentPrefabs() {
        val items = listOf(
            Triple("Tile", ObjLoader.CUBE, { spawnTile() })
        ).filter { it.first.contains(searchText.get(), ignoreCase = true) }

        if (items.isNotEmpty()) {
            if (ImGui.beginTable("EnvironmentTable", 2, imgui.flag.ImGuiTableFlags.SizingFixedFit)) {
                for (item in items) {
                    ImGui.tableNextColumn()
                    renderPrefabItem(item.first, item.second, onSpawn = item.third)
                }
                ImGui.endTable()
            }
        }
    }

    private fun renderObstaclePrefabs() {
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

        val items = listOf(
            ItemData("Rail", ObjLoader.RAIL, "PREFAB_RAIL", { spawnRail() }),
            ItemData("Ledge", ObjLoader.LEDGE, "PREFAB_LEDGE", { spawnLedge() }),
            ItemData("Kicker", ObjLoader.KICKER, "PREFAB_KICKER", { spawnKicker() }),
            ItemData("Manual Pad", ObjLoader.MANUAL_PAD, "PREFAB_MANUAL_PAD", { spawnManualPad() }),
            ItemData("Bank", ObjLoader.BANK, "PREFAB_BANK", { spawnBank() }),
            ItemData("Quarter Pipe", ObjLoader.QUARTER_PIPE, "PREFAB_QUARTER_PIPE", { spawnQuarterPipe() })
        ).filter { it.name.contains(searchText.get(), ignoreCase = true) }

        if (items.isNotEmpty()) {
            if (ImGui.beginTable("ObstacleTable", 3, imgui.flag.ImGuiTableFlags.SizingFixedFit)) {
                for (item in items) {
                    ImGui.tableNextColumn()
                    renderPrefabItem(item.name, item.modelPath, item.dragDropPayload, item.onSpawn)
                }
                ImGui.endTable()
            }
        }
    }

    private data class ItemData(val name: String, val modelPath: String?, val dragDropPayload: String? = null, val onSpawn: () -> Unit)

    private fun renderPrefabItem(name: String, modelPath: String?, dragDropPayload: String? = null, onSpawn: () -> Unit) {
        val size = 80f
        val padding = 5f
        
        ImGui.beginGroup()
        
        val texId = if (modelPath != null) {
            val model = AssetPool.getModel(modelPath, loader)
            ThumbnailCache.getThumbnail(modelPath, model)
        } else {
            AssetPool.getTexture(Texture.WHITE).texId
        }

        if (ImGui.imageButton("PrefabItem_$name", texId.toLong(), size, size, 0f, 1f, 1f, 0f)) {
            onSpawn()
        }
        
        if (dragDropPayload != null && ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload(dragDropPayload, name)
            ImGui.image(texId.toLong(), 64f, 64f, 0f, 1f, 1f, 0f)
            ImGui.text(name)
            ImGui.endDragDropSource()
        }

        ImGui.text(name)
        ImGui.dummy(0f, padding)
        ImGui.endGroup()
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

    fun spawnManualPad(position: Vector3f = Vector3f(0f, 0.1f, 0f)) {
        val scene = SceneManager.getCurrentScene() ?: return
        val go = GameObject("ManualPad_${scene.gameObjects.size}")
        go.transform.translation.set(position)
        go.addComponent(Entity(
            model = com.pafoid.skate.engine.models.TexturedModel(
                AssetPool.getRawModel(ObjLoader.MANUAL_PAD, loader),
                AssetPool.getTexture(selectedMaterial.texturePath)
            )
        ))
        go.addComponent(RigidBody3D(0f).apply { friction = 0.6f; bodyType = com.pafoid.skate.engine.physics3d.enums.BodyType.Static })
        go.addComponent(BoxCollider3D(Vector3f(1f, 0.1f, 1f)))
        scene.addGameObjectToScene(go)
    }

    fun spawnBank(position: Vector3f = Vector3f(0f, 0f, 0f)) {
        val scene = SceneManager.getCurrentScene() ?: return
        val go = GameObject("Bank_${scene.gameObjects.size}")
        go.transform.translation.set(position)
        go.addComponent(Entity(
            model = com.pafoid.skate.engine.models.TexturedModel(
                AssetPool.getRawModel(ObjLoader.BANK, loader),
                AssetPool.getTexture(selectedMaterial.texturePath)
            )
        ))
        go.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = com.pafoid.skate.engine.physics3d.enums.BodyType.Static })
        
        // Simple hull collider for bank
        val rawModel = AssetPool.getRawModel(ObjLoader.BANK, loader)
        val jmeVertices = mutableListOf<JmeVector3f>()
        for (i in 0 until rawModel.vertices.size / 3) {
            jmeVertices.add(JmeVector3f(rawModel.vertices[i*3], rawModel.vertices[i*3+1], rawModel.vertices[i*3+2]))
        }
        val shape = com.jme3.bullet.collision.shapes.HullCollisionShape(jmeVertices)
        go.addComponent(com.pafoid.skate.engine.physics3d.components.CustomCollider3D(shape))
        
        scene.addGameObjectToScene(go)
    }

    fun spawnQuarterPipe(position: Vector3f = Vector3f(0f, 0f, 0f)) {
        val scene = SceneManager.getCurrentScene() ?: return
        val go = GameObject("QuarterPipe_${scene.gameObjects.size}")
        go.transform.translation.set(position)
        go.addComponent(Entity(
            model = com.pafoid.skate.engine.models.TexturedModel(
                AssetPool.getRawModel(ObjLoader.QUARTER_PIPE, loader),
                AssetPool.getTexture(selectedMaterial.texturePath)
            )
        ))
        go.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = com.pafoid.skate.engine.physics3d.enums.BodyType.Static })
        
        // Mesh collider for curved surface
        val rawModel = AssetPool.getRawModel(ObjLoader.QUARTER_PIPE, loader)
        val jmeVertices = mutableListOf<JmeVector3f>()
        for (i in 0 until rawModel.vertices.size / 3) {
            jmeVertices.add(JmeVector3f(rawModel.vertices[i*3], rawModel.vertices[i*3+1], rawModel.vertices[i*3+2]))
        }
        val shape = com.jme3.bullet.collision.shapes.HullCollisionShape(jmeVertices)
        go.addComponent(com.pafoid.skate.engine.physics3d.components.CustomCollider3D(shape))
        
        scene.addGameObjectToScene(go)
    }
}