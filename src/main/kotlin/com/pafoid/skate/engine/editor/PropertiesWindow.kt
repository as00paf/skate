package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.CylinderCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.SceneManager
import imgui.ImGui
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PropertiesWindow: KoinComponent {
    private val sceneManager: SceneManager by inject()

    fun imgui() {
        sceneManager.getSelectedGameObject()?.let { go ->
            ImGui.begin("Properties")
            ImGui.text("Name: ${go.name}")

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
}