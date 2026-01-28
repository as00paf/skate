package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.animation.Joint
import com.pafoid.skate.engine.animation.Skeleton
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.scenes.GameObject
import imgui.ImGui
import imgui.flag.ImGuiTreeNodeFlags

class BoneTreeWindow {
    private var activeGameObject: GameObject? = null
    private var selectedBone: Joint? = null

    fun setActiveObject(go: GameObject?) {
        activeGameObject = go
        if (go == null) {
            selectedBone = null
        }
    }

    fun setSelectedBone(joint: Joint?) {
        selectedBone = joint
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
            var flags = if (child.children.isEmpty()) {
                ImGuiTreeNodeFlags.Leaf or ImGuiTreeNodeFlags.Bullet
            } else {
                ImGuiTreeNodeFlags.DefaultOpen or ImGuiTreeNodeFlags.FramePadding
            }
            if (child == selectedBone) {
                flags = flags or ImGuiTreeNodeFlags.Selected
            }
            val isNodeOpen = ImGui.treeNodeEx(child.name, flags)

            if (ImGui.isItemClicked()) {
                selectedBone = child
            }

            if (isNodeOpen) {
                drawJointNode(child)
                ImGui.treePop()
            }
        }
    }
}
