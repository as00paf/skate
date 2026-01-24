package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.CylinderCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.scenes.GameObject
import imgui.ImGui

class PropertiesWindow {
    private var activeGameObject: GameObject? = null

    fun imgui() {
        activeGameObject?.let { go ->
            ImGui.begin("Properties")

            if (ImGui.beginPopupContextWindow("ComponentAdder")) {
                if (ImGui.menuItem("Add RigidBody3D")) {
                    if (go.getComponent<RigidBody3D>() == null) {
                        go.addComponent(RigidBody3D())
                    }
                }
                
                if (ImGui.menuItem("Add BoxCollider3D")) {
                    go.addComponent(BoxCollider3D())
                }

                if (ImGui.menuItem("Add CylinderCollider3D")) {
                    go.addComponent(CylinderCollider3D())
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