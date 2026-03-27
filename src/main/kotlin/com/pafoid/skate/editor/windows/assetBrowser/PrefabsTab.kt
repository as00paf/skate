package com.pafoid.skate.editor.windows.assetBrowser

import com.pafoid.skate.editor.data.PrefabConfig
import com.pafoid.skate.editor.data.PrefabData
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.PrefabsGenerator
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.ThumbnailCache
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.utils.JobSystem
import com.pafoid.skate.game.prefabs.MaterialType
import imgui.ImGui
import imgui.flag.ImGuiTableFlags
import imgui.flag.ImGuiTreeNodeFlags
import imgui.type.ImString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.awt.Desktop
import java.io.File

class PrefabsTab(
    resourceManager: ResourceManager,
    thumbnailCache: ThumbnailCache,
    stringManager: StringManager,
    private val prefabsGenerator: PrefabsGenerator
): AssetBrowserTab(resourceManager, thumbnailCache, stringManager), KoinComponent {

    private val logger: LoggerService by inject()

    override fun imgui(label: String, searchText: ImString) {
        renderHeader(label, searchText)

        if (ImGui.collapsingHeader("${Icons.CUBE} Player", ImGuiTreeNodeFlags.DefaultOpen)) {
            renderPlayerPrefabs(searchText)
        }

        if (ImGui.collapsingHeader("${Icons.GEAR} Obstacles", ImGuiTreeNodeFlags.DefaultOpen)) {
            renderObstaclePrefabs(searchText)
        }
    }

    private fun renderPlayerPrefabs(searchText: ImString) {
        val availableWidth = ImGui.getContentRegionAvailX()
        val numColumns = Math.max(1, (availableWidth / ITEM_WIDTH).toInt())

        val items = listOf(
            PrefabConfig(
                "Skateboard",
                PrefabType.SKATEBOARD,
                Assets.Models.SKATEBOARD_GLB,
                "PREFAB_SKATEBOARD",
                listOf()
            ),
            //PrefabConfig("Skater", PrefabType.SKATER, Assets.Models.JAMES, "PREFAB_SKATER", listOf(), ::spawnSkater),
        ).filter { it.name.contains(searchText.get(), ignoreCase = true) }

        if (items.isNotEmpty()) {
            ImGui.pushID("PlayerPrefabs")
            if (ImGui.beginTable("SimulationTable", numColumns, ImGuiTableFlags.SizingFixedFit)) {
                for (item in items) {
                    ImGui.tableNextColumn()
                    renderPrefabItem(PrefabData(item.name, item.type, item.modelPath, item.dragDropPayload))
                }
                ImGui.endTable()
            }
            ImGui.popID()
        }
    }

    private fun renderObstaclePrefabs(searchText: ImString) {
        val availableWidth = ImGui.getContentRegionAvailX()
        val numColumns = Math.max(1, (availableWidth / ITEM_WIDTH).toInt())

        val metalOnly = listOf(MaterialType.METAL)
        val woodOrConcrete = listOf(
            MaterialType.CONCRETE,
            MaterialType.WOOD_BROWN,
            MaterialType.WOOD_LIGHT,
            MaterialType.WOOD_TAN,
            MaterialType.WOOD_DARK
        )

        val configs = listOf(
            PrefabConfig("Rail", PrefabType.RAIL, Assets.Models.RAIL, "PREFAB_RAIL", metalOnly),
            PrefabConfig("Ledge", PrefabType.LEDGE, Assets.Models.LEDGE, "PREFAB_LEDGE", woodOrConcrete),
            PrefabConfig("Kicker", PrefabType.KICKER, Assets.Models.KICKER, "PREFAB_KICKER", woodOrConcrete),
            PrefabConfig(
                "Manual Pad",
                PrefabType.MANUAL_PAD,
                Assets.Models.MANUAL_PAD,
                "PREFAB_MANUAL_PAD",
                woodOrConcrete
            ),
            PrefabConfig("Bank", PrefabType.BANK, Assets.Models.BANK, "PREFAB_BANK", woodOrConcrete),
            PrefabConfig(
                "Quarter Pipe",
                PrefabType.QUARTER_PIPE,
                Assets.Models.QUARTER_PIPE,
                "PREFAB_QUARTER_PIPE",
                woodOrConcrete
            ),
        ).filter { it.name.contains(searchText.get(), ignoreCase = true) }

        if (configs.isNotEmpty()) {
            ImGui.pushID("ObstaclePrefabs")
            if (ImGui.beginTable("ObstacleTable", numColumns, ImGuiTableFlags.SizingFixedFit)) {
                for (config in configs) {
                    for (material in config.allowedMaterials) {
                        ImGui.tableNextColumn()
                        val variantName = "${config.name} (${material.displayName})"
                        val data =
                            PrefabData(variantName, config.type, config.modelPath, config.dragDropPayload, material)
                        renderPrefabItem(data)
                    }
                }
                ImGui.endTable()
            }
            ImGui.popID()
        }
    }

    private fun renderPrefabItem(
        data: PrefabData,
    ) {
        val size = 80f
        ImGui.beginGroup()

        val texId = if (data.modelPath != null) {
            val baseModel = resourceManager.loadModelSync(data.modelPath)
            val rawModel = baseModel.mesh[0].rawModel
            val texture = resourceManager.loadTextureSync(data.material?.texturePath)
            // Create a temporary TexturedModel for the thumbnail generator
            val model = TexturedModel(rawModel, texture)
            // Use specific ID per variant so they don't overwrite each other in cache
            val cacheId = "${data.modelPath}_${data.material?.name}"
            thumbnailCache.getThumbnail(cacheId, model)
        } else {
            resourceManager.loadTextureSync(Assets.Textures.DEFAULT).texId
        }

        // Push ID to avoid collision if names are identical
        ImGui.pushID(data.name)
        if (ImGui.imageButton("PrefabItem", texId.toLong(), size, size, 0f, 1f, 1f, 0f)) {
            JobSystem.runOnMain {
                when (data.type) {
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
        }
        
        // Context menu on right-click
        if (ImGui.beginPopupContextItem()) {
            if (ImGui.menuItem("${Icons.PLUS} ${stringManager.getString("context.asset_browser.spawn_in_scene")}")) {
                JobSystem.runOnMain {
                    when (data.type) {
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
            }
            ImGui.separator()
            if (ImGui.menuItem("${Icons.STAR} ${stringManager.getString("context.asset_browser.add_to_favorites")}")) {
                // TODO: Implement favorites system
                logger.logEditor("Add to favorites not yet implemented")
            }
            if (ImGui.menuItem("${Icons.FOLDER} ${stringManager.getString("context.asset_browser.show_in_folder")}")) {
                // Open folder in file explorer
                java.awt.Desktop.getDesktop().open(File(data.modelPath ?: ".").parentFile)
            }
            ImGui.separator()
            if (ImGui.menuItem("${Icons.INFO} ${stringManager.getString("context.asset_browser.properties")}")) {
                logger.logEditor("Properties: ${data.name}, Type: ${data.type}, Material: ${data.material?.name}")
            }
            ImGui.endPopup()
        }
        
        if (data.dragDropPayload != null && ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload(data.dragDropPayload, data)
            // Enhanced drag preview with larger image and material info
            ImGui.image(texId.toLong(), size*1.2f, size*1.2f, 0f, 1f, 1f, 0f)
            ImGui.text(data.name)
            if (data.material != null) {
                ImGui.textColored(0.7f, 0.7f, 0.7f, 1f, "Material: ${data.material.displayName}")
            }
            ImGui.endDragDropSource()
        }
        
        ImGui.popID()

        ImGui.textWrapped(data.name)
        ImGui.endGroup()
    }

    override fun refreshAssets() {
        // Prefabs are static for now, no need to crawl files
    }
}
