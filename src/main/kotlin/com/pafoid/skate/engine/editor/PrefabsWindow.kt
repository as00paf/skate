package com.pafoid.skate.engine.editor

import com.jme3.bullet.collision.shapes.HullCollisionShape
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.utils.Icons
import com.pafoid.skate.engine.utils.JobSystem
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.models.TexturedModel
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.CustomCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.components.SkateboardPhysics
import imgui.ImGui
import imgui.flag.ImGuiTableFlags
import imgui.flag.ImGuiTreeNodeFlags
import imgui.type.ImString
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue
import com.jme3.math.Vector3f as JmeVector3f // Alias for JME Vector3f

class PrefabsWindow : KoinComponent {
    private val thumbnailCache: ThumbnailCache by inject()
    private val resourceManager: ResourceManager by inject()

    private var searchText = ImString("")

    fun imgui() {
        ImGui.begin("Prefabs")

        ImGui.inputTextWithHint("##search", "${Icons.SEARCH} Search...", searchText)
        ImGui.separator()

        if (ImGui.collapsingHeader("${Icons.CUBE} Simulation", ImGuiTreeNodeFlags.DefaultOpen)) {
            renderSimulationPrefabs()
        }

        if (ImGui.collapsingHeader("${Icons.PALETTE} Environment", ImGuiTreeNodeFlags.DefaultOpen)) {
            renderEnvironmentPrefabs()
        }

        if (ImGui.collapsingHeader("${Icons.GEAR} Obstacles", ImGuiTreeNodeFlags.DefaultOpen)) {
            renderObstaclePrefabs()
        }

        ImGui.end()
    }

    private fun renderSimulationPrefabs() {
        val items = listOf(
            Triple("Skateboard", Assets.Models.SKATEBOARD_GLB) { _: MaterialType -> spawnSkateboard() },
            Triple("Player", null) { _: MaterialType -> /* TODO */ }
        ).filter { it.first.contains(searchText.get(), ignoreCase = true) }

        if (items.isNotEmpty()) {
            if (ImGui.beginTable("SimulationTable", 2, ImGuiTableFlags.SizingFixedFit)) {
                for (item in items) {
                    ImGui.tableNextColumn()
                    renderPrefabItem(item.first, item.second, MaterialType.CONCRETE, onSpawn = item.third)
                }
                ImGui.endTable()
            }
        }
    }

    private fun renderEnvironmentPrefabs() {
        val items = listOf(
            Triple("Tile", Assets.Models.CUBE) { _: MaterialType -> spawnTile() }
        ).filter { it.first.contains(searchText.get(), ignoreCase = true) }

        if (items.isNotEmpty()) {
            if (ImGui.beginTable("EnvironmentTable", 2, ImGuiTableFlags.SizingFixedFit)) {
                for (item in items) {
                    ImGui.tableNextColumn()
                    renderPrefabItem(item.first, item.second, MaterialType.CONCRETE, onSpawn = item.third)
                }
                ImGui.endTable()
            }
        }
    }

    private fun renderObstaclePrefabs() {
        val metalOnly = listOf(MaterialType.METAL)
        val woodOrConcrete = listOf(
            MaterialType.CONCRETE,
            MaterialType.WOOD_BROWN,
            MaterialType.WOOD_LIGHT,
            MaterialType.WOOD_TAN,
            MaterialType.WOOD_DARK
        )
        // Specific constraints if we had the models:
        // Picnic Table -> Wood variants
        // Jersey Barrier -> Concrete only

        val configs = listOf(
            PrefabConfig("Rail", Assets.Models.RAIL, "PREFAB_RAIL", metalOnly) { mat -> spawnRail(material = mat) },
            PrefabConfig("Ledge", Assets.Models.LEDGE, "PREFAB_LEDGE", woodOrConcrete) { mat -> spawnLedge(material = mat) },
            PrefabConfig("Kicker", Assets.Models.KICKER, "PREFAB_KICKER", woodOrConcrete) { mat -> spawnKicker(material = mat) },
            PrefabConfig("Manual Pad", Assets.Models.MANUAL_PAD, "PREFAB_MANUAL_PAD", woodOrConcrete) { mat -> spawnManualPad(material = mat) },
            PrefabConfig("Bank", Assets.Models.BANK, "PREFAB_BANK", woodOrConcrete) { mat -> spawnBank(material = mat) },
            PrefabConfig("Quarter Pipe", Assets.Models.QUARTER_PIPE, "PREFAB_QUARTER_PIPE", woodOrConcrete) { mat -> spawnQuarterPipe(material = mat) }
        ).filter { it.name.contains(searchText.get(), ignoreCase = true) }

        if (configs.isNotEmpty()) {
            // Use 4 columns to show more variants
            if (ImGui.beginTable("ObstacleTable", 4, ImGuiTableFlags.SizingFixedFit)) {
                for (config in configs) {
                    for (material in config.allowedMaterials) {
                        ImGui.tableNextColumn()
                        val variantName = "${config.name} (${material.displayName})"
                        renderPrefabItem(variantName, config.modelPath, material, config.dragDropPayload, config.onSpawn)
                    }
                }
                ImGui.endTable()
            }
        }
    }

    private fun renderPrefabItem(
        name: String, 
        modelPath: String?, 
        material: MaterialType,
        dragDropPayload: String? = null, 
        onSpawn: (MaterialType) -> Unit
    ) {
        val size = 80f
        val padding = 5f
        
        ImGui.beginGroup()
        
        val texId = if (modelPath != null) {
            val rawModel = resourceManager.loadModelSync(modelPath).parts[0].rawModel
            val texture = resourceManager.loadTextureSync(material.texturePath)
            // Create a temporary TexturedModel for the thumbnail generator
            val model = TexturedModel(rawModel, texture)
            // Use specific ID per variant so they don't overwrite each other in cache
            val cacheId = "${modelPath}_${material.name}"
            thumbnailCache.getThumbnail(cacheId, model)
        } else {
            resourceManager.loadTextureSync(Assets.Textures.WHITE).texId
        }

        // Push ID to avoid collision if names are identical (though we made them unique with variant name)
        ImGui.pushID(name)
        if (ImGui.imageButton("PrefabItem", texId.toLong(), size, size, 0f, 1f, 1f, 0f)) {
            onSpawn(material)
        }
        ImGui.popID()
        
        if (dragDropPayload != null && ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload(dragDropPayload, name) // We might need to encode material in payload too
            // For now, dragging just uses the name, but onSpawn is what matters for clicking.
            // If dragging is critical for material selection, we'd need to serialize the material choice.
            // But typically dragging instantiates based on what was dragged.
            // Since we can't easily pass the material via simple string payload without parsing,
            // we will rely on the "Click to spawn" for specific variants or assume a default if dragged.
            // Wait, we CAN pass complex string "Rail|METAL".
            ImGui.image(texId.toLong(), 64f, 64f, 0f, 1f, 1f, 0f)
            ImGui.text(name)
            ImGui.endDragDropSource()
        }

        ImGui.textWrapped(name)
        ImGui.dummy(0f, padding)
        ImGui.endGroup()
    }

    fun spawnSkateboard() {
        val scene = SceneManager.getCurrentScene() ?: return
        
        JobSystem.runAsync {
            val model = resourceManager.loadModel(Assets.Models.SKATEBOARD_GLB)
            JobSystem.runOnMain {
                val skate = GameObject("Skateboard")
                skate.transform.translation.set(0f, 2f, 0f)
                skate.transform.scale.set(1.0f, 1.0f, 1.0f) // Now in Meters
                skate.addComponent(Entity(model = model))
                skate.addComponent(RigidBody3D(1.8f).apply { friction = 0.1f }) // 1.8kg mass
                skate.addComponent(BoxCollider3D(Vector3f(0.4f, 0.02f, 0.1f))) // 0.8m x 0.04m x 0.2m
                skate.addComponent(SkateboardPhysics())
                
                scene.addGameObjectToScene(skate)
            }
        }
    }

    fun spawnTile() {
        val scene = SceneManager.getCurrentScene() ?: return
        val tile = GameObject("Tile_${scene.gameObjects.size}")
        tile.addComponent(Entity(
            model = TexturedModel(
                resourceManager.loadModelSync(Assets.Models.CUBE).parts[0].rawModel,
                resourceManager.loadTextureSync(Assets.Textures.WHITE)
            )
        ))
        tile.addComponent(com.pafoid.skate.engine.scenes.components.ModularTile())
        tile.addComponent(RigidBody3D(0f).apply { bodyType = BodyType.Static })
        tile.addComponent(BoxCollider3D(Vector3f(1f, 1f, 1f)))
        scene.addGameObjectToScene(tile)
    }

    fun spawnRail(position: Vector3f = Vector3f(0f, 0.5f, 0f), material: MaterialType = MaterialType.METAL) {
        val scene = SceneManager.getCurrentScene() ?: return
        val rail = GameObject("Rail_${scene.gameObjects.size}")
        rail.transform.translation.set(position) 
        rail.transform.scale.set(1f, 1f, 1f)
        rail.addComponent(Entity(
            model = TexturedModel(
                resourceManager.loadModelSync(Assets.Models.RAIL).parts[0].rawModel,
                resourceManager.loadTextureSync(material.texturePath)
            )
        ))
        rail.addComponent(RigidBody3D(0f).apply { friction = 0.05f; bodyType = BodyType.Static })
        rail.addComponent(com.pafoid.skate.engine.physics3d.components.CylinderCollider3D(radius = 0.05f, height = 2.0f, axis = 0)) 
        scene.addGameObjectToScene(rail)
    }

    fun spawnLedge(position: Vector3f = Vector3f(0f, 0.25f, 0f), material: MaterialType = MaterialType.CONCRETE) {
        val scene = SceneManager.getCurrentScene() ?: return
        val ledge = GameObject("Ledge_${scene.gameObjects.size}")
        ledge.transform.translation.set(position) 
        ledge.transform.scale.set(1f, 1f, 1f)
        ledge.addComponent(Entity(
            model = TexturedModel(
                resourceManager.loadModelSync(Assets.Models.LEDGE).parts[0].rawModel,
                resourceManager.loadTextureSync(material.texturePath)
            )
        ))
        ledge.addComponent(RigidBody3D(0f).apply { friction = 0.6f; bodyType = BodyType.Static })
        ledge.addComponent(BoxCollider3D(Vector3f(0.5f, 0.25f, 0.5f)))
        scene.addGameObjectToScene(ledge)
    }

    fun spawnKicker(position: Vector3f = Vector3f(0f, 0f, 0f), material: MaterialType = MaterialType.CONCRETE) {
        val scene = SceneManager.getCurrentScene() ?: return
        val kicker = GameObject("Kicker_${scene.gameObjects.size}")
        kicker.transform.translation.set(position)
        kicker.transform.scale.set(1f, 1f, 1f)
        kicker.addComponent(Entity(
            model = TexturedModel(
                resourceManager.loadModelSync(Assets.Models.KICKER).parts[0].rawModel,
                resourceManager.loadTextureSync(material.texturePath)
            )
        ))
        kicker.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = BodyType.Static })
        
        val kickerRawModel = resourceManager.loadModelSync(Assets.Models.KICKER).parts[0].rawModel
        val jmeVertices = mutableListOf<JmeVector3f>()
        for (i in 0 until kickerRawModel.vertices.size / 3) {
            jmeVertices.add(JmeVector3f(kickerRawModel.vertices[i*3], kickerRawModel.vertices[i*3+1], kickerRawModel.vertices[i*3+2]))
        }
        
        if (jmeVertices.isNotEmpty()) {
            val kickerShape = HullCollisionShape(jmeVertices)
            kicker.addComponent(CustomCollider3D(kickerShape))
        }
        
        scene.addGameObjectToScene(kicker)
    }

    fun spawnManualPad(position: Vector3f = Vector3f(0f, 0.1f, 0f), material: MaterialType = MaterialType.CONCRETE) {
        val scene = SceneManager.getCurrentScene() ?: return
        val go = GameObject("ManualPad_${scene.gameObjects.size}")
        go.transform.translation.set(position)
        go.addComponent(Entity(
            model = TexturedModel(
                resourceManager.loadModelSync(Assets.Models.MANUAL_PAD).parts[0].rawModel,
                resourceManager.loadTextureSync(material.texturePath)
            )
        ))
        go.addComponent(RigidBody3D(0f).apply { friction = 0.6f; bodyType = BodyType.Static })
        go.addComponent(BoxCollider3D(Vector3f(1f, 0.1f, 1f)))
        scene.addGameObjectToScene(go)
    }

    fun spawnBank(position: Vector3f = Vector3f(0f, 0f, 0f), material: MaterialType = MaterialType.CONCRETE) {
        val scene = SceneManager.getCurrentScene() ?: return
        val go = GameObject("Bank_${scene.gameObjects.size}")
        go.transform.translation.set(position)
        go.addComponent(Entity(
            model = TexturedModel(
                resourceManager.loadModelSync(Assets.Models.BANK).parts[0].rawModel,
                resourceManager.loadTextureSync(material.texturePath)
            )
        ))
        go.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = BodyType.Static })
        
        val rawModel = resourceManager.loadModelSync(Assets.Models.BANK).parts[0].rawModel
        val jmeVertices = mutableListOf<JmeVector3f>()
        for (i in 0 until rawModel.vertices.size / 3) {
            jmeVertices.add(JmeVector3f(rawModel.vertices[i*3], rawModel.vertices[i*3+1], rawModel.vertices[i*3+2]))
        }
        val shape = HullCollisionShape(jmeVertices)
        go.addComponent(CustomCollider3D(shape))
        
        scene.addGameObjectToScene(go)
    }

    fun spawnQuarterPipe(position: Vector3f = Vector3f(0f, 0f, 0f), material: MaterialType = MaterialType.CONCRETE) {
        val scene = SceneManager.getCurrentScene() ?: return
        val go = GameObject("QuarterPipe_${scene.gameObjects.size}")
        go.transform.translation.set(position)
        go.addComponent(Entity(
            model = TexturedModel(
                resourceManager.loadModelSync(Assets.Models.QUARTER_PIPE).parts[0].rawModel,
                resourceManager.loadTextureSync(material.texturePath)
            )
        ))
        go.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = BodyType.Static })
        
        val rawModel = resourceManager.loadModelSync(Assets.Models.QUARTER_PIPE).parts[0].rawModel
        val jmeVertices = mutableListOf<JmeVector3f>()
        for (i in 0 until rawModel.vertices.size / 3) {
            jmeVertices.add(JmeVector3f(rawModel.vertices[i*3], rawModel.vertices[i*3+1], rawModel.vertices[i*3+2]))
        }
        val shape = HullCollisionShape(jmeVertices)
        go.addComponent(CustomCollider3D(shape))
        
        scene.addGameObjectToScene(go)
    }
}

