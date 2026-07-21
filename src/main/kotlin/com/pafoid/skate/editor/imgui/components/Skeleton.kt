package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.BoneOverride
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.getComponent
import imgui.ImGui
import imgui.flag.ImGuiTreeNodeFlags
import org.joml.Quaternionf
import org.joml.Vector3f

fun SkeletonComponent.imgui(stringManager: StringManager, logger: LoggerService) {
    val skeleton = pose.skeleton
    val go = gameObject
    //if (go != sceneManager.currentScene?.selectedGameObject) return

    if (ImGui.button(stringManager.getString("btn.save_pose"))) {
        val boneOverride = go.getComponent<BoneOverride>() ?: BoneOverride().also { go.addComponent(it) }
        // TODO: use event system
        //poseSerializer.savePose(boneOverride, "assets/poses/${poseFileName.get()}.json")
    }
    ImGui.sameLine()
    if (ImGui.button(stringManager.getString("btn.load_pose"))) {
        // TODO: use event system
        /* val loadedOverride = poseSerializer.loadPose("assets/poses/${poseFileName.get()}.json")
         loadedOverride?.let { bo ->
             val existingOverride =
                 go.getComponent<BoneOverride>() ?: BoneOverride().also { go.addComponent(it) }
             // Replace existing overrides with loaded ones
             bo.getOverrides().forEach { (boneName, rotation) ->
                 existingOverride.addOverride(boneName, rotation)
             }
         }*/
    }

    if (ImGui.treeNodeEx(
            skeleton.rootBone.name,
            ImGuiTreeNodeFlags.DefaultOpen or ImGuiTreeNodeFlags.FramePadding
        )
    ) {
        drawBoneNode(stringManager, go, skeleton.rootBone)
        ImGui.treePop()
    }
}

fun SkeletonComponent.drawBoneNode(stringManager: StringManager, gameObject: GameObject, bone: Bone) {
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

                if (ImGui.dragFloat3(
                        stringManager.getString("lbl.bone_tree.rotation_euler"),
                        rotationXYZ,
                        1f,
                        -180f,
                        180f
                    )
                ) {
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
            drawBoneNode(stringManager, gameObject, child)
            ImGui.treePop()
        }
    }
}