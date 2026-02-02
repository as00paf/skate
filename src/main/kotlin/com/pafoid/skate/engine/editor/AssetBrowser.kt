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
import com.pafoid.skate.engine.prefabs.MaterialType
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.PrefabsGenerator
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.components.SkateboardPhysics
import imgui.ImGui
import imgui.flag.ImGuiTableFlags
import imgui.flag.ImGuiTreeNodeFlags
import imgui.type.ImString
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import kotlin.getValue
import com.jme3.math.Vector3f as JmeVector3f // Alias for JME Vector3f

class AssetBrowser : KoinComponent {
    private val thumbnailCache: ThumbnailCache by inject()
    private val resourceManager: ResourceManager by inject()
    private val sceneManager: SceneManager by inject()
    private val prefabsGenerator: PrefabsGenerator by inject()

    private var searchText = ImString(256)
    
    private val modelFiles = mutableListOf<File>()
    private val textureFiles = mutableListOf<File>()

    private val texturesTab = AssetBrowserTab(resourceManager, thumbnailCache)
    private val modelsTab = AssetBrowserTab(resourceManager, thumbnailCache)

    init {
        refreshAssets()
    }
    
    private fun refreshAssets() {
        JobSystem.runIO {
            modelFiles.clear()
            val modelsDir = File("assets")
            if (modelsDir.exists()) {
                modelsDir.walkTopDown().filter {
                    it.isFile && (it.extension == "obj" || it.extension == "gltf" || it.extension == "glb" || it.extension == "fbx" || it.extension == "dae")
                }.forEach { modelFiles.add(it) }
            }

            textureFiles.clear()
            val texturesDir = File("assets/textures")
            if (texturesDir.exists()) {
                texturesDir.walkTopDown().filter {
                    it.isFile && (it.extension == "png" || it.extension == "jpg" || it.extension == "jpeg")
                }.forEach { textureFiles.add(it) }
            }
        }
    }

    fun imgui() {
        ImGui.begin("Asset Browser")

        if (ImGui.beginTabBar("AssetBrowserTabs")) {
            if (ImGui.beginTabItem("Models")) {
                modelsTab.render("##searchModels", searchText, AssetBrowserTab.Type.MODELS, modelFiles, ::refreshAssets)
                ImGui.endTabItem()
            }
            if (ImGui.beginTabItem("Textures")) {
                texturesTab.render("##searchTextures", searchText, AssetBrowserTab.Type.TEXTURES, textureFiles, ::refreshAssets)
                ImGui.endTabItem()
            }
            if (ImGui.beginTabItem("Prefabs")) {
                renderPrefabsTab()
                ImGui.endTabItem()
            }
            ImGui.endTabBar()
        }

        ImGui.end()
    }

    private fun renderPrefabsTab() {
        ImGui.inputTextWithHint("##searchPrefabs", "${Icons.SEARCH} Search...", searchText)
        ImGui.separator()

       if (ImGui.collapsingHeader("${Icons.CUBE} Simulation", ImGuiTreeNodeFlags.DefaultOpen)) {
            renderSimulationPrefabs()
        }

        /*if (ImGui.collapsingHeader("${Icons.PALETTE} Environment", ImGuiTreeNodeFlags.DefaultOpen)) {
           renderEnvironmentPrefabs()
       }*/

        if (ImGui.collapsingHeader("${Icons.GEAR} Obstacles", ImGuiTreeNodeFlags.DefaultOpen)) {
            renderObstaclePrefabs()
        }
    }

    private fun renderSimulationPrefabs() {
        val items = listOf(
            PrefabConfig("Skateboard", PrefabType.SKATEBOARD, Assets.Models.SKATEBOARD_GLB, "PREFAB_SKATEBOARD", listOf()),
           //PrefabConfig("Skater", PrefabType.SKATER, Assets.Models.JAMES, "PREFAB_SKATER", listOf(), ::spawnSkater),
        ).filter { it.name.contains(searchText.get(), ignoreCase = true) }

        if (items.isNotEmpty()) {
            if (ImGui.beginTable("SimulationTable", 2, ImGuiTableFlags.SizingFixedFit)) {
                for (item in items) {
                    ImGui.tableNextColumn()
                    renderPrefabItem(PrefabData(item.name, item.type, item.modelPath, item.dragDropPayload))
                }
                ImGui.endTable()
            }
        }
    }

    private fun renderEnvironmentPrefabs() {
        /*val items = listOf(
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
        }*/
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
            PrefabConfig("Rail", PrefabType.RAIL, Assets.Models.RAIL, "PREFAB_RAIL", metalOnly),
            PrefabConfig("Ledge", PrefabType.LEDGE, Assets.Models.LEDGE, "PREFAB_LEDGE", woodOrConcrete),
            PrefabConfig("Kicker", PrefabType.KICKER, Assets.Models.KICKER, "PREFAB_KICKER", woodOrConcrete),
            PrefabConfig("Manual Pad", PrefabType.MANUAL_PAD, Assets.Models.MANUAL_PAD, "PREFAB_MANUAL_PAD", woodOrConcrete),
            PrefabConfig("Bank", PrefabType.BANK, Assets.Models.BANK, "PREFAB_BANK", woodOrConcrete),
            PrefabConfig("Quarter Pipe", PrefabType.QUARTER_PIPE, Assets.Models.QUARTER_PIPE, "PREFAB_QUARTER_PIPE", woodOrConcrete),
        ).filter { it.name.contains(searchText.get(), ignoreCase = true) }

        if (configs.isNotEmpty()) {
            // Use 4 columns to show more variants
            if (ImGui.beginTable("ObstacleTable", 4, ImGuiTableFlags.SizingFixedFit)) {
                for (config in configs) {
                    for (material in config.allowedMaterials) {
                        ImGui.tableNextColumn()
                        val variantName = "${config.name} (${material.displayName})"
                        val data = PrefabData(variantName, config.type, config.modelPath, config.dragDropPayload, material)
                        renderPrefabItem(data)
                    }
                }
                ImGui.endTable()
            }
        }
    }

    private fun renderPrefabItem(
        data: PrefabData,
    ) {
        val size = 80f
        ImGui.beginGroup()
        
        val texId = if (data.modelPath != null) {
            val rawModel = resourceManager.loadModelSync(data.modelPath).parts[0].rawModel
            val texture = resourceManager.loadTextureSync(data.material?.texturePath)
            // Create a temporary TexturedModel for the thumbnail generator
            val model = TexturedModel(rawModel, texture)
            // Use specific ID per variant so they don't overwrite each other in cache
            val cacheId = "${data.modelPath}_${data.material?.name}"
            thumbnailCache.getThumbnail(cacheId, model)
        } else {
            resourceManager.loadTextureSync(Assets.Textures.DEFAULT).texId
        }

        // Push ID to avoid collision if names are identical (though we made them unique with variant name)
        ImGui.pushID(data.name)
        if (ImGui.imageButton("PrefabItem", texId.toLong(), size, size, 0f, 1f, 1f, 0f)) {
            when(data.type) {
                PrefabType.SKATEBOARD -> prefabsGenerator.spawnSkateboard()
                PrefabType.SKATER -> prefabsGenerator.spawnSkater()
                PrefabType.LEDGE -> prefabsGenerator.spawnLedge(material = data.material)
                PrefabType.RAIL -> prefabsGenerator.spawnRail(material = data.material)
                PrefabType.KICKER -> prefabsGenerator.spawnKicker(material = data.material)
                PrefabType.MANUAL_PAD -> prefabsGenerator.spawnManualPad(material = data.material)
                PrefabType.BANK -> prefabsGenerator.spawnBank(material = data.material)
                PrefabType.QUARTER_PIPE -> prefabsGenerator.spawnQuarterPipe(material = data.material)
            }
        }
        ImGui.popID()
        
        if (data.dragDropPayload != null && ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload(data.dragDropPayload, data) // We might need to encode material in payload too
            ImGui.image(texId.toLong(), 64f, 64f, 0f, 1f, 1f, 0f)
            ImGui.text(data.name)
            ImGui.endDragDropSource()
        }

        ImGui.textWrapped(data.name)
        ImGui.endGroup()
    }
}

