package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.animation.Joint
import com.pafoid.skate.engine.animation.Skeleton
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.scenes.GameObject
import imgui.ImGui
import imgui.flag.ImGuiTreeNodeFlags

class BoneTreeWindow {
    private var activeGameObject: GameObject? = null

    fun setActiveObject(go: GameObject?) {
        activeGameObject = go
    }

    fun imgui() {
        val skeleton = activeGameObject?.getComponent<Entity>()?.model?.skeleton
        if (skeleton != null) {
            ImGui.begin("Bone Tree")
            if (ImGui.treeNodeEx(skeleton.rootJoint.name, ImGuiTreeNodeFlags.DefaultOpen or ImGuiTreeNodeFlags.FramePadding)) {
                drawJointNode(skeleton.rootJoint)
                ImGui.treePop()
            }
            ImGui.end()
        }
    }

    private fun drawJointNode(joint: Joint) {
        joint.children.forEach { child ->
            val flags = if (child.children.isEmpty()) {
                ImGuiTreeNodeFlags.Leaf or ImGuiTreeNodeFlags.Bullet
            } else {
                ImGuiTreeNodeFlags.DefaultOpen or ImGuiTreeNodeFlags.FramePadding
            }
            val isNodeOpen = ImGui.treeNodeEx(child.name, flags)

            if (isNodeOpen) {
                drawJointNode(child)
                ImGui.treePop()
            }
        }
    }
}
