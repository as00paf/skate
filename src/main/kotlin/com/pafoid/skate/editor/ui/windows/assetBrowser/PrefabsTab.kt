package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.editor.data.PrefabData
import com.pafoid.skate.editor.data.PrefabType
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.data.UiConstants.ASSET_BROWSER_ITEM_WIDTH
import com.pafoid.skate.editor.systems.ThumbnailCache
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.game.prefabs.MaterialType
import imgui.ImGui
import imgui.flag.ImGuiTableFlags
import imgui.flag.ImGuiTreeNodeFlags
import imgui.type.ImString
import java.awt.Desktop
import java.io.File

class PrefabsTab(
    private val engine: Engine,
    stringManager: StringManager,
    private val prefabsGenerator: PrefabsGenerator,
) : AssetBrowserTab(engine.assetsManager, stringManager) {

    private val thumbnailCache: ThumbnailCache by lazy { ThumbnailCache(engine.renderer.renderResources.renderers.thumbnail) }

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
        val numColumns = 1.coerceAtLeast((availableWidth / ASSET_BROWSER_ITEM_WIDTH).toInt())

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
        val numColumns = 1.coerceAtLeast((availableWidth / ASSET_BROWSER_ITEM_WIDTH).toInt())

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
            val baseModel = assetsManager.loadModel(data.modelPath)

            val texture =
                data.material?.texturePath?.let { assetsManager.getTexture(it) } ?: assetsManager.getBundledTexture(
                    Assets.Bundled.DEFAULT_TEXTURE
                )
            val model =
                TexturedModel(
                    data.material?.texturePath ?: Assets.Bundled.DEFAULT_TEXTURE,
                    mesh = baseModel.mesh,
                    material = Material(texture)
                )
            val cacheId = "${data.modelPath}_${data.material?.name}"
            thumbnailCache.getThumbnail(cacheId, model)
        } else {
            assetsManager.getBundledTexture(Assets.Bundled.DEFAULT_TEXTURE).texId
        }

        ImGui.pushID(data.name)
        if (ImGui.imageButton("PrefabItem", texId.toLong(), size, size, 0f, 1f, 1f, 0f)) {
            spawnPrefab(data)
        }

        if (ImGui.beginPopupContextItem()) {
            if (ImGui.menuItem("${Icons.PLUS} ${stringManager.getString("context.asset_browser.spawn_in_scene")}")) {
                spawnPrefab(data)
            }
            ImGui.separator()
            if (ImGui.menuItem("${Icons.STAR} ${stringManager.getString("context.asset_browser.add_to_favorites")}")) {
                // Future enhancement: Implement favorites system for quick access to prefabs
                engine.logger.logEditor("Add to favorites not yet implemented")
            }
            if (ImGui.menuItem("${Icons.FOLDER} ${stringManager.getString("context.asset_browser.show_in_folder")}")) {
                Desktop.getDesktop().open(File(data.modelPath ?: ".").parentFile)
            }
            ImGui.separator()
            if (ImGui.menuItem("${Icons.INFO} ${stringManager.getString("context.asset_browser.properties")}")) {
                engine.logger.logEditor("Properties: ${data.name}, Type: ${data.type}, Material: ${data.material?.name}")
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

    private fun spawnPrefab(data: PrefabData) {
        engine.jobSystem.runOnMain {
            val go = when (data.type) {
                PrefabType.SKATEBOARD -> prefabsGenerator.spawnSkateboard()
                PrefabType.SKATER -> prefabsGenerator.spawnSkater()
                PrefabType.LEDGE -> prefabsGenerator.spawnLedge(material = data.material)
                PrefabType.RAIL -> prefabsGenerator.spawnRail(material = data.material)
                PrefabType.KICKER -> prefabsGenerator.spawnKicker(material = data.material)
                PrefabType.MANUAL_PAD -> prefabsGenerator.spawnManualPad(material = data.material)
                PrefabType.BANK -> prefabsGenerator.spawnBank(material = data.material)
                PrefabType.QUARTER_PIPE -> prefabsGenerator.spawnQuarterPipe(material = data.material)
            }
            engine.gameObjectManager.addGameObject(go)
        }
    }

    override fun refreshAssets() {

    }
}
