package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.animation.Joint
import com.pafoid.skate.engine.scenes.components.RenderComponent
import com.pafoid.skate.engine.scenes.GameObject
import imgui.ImGui
import imgui.flag.ImGuiTreeNodeFlags
import com.pafoid.skate.engine.assets.PoseSerializer
import com.pafoid.skate.engine.animation.BoneOverride
import com.pafoid.skate.engine.animation.BoneMirrorUtil
import com.pafoid.skate.engine.scenes.SceneManager
import imgui.type.ImString
import imgui.type.ImBoolean
import org.joml.Quaternionf
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

class BoneTreeWindow : KoinComponent {
    private val sceneManager: SceneManager by inject()
    private val poseSerializer: PoseSerializer by inject()

    private var selectedBone: Joint? = null
    private val poseFileName = ImString(128)
    private val mirrorPoseEnabled = ImBoolean(false)

    fun setSelectedBone(joint: Joint?) {
        selectedBone = joint
    }

    fun imgui() {
        sceneManager.getSelectedGameObject()?.let { go ->
            val skeleton = go.getComponent<RenderComponent>()?.model?.skeleton
            if (skeleton != null) {
                ImGui.begin("Bone Tree")

                ImGui.inputText("Pose File Name", poseFileName)
                ImGui.sameLine()
                if (ImGui.button("Save Pose")) {
                    val boneOverride = go.getComponent<BoneOverride>() ?: BoneOverride().also { go.addComponent(it) }
                    poseSerializer.savePose(boneOverride, "assets/poses/${poseFileName.get()}.json")
                }
                ImGui.sameLine()
                if (ImGui.button("Load Pose")) {
                    val loadedOverride = poseSerializer.loadPose("assets/poses/${poseFileName.get()}.json")
                    loadedOverride?.let { bo ->
                        val existingOverride =
                            go.getComponent<BoneOverride>() ?: BoneOverride().also { go.addComponent(it) }
                        // Replace existing overrides with loaded ones
                        bo.getOverrides().forEach { (boneName, rotation) ->
                            existingOverride.addOverride(boneName, rotation)
                        }
                    }
                }

                ImGui.checkbox("Mirror Pose", mirrorPoseEnabled)

                if (ImGui.treeNodeEx(
                        skeleton.rootJoint.name,
                        ImGuiTreeNodeFlags.DefaultOpen or ImGuiTreeNodeFlags.FramePadding
                    )
                ) {
                    drawJointNode(go, skeleton.rootJoint)
                    ImGui.treePop()
                }
                ImGui.end()
            }
        }
    }

    private fun drawJointNode(gameObject: GameObject, joint: Joint) {
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
                gameObject.getComponent<BoneOverride>()?.let { bo ->
                    val currentRotation = bo.getOverride(child.name) ?: Quaternionf()
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

                        // Apply to mirrored bone if enabled
                        if (mirrorPoseEnabled.get()) {
                            val mirroredBoneName = BoneMirrorUtil.getMirroredBoneName(child.name)
                            if (mirroredBoneName != child.name) {
                                // Negate X and Z Euler angles for mirrored rotation
                                val mirroredRotation = Quaternionf().rotationXYZ(
                                    Math.toRadians(-rotationXYZ[0].toDouble()).toFloat(),
                                    Math.toRadians(rotationXYZ[1].toDouble()).toFloat(),
                                    Math.toRadians(-rotationXYZ[2].toDouble()).toFloat()
                                )
                                bo.addOverride(mirroredBoneName, mirroredRotation)
                            }
                        }
                    }
                }
            }

            if (isNodeOpen) {
                drawJointNode(gameObject, child)
                ImGui.treePop()
            }
        }
    }
}

