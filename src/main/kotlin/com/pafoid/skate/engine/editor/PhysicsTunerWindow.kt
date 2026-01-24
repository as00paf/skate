package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.physics3d.Physics3D
import com.pafoid.skate.engine.scenes.components.PlayerController
import com.pafoid.skate.engine.scenes.components.SkateboardPhysics
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import imgui.ImGui
import imgui.type.ImBoolean
import org.joml.Vector3f

class PhysicsTunerWindow {
    
    fun imgui(physics: Physics3D, playerController: PlayerController?, skateboardPhysics: SkateboardPhysics?, rb: RigidBody3D?) {
        ImGui.begin("Physics Tuner")
        
        // Gravity
        if (ImGui.collapsingHeader("Global Settings")) {
            val gravity = physics.getGravity()
            val gVal = floatArrayOf(gravity.x, gravity.y, gravity.z)
            if (ImGui.dragFloat3("Gravity", gVal)) {
                physics.setGravity(Vector3f(gVal[0], gVal[1], gVal[2]))
            }
        }
        
        if (playerController != null && ImGui.collapsingHeader("Player Controller")) {
            val jumpImpulse = floatArrayOf(playerController.jumpImpulse)
            if (ImGui.dragFloat("Pop Force", jumpImpulse, 0.1f)) {
                playerController.jumpImpulse = jumpImpulse[0]
            }
            
            val catchStrength = floatArrayOf(playerController.catchStrength)
            if (ImGui.dragFloat("Catch Strength", catchStrength, 0.01f)) {
                playerController.catchStrength = catchStrength[0]
            }

            val flickSensitivity = floatArrayOf(playerController.flickSensitivity)
            if (ImGui.dragFloat("Flick Sensitivity", flickSensitivity, 0.1f)) {
                playerController.flickSensitivity = flickSensitivity[0]
            }
        }
        
        if (skateboardPhysics != null && ImGui.collapsingHeader("Suspension")) {
            val stiffness = floatArrayOf(skateboardPhysics.stiffness)
            if (ImGui.dragFloat("Stiffness", stiffness, 1f)) {
                skateboardPhysics.stiffness = stiffness[0]
            }
            
            val damping = floatArrayOf(skateboardPhysics.damping)
            if (ImGui.dragFloat("Damping", damping, 0.1f)) {
                skateboardPhysics.damping = damping[0]
            }
             val restLength = floatArrayOf(skateboardPhysics.suspensionRestLength)
            if (ImGui.dragFloat("Rest Length", restLength, 0.01f)) {
                skateboardPhysics.suspensionRestLength = restLength[0]
            }
        }
        
        if (rb != null && ImGui.collapsingHeader("Board Rigidbody")) {
            val friction = floatArrayOf(rb.friction)
            if (ImGui.dragFloat("Friction", friction, 0.01f, 0f, 1f)) {
                rb.friction = friction[0]
                rb.rawBody?.setFriction(friction[0])
            }
            
            val mass = floatArrayOf(rb.mass)
            if (ImGui.dragFloat("Mass", mass, 0.1f)) {
                rb.mass = mass[0]
                rb.rawBody?.setMass(mass[0])
            }
        }

        ImGui.end()
    }
}