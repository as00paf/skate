package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.data.PrefabData
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.imgui.contextPopupId
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.data.UiConstants.ASSET_BROWSER_ITEM_WIDTH
import com.pafoid.skate.editor.systems.ThumbnailCache
import com.pafoid.skate.engine.assets.AssetState
import com.pafoid.skate.engine.assets.AssetTicket
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.game.prefabs.MaterialType
import com.pafoid.skate.game.prefabs.PrefabCatalog
import com.pafoid.skate.game.prefabs.PrefabCategory
import com.pafoid.skate.game.prefabs.PrefabEntry
import imgui.ImGui
import imgui.flag.ImGuiTableFlags
import imgui.flag.ImGuiTreeNodeFlags
import imgui.type.ImInt
import imgui.type.ImString
import java.awt.Desktop
import java.io.File

/**
 * Prefab picker: every [PrefabCatalog] entry as a thumbnail tile, grouped by [PrefabCategory]. Click (or the
 * context menu) spawns at the origin, dragging onto the viewport spawns at the drop point. Obstacles use the
 * surface chosen in the Material combo when they offer it (rails stay metal).
 */
class PrefabsTab(
    private val engine: Engine,
    stringManager: StringManager,
) : AssetBrowserTab(engine.assetsManager, stringManager) {

    private val thumbnailCache: ThumbnailCache by lazy { ThumbnailCache(engine.renderer.renderResources.renderers.thumbnail) }
    private val surfaces = MaterialType.entries
    private val surfaceIndex = ImInt(0)
    private val thumbnails = BudgetedThumbnails<Int>(perFrame = 1)
    private val modelTickets = HashMap<String, AssetTicket<`3dModel`>>()
    private val textureTickets = HashMap<String, AssetTicket<Texture>>()

    override fun imgui(label: String, searchText: ImString) {
        thumbnails.beginFrame()
        renderHeader(label, searchText)

        val names = surfaces.map { it.displayName }.toTypedArray()
        ImGui.setNextItemWidth(180f)
        ImGui.combo(stringManager.label("lbl.material"), surfaceIndex, names, names.size)

        val matching = PrefabCategory.entries.sumOf { c -> PrefabCatalog.inCategory(c).count { displayName(it).contains(searchText.get(), ignoreCase = true) } }
        if (renderEmptyState(PrefabCategory.entries.sumOf { PrefabCatalog.inCategory(it).size }, matching, searchText)) return

        PrefabCategory.entries.forEach { category ->
            val entries = PrefabCatalog.inCategory(category).filter {
                displayName(it).contains(searchText.get(), ignoreCase = true)
            }
            if (entries.isEmpty()) return@forEach
            val header = "${iconOf(category)} ${stringManager.getString("lbl.prefabs.${category.name.lowercase()}")}"
            if (ImGui.collapsingHeader("$header###prefabs_${category.name}", ImGuiTreeNodeFlags.DefaultOpen)) {
                renderGrid(category, entries)
            }
        }
    }

    private fun iconOf(category: PrefabCategory) = when (category) {
        PrefabCategory.PLAYER -> Icons.CUBE
        PrefabCategory.GENERIC -> Icons.STAR
        else -> Icons.GEAR
    }

    private fun displayName(entry: PrefabEntry) = stringManager.getString(entry.type.nameKey)

    private fun renderGrid(category: PrefabCategory, entries: List<PrefabEntry>) {
        val numColumns = 1.coerceAtLeast((ImGui.getContentRegionAvailX() / ASSET_BROWSER_ITEM_WIDTH).toInt())
        ImGui.pushID("Prefabs${category.name}")
        if (ImGui.beginTable("PrefabTable", numColumns, ImGuiTableFlags.SizingFixedFit)) {
            entries.forEach { entry ->
                ImGui.tableNextColumn()
                renderPrefabItem(entry)
            }
            ImGui.endTable()
        }
        ImGui.popID()
    }

    private fun renderPrefabItem(entry: PrefabEntry) {
        val size = 80f
        val name = displayName(entry)
        val data = PrefabData(entry.type, PrefabCatalog.materialFor(entry.type, surfaces[surfaceIndex.get()]))
        ImGui.beginGroup()

        val texId = thumbnailTexId(entry, data.material)

        ImGui.pushID(entry.type.name)
        val clicked = if (texId != null) {
            ImGui.imageButton("PrefabItem", texId.toLong(), size, size, 0f, 1f, 1f, 0f)
        } else {
            ImGui.button(iconOf(entry.category), size, size) // placeholder while the model loads (AST-23)
        }
        if (clicked) {
            spawnPrefab(data)
        }

        if (ImGui.beginPopupContextItem(contextPopupId("prefab"))) {
            if (stringManager.tipped(ImGui.menuItem("${Icons.PLUS} ${stringManager.getString("context.asset_browser.spawn_in_scene")}"), "context.asset_browser.spawn_in_scene")) {
                spawnPrefab(data)
            }
            ImGui.separator()
            if (stringManager.tipped(ImGui.menuItem("${Icons.FOLDER} ${stringManager.getString("context.asset_browser.show_in_folder")}"), "context.asset_browser.show_in_folder")) {
                File(entry.modelPath).absoluteFile.parentFile?.let { Desktop.getDesktop().open(it) }
            }
            ImGui.separator()
            if (stringManager.tipped(ImGui.menuItem("${Icons.INFO} ${stringManager.getString("context.asset_browser.properties")}"), "context.asset_browser.properties")) {
                engine.logger.logEditor("Properties: $name, Type: ${entry.type}, Material: ${data.material?.displayName}")
            }
            ImGui.endPopup()
        }

        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload(PrefabData.PAYLOAD, data)
            texId?.let { ImGui.image(it.toLong(), size * 1.2f, size * 1.2f, 0f, 1f, 1f, 0f) }
            ImGui.text(name)
            data.material?.let {
                ImGui.textColored(0.7f, 0.7f, 0.7f, 1f, "${stringManager.getString("lbl.material")}: ${it.displayName}")
            }
            ImGui.endDragDropSource()
        }

        ImGui.popID()

        ImGui.textWrapped(name)
        ImGui.endGroup()
    }

    /**
     * Thumbnail of [entry] drawn with [surface], the texture the spawned prefab gets (AST-13). The model and texture
     * load asynchronously (worker decode, main-thread upload); the render waits for both and runs once per frame at
     * most (AST-23). Null until then.
     */
    private fun thumbnailTexId(entry: PrefabEntry, surface: MaterialType?): Int? {
        val key = PrefabThumbnailMaterial.cacheKey(entry, surface)
        val model = modelTickets.getOrPut(entry.modelPath) { assetsManager.requestModel(entry.modelPath) }
        val texturePath = surface?.let { PrefabThumbnailMaterial.texturePath(it, assetsManager.paths.projectRoot) }
        val texture = texturePath?.let { path -> textureTickets.getOrPut(path) { assetsManager.requestTexture(path) } }
        return thumbnails.get(
            key,
            ready = { model.state !== AssetState.Loading && texture?.state !== AssetState.Loading },
            render = {
                val textured = PrefabThumbnailMaterial.applyTexture(model.get() ?: `3dModel`(entry.modelPath), texture?.takeIf { it.isReady }?.get())
                thumbnailCache.getThumbnail(key, textured)
            },
        )
    }

    private fun spawnPrefab(data: PrefabData) {
        engine.eventSystem.publish(ViewportAction.SpawnPrefab(data.type, material = data.material))
    }

    override fun refreshAssets() {
    }
}
