package com.pafoid.skate.editor.windows

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.scene.getGameObject
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.utils.StringManager
import com.pafoid.skate.game.player.PlayerController
import com.pafoid.skate.game.prefabs.Skateboard
import com.pafoid.skate.game.skateboard.SkateboardPhysics
import imgui.ImGui
import imgui.type.ImBoolean
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PhysicsTunerWindow : KoinComponent {
    private val stringManager: StringManager by inject()
    
    fun imgui(currentScene: Scene) {
        val skate = currentScene.getGameObject("Skateboard") as? Skateboard ?: return
        val physics: IPhysics3D = currentScene.physics3d

        val playerController = skate.getComponent<PlayerController>()
        val skateboardPhysics = skate.getComponent<SkateboardPhysics>()
        val rb= skate.getComponent<RigidBody3D>()

        ImGui.begin(stringManager.getString("window.physics_tuner"))
        
        // Gravity
        if (ImGui.collapsingHeader(stringManager.getString("lbl.physics_tuner.global_settings"))) {
            val debugPhysics = ImBoolean(physics.debugEnabled)
            if (ImGui.checkbox(stringManager.getString("lbl.physics_tuner.debug_physics"), debugPhysics)) {
                physics.debugEnabled = debugPhysics.get()
            }

            val gravity = physics.getGravity()
            val gVal = floatArrayOf(gravity.x, gravity.y, gravity.z)
            if (ImGui.dragFloat3(stringManager.getString("lbl.physics_tuner.gravity"), gVal)) {
                physics.setGravity(Vector3f(gVal[0], gVal[1], gVal[2]))
            }
        }
        
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
        
        if (rb != null && ImGui.collapsingHeader(stringManager.getString("lbl.physics_tuner.board_rigidbody"))) {
            val friction = floatArrayOf(rb.friction)
            if (ImGui.dragFloat(stringManager.getString("lbl.physics_tuner.friction"), friction, 0.01f, 0f, 1f)) {
                rb.friction = friction[0]
                rb.rawBody?.setFriction(friction[0])
            }
            
            val mass = floatArrayOf(rb.mass)
            if (ImGui.dragFloat(stringManager.getString("lbl.physics_tuner.mass"), mass, 0.1f)) {
                rb.mass = mass[0]
                rb.rawBody?.setMass(mass[0])
            }
        }

        ImGui.end()
    }
}