package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.editor.data.PrefabData
import com.pafoid.skate.editor.data.PrefabType
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.PrefabsGenerator
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.ThumbnailCache
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.utils.IJobSystem
import com.pafoid.skate.game.prefabs.MaterialType
import imgui.ImGui
import imgui.flag.ImGuiTableFlags
import imgui.flag.ImGuiTreeNodeFlags
import imgui.type.ImString
import org.koin.core.component.KoinComponent
import java.awt.Desktop
import java.io.File

class PrefabsTab(
    resourceManager: ResourceManager,
    stringManager: StringManager,
    private val thumbnailCache: ThumbnailCache,
    private val prefabsGenerator: PrefabsGenerator,
    private val logger: LoggerService,
    private val jobSystem: IJobSystem,
): AssetBrowserTab(resourceManager, stringManager), KoinComponent {


    override fun imgui(label: String, searchText: ImString) {
        renderHeader(label, searchText)

        if (ImGui.collapsingHeader("${Icons.CUBE} ${stringManager.getString("lbl.prefabs.player")}", ImGuiTreeNodeFlags.DefaultOpen)) {
            renderPlayerPrefabs(searchText)
        }

        if (ImGui.collapsingHeader("${Icons.GEAR} ${stringManager.getString("lbl.prefabs.obstacles")}", ImGuiTreeNodeFlags.DefaultOpen)) {
            renderObstaclePrefabs(searchText)
        }
    }

    private fun renderPlayerPrefabs(searchText: ImString) {
        val availableWidth = ImGui.getContentRegionAvailX()
        val numColumns = Math.max(1, (availableWidth / ITEM_WIDTH).toInt())

        val templates = listOf(
            PrefabData.createTemplate(
                "Skateboard",
                PrefabType.SKATEBOARD,
                Assets.Models.SKATEBOARD_GLB,
                PrefabData.PAYLOAD_SKATEBOARD
            ),
            PrefabData.createTemplate(
                "Skater",
                PrefabType.SKATER,
                Assets.Models.JAMES,
                PrefabData.PAYLOAD_SKATER
            ),
        ).filter { it.name.contains(searchText.get(), ignoreCase = true) }

        if (templates.isNotEmpty()) {
            ImGui.pushID("PlayerPrefabs")
            if (ImGui.beginTable("SimulationTable", numColumns, ImGuiTableFlags.SizingFixedFit)) {
                for (item in templates) {
                    ImGui.tableNextColumn()
                    renderPrefabItem(item)
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

        val templates = listOf(
            PrefabData.createTemplate("Rail", PrefabType.RAIL, Assets.Models.RAIL, PrefabData.PAYLOAD_RAIL),
            PrefabData.createTemplate("Ledge", PrefabType.LEDGE, Assets.Models.LEDGE, PrefabData.PAYLOAD_LEDGE),
            PrefabData.createTemplate("Kicker", PrefabType.KICKER, Assets.Models.KICKER, PrefabData.PAYLOAD_KICKER),
            PrefabData.createTemplate("Manual Pad", PrefabType.MANUAL_PAD, Assets.Models.MANUAL_PAD, PrefabData.PAYLOAD_MANUAL_PAD),
            PrefabData.createTemplate("Bank", PrefabType.BANK, Assets.Models.BANK, PrefabData.PAYLOAD_BANK),
            PrefabData.createTemplate("Quarter Pipe", PrefabType.QUARTER_PIPE, Assets.Models.QUARTER_PIPE, PrefabData.PAYLOAD_QUARTER_PIPE),
        ).filter { it.name.contains(searchText.get(), ignoreCase = true) }

        if (templates.isNotEmpty()) {
            ImGui.pushID("ObstaclePrefabs")
            if (ImGui.beginTable("ObstacleTable", numColumns, ImGuiTableFlags.SizingFixedFit)) {
                for (template in templates) {
                    val materials = when (template.type) {
                        PrefabType.RAIL -> metalOnly
                        else -> woodOrConcrete
                    }
                    val variants = PrefabData.expandToVariants(template, materials)
                    for (data in variants) {
                        ImGui.tableNextColumn()
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
            val texture = resourceManager.loadTextureSync(data.material?.texturePath ?: Assets.Textures.DEFAULT)
            val model = TexturedModel(rawModel, texture)
            val cacheId = "${data.modelPath}_${data.material?.name}"
            thumbnailCache.getThumbnail(cacheId, model)
        } else {
            resourceManager.loadTextureSync(Assets.Textures.DEFAULT).texId
        }

        ImGui.pushID(data.name)
        if (ImGui.imageButton("PrefabItem", texId.toLong(), size, size, 0f, 1f, 1f, 0f)) {
            jobSystem.runOnMain {
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

        if (ImGui.beginPopupContextItem()) {
            if (ImGui.menuItem("${Icons.PLUS} ${stringManager.getString("context.asset_browser.spawn_in_scene")}")) {
                jobSystem.runOnMain {
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
                // Future enhancement: Implement favorites system for quick access to prefabs
                logger.logEditor("Add to favorites not yet implemented")
            }
            if (ImGui.menuItem("${Icons.FOLDER} ${stringManager.getString("context.asset_browser.show_in_folder")}")) {
                Desktop.getDesktop().open(File(data.modelPath ?: ".").parentFile)
            }
            ImGui.separator()
            if (ImGui.menuItem("${Icons.INFO} ${stringManager.getString("context.asset_browser.properties")}")) {
                logger.logEditor("Properties: ${data.name}, Type: ${data.type}, Material: ${data.material?.name}")
            }
            ImGui.endPopup()
        }
        
        if (data.dragDropPayloadType != null && ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload(data.dragDropPayloadType, data)
            ImGui.image(texId.toLong(), size*1.2f, size*1.2f, 0f, 1f, 1f, 0f)
            ImGui.text(data.name)
            if (data.material != null) {
                ImGui.textColored(0.7f, 0.7f, 0.7f, 1f, "${stringManager.getString("lbl.material")}: ${data.material.displayName}")
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
