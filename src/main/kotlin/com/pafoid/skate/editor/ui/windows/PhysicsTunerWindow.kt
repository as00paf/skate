package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.IWindowWithScene
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.game.skateboard.SkateboardPhysics
import imgui.ImGui
import imgui.type.ImBoolean
import org.joml.Vector3f

class PhysicsTunerWindow(
    private val stringManager: StringManager,
    private val systemManager: SystemManager,
) : IWindowWithScene {

    private val gameObjectManager: GameObjectManager by lazy {
        systemManager.getSystem<GameObjectManager>() ?: throw RuntimeException("GameObjectManager not initialized")
    }

    override fun imgui(scene: Scene) {
        val physics: IPhysics3D = scene.physics3d

        ImGui.begin(stringManager.getString("window.physics_tuner"))
        
        // Gravity
        if (ImGui.collapsingHeader(stringManager.getString("lbl.physics_tuner.global_settings"))) {
            val debugPhysics = ImBoolean(physics.debugEnabled)
            if (ImGui.checkbox(stringManager.getString("lbl.physics_tuner.debug_physics"), debugPhysics)) {
                physics.debugEnabled = debugPhysics.get()
            }

            val gravity = physics.getGravity()
            val gVal = floatArrayOf(gravity.x, gravity.y, gravity.z)
            if (MImGui.dragFloat3(stringManager.getString("lbl.physics_tuner.gravity"), gVal)) {
                physics.setGravity(Vector3f(gVal[0], gVal[1], gVal[2]))
            }
        }

        val skater = gameObjectManager.getGameObject("Skater") ?: run {
            ImGui.end()
            return
        }
        val playerController = skater.getComponent<PlayerController>()

        if (playerController != null && ImGui.collapsingHeader(stringManager.getString("lbl.physics_tuner.player_controller"))) {
            val jumpImpulse = floatArrayOf(playerController.jumpImpulse)
            if (ImGui.dragFloat(stringManager.getString("lbl.physics_tuner.pop_force"), jumpImpulse, 0.1f)) {
                playerController.jumpImpulse = jumpImpulse[0]
            }
            
            val catchStrength = floatArrayOf(playerController.catchStrength)
            if (ImGui.dragFloat(stringManager.getString("lbl.physics_tuner.catch_strength"), catchStrength, 0.01f)) {
                playerController.catchStrength = catchStrength[0]
            }

            val flickSensitivity = floatArrayOf(playerController.flickSensitivity)
            if (ImGui.dragFloat(stringManager.getString("lbl.physics_tuner.flick_sensitivity"), flickSensitivity, 0.1f)) {
                playerController.flickSensitivity = flickSensitivity[0]
            }
        }

        val skate = gameObjectManager.getGameObject("Skate") ?: run {
            ImGui.end()
            return
        }
        val skateboardPhysics = skate.getComponent<SkateboardPhysics>()
        
        if (skateboardPhysics != null && ImGui.collapsingHeader(stringManager.getString("lbl.physics_tuner.suspension"))) {
            val stiffness = floatArrayOf(skateboardPhysics.stiffness)
            if (ImGui.dragFloat(stringManager.getString("lbl.physics_tuner.stiffness"), stiffness, 1f)) {
                skateboardPhysics.stiffness = stiffness[0]
            }
            
            val damping = floatArrayOf(skateboardPhysics.damping)
            if (ImGui.dragFloat(stringManager.getString("lbl.physics_tuner.damping"), damping, 0.1f)) {
                skateboardPhysics.damping = damping[0]
            }
             val restLength = floatArrayOf(skateboardPhysics.suspensionRestLength)
            if (ImGui.dragFloat(stringManager.getString("lbl.physics_tuner.rest_length"), restLength, 0.01f)) {
                skateboardPhysics.suspensionRestLength = restLength[0]
            }
        }

        ImGui.end()
    }
}