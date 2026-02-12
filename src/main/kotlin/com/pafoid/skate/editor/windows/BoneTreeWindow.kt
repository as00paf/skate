package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.assets.data.models.CharacterModel
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.BoneMirrorUtil
import com.pafoid.skate.engine.assets.data.models.animations.BoneOverride
import com.pafoid.skate.engine.assets.serialization.PoseSerializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.scene.getSelectedGameObject
import com.pafoid.skate.engine.ecs.systems.SceneManager
import imgui.ImGui
import imgui.flag.ImGuiTreeNodeFlags
import imgui.type.ImBoolean
import imgui.type.ImString
import org.joml.Quaternionf
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BoneTreeWindow : KoinComponent {
    private val sceneManager: SceneManager by inject()
    private val poseSerializer: PoseSerializer by inject()
    private val stringManager: StringManager by inject()

    private var selectedBone: Bone? = null
    private val poseFileName = ImString(128)
    private val mirrorPoseEnabled = ImBoolean(false)

    fun setSelectedBone(bone: Bone?) {
        selectedBone = bone
    }

    fun imgui() {
        sceneManager.currentScene?.getSelectedGameObject()?.let { go ->
            val skeleton = (go.getComponent<RenderComponent>()?.model as? CharacterModel)?.skeleton
            if (skeleton != null) {
                ImGui.begin(stringManager.getString("window.bonetree"))

                ImGui.inputText(stringManager.getString("lbl.bone_tree.pose_file_name"), poseFileName)
                ImGui.sameLine()
                if (ImGui.button(stringManager.getString("btn.save_pose"))) {
                    val boneOverride = go.getComponent<BoneOverride>() ?: BoneOverride().also { go.addComponent(it) }
                    poseSerializer.savePose(boneOverride, "assets/poses/${poseFileName.get()}.json")
                }
                ImGui.sameLine()
                if (ImGui.button(stringManager.getString("btn.load_pose"))) {
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

                ImGui.checkbox(stringManager.getString("lbl.bone_tree.mirror_pose"), mirrorPoseEnabled)

                if (ImGui.treeNodeEx(
                        skeleton.rootBone.name,
                        ImGuiTreeNodeFlags.DefaultOpen or ImGuiTreeNodeFlags.FramePadding
                    )
                ) {
                    drawBoneNode(go, skeleton.rootBone)
                    ImGui.treePop()
                }
                ImGui.end()
            }
        }
    }

    private fun drawBoneNode(gameObject: GameObject, bone: Bone) {
        bone.children.forEach { child ->
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

                    if (ImGui.dragFloat3(stringManager.getString("lbl.bone_tree.rotation_euler"), rotationXYZ, 1f, -180f, 180f)) {
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
                drawBoneNode(gameObject, child)
                ImGui.treePop()
            }
        }
    }
}

