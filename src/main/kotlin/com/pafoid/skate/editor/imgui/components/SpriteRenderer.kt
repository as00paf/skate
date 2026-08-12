package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.SpriteRenderer

fun SpriteRenderer.imgui(engine: Engine) {
    if (MImGui.colorPicker4(engine.stringManager.getString("component.SpriteRenderer.color"), color)) {
        color.set(color[0], color[1], color[2], color[3])//TODO: use event system
    }
    zIndex = MImGui.dragInt(engine.stringManager.getString("component.SpriteRenderer.zIndex"), zIndex)
    sprite.texture = MImGui.textureDropdown(
        engine.stringManager.getString("component.SpriteRenderer.texture"),
        sprite.texture,
        engine.assetsManager,
        engine.stringManager.getString("material.texture.select")
    )
}