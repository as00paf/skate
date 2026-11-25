package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.ui.windows.assetBrowser.AssetBrowserListing
import com.pafoid.skate.engine.assets.AssetFolder
import com.pafoid.skate.engine.core.Engine
import imgui.ImGui
import imgui.type.ImString

/**
 * The picker half of an asset reference (EDT-108): a combo listing the project's and the engine defaults' files of one
 * [AssetFolder] with a filter box, a drop target for files dragged from the Asset Browser or Project window, and the
 * "file not found" flag. It only reports the chosen path through `onPick`, so any field can reuse it, whether the
 * value is a `String` (see [StringFields]) or something derived from a path, such as a RenderComponent's model.
 */
object AssetRefPicker {
    private const val CACHE_MS = 2000L

    private val listing = AssetBrowserListing()
    private val cache = HashMap<AssetFolder, Pair<Long, List<String>>>()
    private val filters = HashMap<String, ImString>()

    private fun candidates(engine: Engine, kind: AssetFolder): List<String> {
        val now = System.currentTimeMillis()
        cache[kind]?.takeIf { now - it.first < CACHE_MS }?.let { return it.second }
        val list = AssetRefs.candidates(listing, engine.assetsManager.paths.projectRoot, kind)
        cache[kind] = now to list
        return list
    }

    /** Draws the combo on the current line; [id] must be unique in the window. */
    fun combo(engine: Engine, id: String, current: String?, kind: AssetFolder, onPick: (String?) -> Unit) {
        val strings = engine.stringManager
        ImGui.setNextItemWidth(-1f)
        if (!ImGui.beginCombo("##pick.$id", strings.getString("inspector.asset_ref.pick"))) return
        val filter = filters.getOrPut(id) { ImString(64) }
        ImGui.setNextItemWidth(-1f)
        ImGui.inputTextWithHint("##filter.$id", strings.getString("inspector.asset_ref.filter"), filter)
        if (ImGui.selectable("${strings.getString("inspector.asset_ref.none")}##none.$id", current.isNullOrEmpty())) onPick(null)
        val all = candidates(engine, kind)
        if (all.isEmpty()) ImGui.textDisabled(strings.getString("inspector.asset_ref.empty", kind.dirName))
        AssetRefs.filter(all, filter.get()).forEach { path ->
            if (ImGui.selectable("$path##$id.$path", path == current)) onPick(path)
        }
        ImGui.endCombo()
    }

    /** Makes the last item a drop target for [kind] files; true and [onPick] when one is dropped. */
    fun dropTarget(kind: AssetFolder, onPick: (String) -> Unit) {
        if (!ImGui.beginDragDropTarget()) return
        for (payload in AssetRefs.DROP_PAYLOADS) {
            val path = ImGui.acceptDragDropPayload<String>(payload) ?: continue
            AssetRefs.dropValue(kind, path)?.let(onPick)
        }
        ImGui.endDragDropTarget()
    }

    /** The orange flag under a field whose [current] file is missing or of another type. */
    fun flag(engine: Engine, current: String?, kind: AssetFolder) {
        if (current.isNullOrEmpty()) return
        val strings = engine.stringManager
        val message = when {
            !AssetRefs.exists(engine.assetsManager.paths, current) -> strings.getString("inspector.asset_ref.missing", current)
            !AssetRefs.accepts(kind, current) -> strings.getString("inspector.asset_ref.wrong_kind", kind.dirName, current)
            else -> return
        }
        ImGui.pushTextWrapPos(0f)
        ImGui.textColored(1f, 0.6f, 0.2f, 1f, message)
        ImGui.popTextWrapPos()
    }
}
