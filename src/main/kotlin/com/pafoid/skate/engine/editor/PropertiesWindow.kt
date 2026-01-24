package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.physics2d.components.Box2DCollider
import com.pafoid.skate.engine.physics2d.components.RigidBody2D
import com.pafoid.skate.engine.scenes.GameObject
import imgui.ImGui

class PropertiesWindow {
    private var activeGameObject: GameObject? = null

    fun imgui() {
        activeGameObject?.let { go ->
            ImGui.begin("Properties")

            if (ImGui.beginPopupContextWindow("ComponentAdder")) {
                if (ImGui.menuItem("Add Rigidbody2D")) {
                    if (go.getComponent<RigidBody2D>() == null) {
                        go.addComponent(RigidBody2D())
                    }
                }

                if (ImGui.menuItem("Add Box2D Collider")) {
                    if (go.getComponent<Box2DCollider>() == null) {
                        go.addComponent(Box2DCollider())
                    }
                }

                ImGui.endPopup()
            }

            go.imgui()
            ImGui.end()
        }
    }

    fun getActiveObject(): GameObject? = activeGameObject
    fun setActiveObject(go: GameObject?) {
        activeGameObject = go
    }
}