package com.pafoid.skate.editor.windows

import com.pafoid.skate.engine.ecs.scene.getSelectedGameObject
import com.pafoid.skate.engine.ecs.systems.SceneManager
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.CylinderCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.utils.StringManager
import imgui.ImGui
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PropertiesWindow: KoinComponent {
    private val sceneManager: SceneManager by inject()
    private val stringManager: StringManager by inject()

    fun imgui() {
        sceneManager.currentScene?.getSelectedGameObject()?.let { go ->
            ImGui.begin(stringManager.getString("window.properties"))
            ImGui.text("${stringManager.getString("lbl.name")}: ${go.name}")

            if (ImGui.beginPopupContextWindow("ComponentAdder")) {
                if (ImGui.menuItem(stringManager.getString("menu.component.add_rigidbody"))) {
                    if (go.getComponent<RigidBody3D>() == null) {
                        go.addComponent(RigidBody3D())
                    }
                }
                
                if (ImGui.menuItem(stringManager.getString("menu.component.add_box_collider"))) {
                    go.addComponent(BoxCollider3D())
                }

                if (ImGui.menuItem(stringManager.getString("menu.component.add_cylinder_collider"))) {
                    go.addComponent(CylinderCollider3D())
                }
                ImGui.endPopup()
            }

            go.imgui()
            ImGui.end()
        }
    }
}