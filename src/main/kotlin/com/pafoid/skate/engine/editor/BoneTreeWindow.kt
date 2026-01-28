package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.animation.Joint
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.scenes.GameObject
import imgui.ImGui
import imgui.flag.ImGuiTreeNodeFlags
import com.pafoid.skate.engine.assets.PoseSerializer
import com.pafoid.skate.engine.animation.BoneOverride
import imgui.type.ImString
import org.joml.Quaternionf
import org.joml.Vector3f

class BoneTreeWindow {
    private var activeGameObject: GameObject? = null
    private var selectedBone: Joint? = null
    private val poseFileName = ImString(128)

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

            ImGui.inputText("Pose File Name", poseFileName)
            ImGui.sameLine()
            if (ImGui.button("Save Pose")) {
                activeGameObject?.let { go ->
                    val boneOverride = go.getComponent<BoneOverride>() ?: BoneOverride().also { go.addComponent(it) }
                    PoseSerializer.savePose(boneOverride, "assets/poses/${poseFileName.get()}.json")
                }
            }
            ImGui.sameLine()
            if (ImGui.button("Load Pose")) {
                activeGameObject?.let { go ->
                    val loadedOverride = PoseSerializer.loadPose("assets/poses/${poseFileName.get()}.json")
                    loadedOverride?.let { bo ->
                        val existingOverride = go.getComponent<BoneOverride>() ?: BoneOverride().also { go.addComponent(it) }
                        // Replace existing overrides with loaded ones
                        bo.getOverrides().forEach { (boneName, rotation) ->
                            existingOverride.addOverride(boneName, rotation)
                        }
                    }
                }
            }

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

            // Allow applying manual rotation overrides to selected bone
            if (child == selectedBone) {
                activeGameObject?.getComponent<BoneOverride>()?.let { bo ->
                    var currentRotation = bo.getOverride(child.name) ?: Quaternionf()
                    val eulerAngles = currentRotation.getEulerAnglesXYZ(Vector3f())
                    
                    val rotationXYZ = floatArrayOf(
                        Math.toDegrees(eulerAngles.x.toDouble()).toFloat(),
                        Math.toDegrees(eulerAngles.y.toDouble()).toFloat(),
                        Math.toDegrees(eulerAngles.z.toDouble()).toFloat()
                    )

                    if (ImGui.dragFloat3("Rotation (Euler)", rotationXYZ, 1f, -180f, 180f)) {
                        val newRotation = Quaternionf().rotationXYZ(
                            Math.toRadians(rotationXYZ[0].toDouble()).toFloat(),
                            Math.toRadians(rotationXYZ[1].toDouble()).toFloat(),
                            Math.toRadians(rotationXYZ[2].toDouble()).toFloat()
                        )
                        bo.addOverride(child.name, newRotation)
                    }
                }
            }

            if (isNodeOpen) {
                drawJointNode(child)
                ImGui.treePop()
            }
        }
    }
}
