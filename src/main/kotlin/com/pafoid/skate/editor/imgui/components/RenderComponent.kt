package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.commands.objects.MaterialState
import com.pafoid.skate.editor.events.MaterialAction
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.AlphaMode
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.RenderComponent
import imgui.ImGui
import imgui.flag.ImGuiColorEditFlags
import imgui.type.ImInt
import org.joml.Vector3f
import org.joml.Vector4f
import java.io.File

// TODO: use event system/commands to support undo/redo
fun RenderComponent.imgui(engine: Engine) {
    textureScale = MImGui.dragFloat(
        engine.stringManager.getString("component.RenderComponent.textureScale"),
        textureScale
    )

    renderMode = MImGui.enumDropdown(engine.stringManager.getString("component.RenderComponent.renderMode"), renderMode)

    val castShadowLabel = engine.stringManager.getString("component.RenderComponent.castShadow")
    if (ImGui.checkbox(castShadowLabel, castShadow)) {
        castShadow = !castShadow
    }

    val receiveShadowLabel = engine.stringManager.getString("component.RenderComponent.receiveShadow")
    if (ImGui.checkbox(receiveShadowLabel, receiveShadow)) {
        receiveShadow = !receiveShadow
    }

    materialInspector(engine)
}

/** Shared by every material inspector so a gesture survives redraws; see [MaterialEditSession]. */
private val materialSession = MaterialEditSession()

/**
 * Material section (EDT-17): edits the component's material override live, one undo step per gesture, and links it
 * to a `.skmat` asset (save as / save / load / unlink). Without an override it shows the model's own material
 * read-only and offers to create one or load an asset.
 */
private fun RenderComponent.materialInspector(engine: Engine) {
    val strings = engine.stringManager
    fun s(key: String) = strings.getString("material.$key")
    fun publish(action: MaterialAction) = engine.eventSystem.publish(action)

    ImGui.pushID("material_${System.identityHashCode(this)}")
    ImGui.separator()
    if (!ImGui.collapsingHeader(strings.label("material.section"), imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
        ImGui.popID()
        return
    }

    val mat = material
    if (mat == null) {
        ImGui.textDisabled(s("override.none"))
        if (ImGui.button("${s("override.create")}##override_create")) publish(MaterialAction.CreateOverride(this))
        tooltip(s("override.create.tooltip"))
        ImGui.sameLine()
        if (ImGui.button("${s("asset.load")}##asset_load")) publish(MaterialAction.Load(this))
        tooltip(s("asset.load.tooltip"))
        ImGui.popID()
        return
    }

    // Asset row
    ImGui.text("${s("asset")}: ${materialPath?.let { File(it).name } ?: s("asset.none")}")
    materialPath?.let { tooltip(it) }
    if (materialPath != null) {
        if (ImGui.button("${s("asset.save")}##asset_save")) publish(MaterialAction.Save(this))
        tooltip(s("asset.save.tooltip"))
        ImGui.sameLine()
    }
    if (ImGui.button("${s("asset.saveAs")}##asset_save_as")) publish(MaterialAction.SaveAs(this))
    tooltip(s("asset.saveAs.tooltip"))
    ImGui.sameLine()
    if (ImGui.button("${s("asset.load")}##asset_load")) publish(MaterialAction.Load(this))
    tooltip(s("asset.load.tooltip"))
    if (materialPath != null) {
        ImGui.sameLine()
        if (ImGui.button("${s("asset.unlink")}##asset_unlink")) publish(MaterialAction.Unlink(this))
        tooltip(s("asset.unlink.tooltip"))
    }
    ImGui.sameLine()
    if (ImGui.button("${s("override.remove")}##override_remove")) publish(MaterialAction.RemoveOverride(this))
    tooltip(s("override.remove.tooltip"))

    materialFields(mat, engine, ::s)

    val target = gameObject?.name
    materialSession.settle(ImGui.isAnyItemActive()).forEach { edit ->
        publish(MaterialAction.Edit(edit.material, edit.before, edit.after, target))
    }
    ImGui.popID()
}

private fun materialFields(mat: Material, engine: Engine, s: (String) -> String) {
    var d = materialSession.current(mat)
    fun preview(next: MaterialState) {
        d = next
        materialSession.preview(mat, next)
    }

    row("baseColor", s("baseColor")) {
        val c = floatArrayOf(d.baseColor.x, d.baseColor.y, d.baseColor.z, d.baseColor.w)
        if (ImGui.colorEdit4("##value", c)) preview(d.copy(baseColor = Vector4f(c[0], c[1], c[2], c[3])))
    }
    row("baseColorFactor", s("baseColorFactor")) {
        val v = floatArrayOf(d.baseColorFactor)
        if (ImGui.dragFloat("##value", v, 0.01f, 0f, 10f)) preview(d.copy(baseColorFactor = v[0]))
    }
    row("metallic", s("metallicFactor")) {
        val v = floatArrayOf(d.metallic)
        if (ImGui.sliderFloat("##value", v, 0f, 1f)) preview(d.copy(metallic = v[0]))
    }
    row("roughness", s("roughnessFactor")) {
        val v = floatArrayOf(d.roughness)
        if (ImGui.sliderFloat("##value", v, 0f, 1f)) preview(d.copy(roughness = v[0]))
    }
    row("emissive", s("emissiveFactor")) {
        // HDR: emissive above 1 feeds bloom
        val e = floatArrayOf(d.emissive.x, d.emissive.y, d.emissive.z)
        val flags = ImGuiColorEditFlags.HDR or ImGuiColorEditFlags.Float
        if (ImGui.colorEdit3("##value", e, flags)) preview(d.copy(emissive = Vector3f(e[0], e[1], e[2])))
    }
    row("alphaMode", s("alphaMode")) {
        val names = AlphaMode.entries.map { it.name }.toTypedArray()
        val index = ImInt(d.alphaMode.ordinal)
        if (ImGui.combo("##value", index, names, names.size)) preview(d.copy(alphaMode = AlphaMode.entries[index.get()]))
    }
    if (d.alphaMode == AlphaMode.MASK) {
        row("alphaCutoff", s("alphaCutoff")) {
            val v = floatArrayOf(d.alphaCutoff)
            if (ImGui.sliderFloat("##value", v, 0f, 1f)) preview(d.copy(alphaCutoff = v[0]))
        }
    }
    row("doubleSided", s("doubleSided")) {
        if (ImGui.checkbox("##value", d.doubleSided)) preview(d.copy(doubleSided = !d.doubleSided))
    }

    val none = s("texture.select")
    fun texture(id: String, label: String, value: Texture?, set: (Texture?) -> MaterialState) {
        ImGui.pushID(id)
        val picked = MImGui.textureDropdown(label, value, engine.assetsManager, none)
        ImGui.popID()
        if (picked !== value) preview(set(picked))
    }
    texture("tex_base", s("baseColorTexture"), d.baseColorTexture) { d.copy(baseColorTexture = it) }
    texture("tex_normal", s("normalTexture"), d.normalMap) { d.copy(normalMap = it) }
    texture("tex_mr", s("metallicRoughnessTexture"), d.metallicRoughnessTexture) { d.copy(metallicRoughnessTexture = it) }
    texture("tex_ao", s("aoTexture"), d.aoTexture) { d.copy(aoTexture = it) }
    texture("tex_emissive", s("emissiveTexture"), d.emissiveTexture) { d.copy(emissiveTexture = it) }
}

/** Label | control row with an ID that does not depend on the (translated) label. */
private inline fun row(id: String, label: String, control: () -> Unit) {
    ImGui.pushID(id)
    ImGui.columns(2, "##cols", false)
    ImGui.setColumnWidth(0, MImGui.DEFAULT_COLUMN_WIDTH)
    ImGui.text(label)
    ImGui.nextColumn()
    ImGui.pushItemWidth(-1f)
    control()
    ImGui.popItemWidth()
    ImGui.columns(1)
    ImGui.popID()
}

private fun tooltip(text: String) {
    if (ImGui.isItemHovered()) ImGui.setTooltip(text)
}
