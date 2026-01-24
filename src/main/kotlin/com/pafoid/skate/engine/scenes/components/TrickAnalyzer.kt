package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.scenes.components.SkateboardPhysics
import org.joml.Vector3f
import kotlin.math.abs

class TrickAnalyzer : Component() {
    
    private var physics: SkateboardPhysics? = null
    private var isAirborne = false
    
    private val totalRotation = Vector3f()
    private val lastRotation = Vector3f()
    
    var lastTrickName = ""
    var currentAirRotation = Vector3f()

    override fun start() {
        physics = gameObject.getComponent<SkateboardPhysics>()
        lastRotation.set(gameObject.transform.rotation)
    }

    override fun update(dt: Float) {
        val phys = physics ?: return
        
        val currentRot = gameObject.transform.rotation
        
        // Calculate delta (handling wrap-around if necessary, but Transform.rotation usually just accumulates or wraps)
        // If Transform.rotation wraps at 360, we need to handle it. 
        // Assuming simple accumulation for now or checking small deltas.
        
        var dx = currentRot.x - lastRotation.x
        var dy = currentRot.y - lastRotation.y
        var dz = currentRot.z - lastRotation.z
        
        // Simple wrap correction assuming we don't rotate more than 180 per frame
        if (dx > 180) dx -= 360
        if (dx < -180) dx += 360
        if (dy > 180) dy -= 360
        if (dy < -180) dy += 360
        if (dz > 180) dz -= 360
        if (dz < -180) dz += 360
        
        if (!phys.isGrounded) {
            if (!isAirborne) {
                // Takeoff
                isAirborne = true
                totalRotation.set(0f, 0f, 0f)
            }
            
            totalRotation.add(dx, dy, dz)
            currentAirRotation.set(totalRotation)
        } else {
            if (isAirborne) {
                // Landing
                isAirborne = false
                analyzeTrick()
            }
        }
        
        lastRotation.set(currentRot)
    }

    private fun analyzeTrick() {
        val pitch = totalRotation.x
        val yaw = totalRotation.y
        val roll = totalRotation.z
        
        val trickParts = mutableListOf<String>()
        
        // Yaw (Spins)
        // 180, 360, 540...
        // 180 is approx 180. Threshold: +/- 45 deg?
        val absYaw = abs(yaw)
        if (absYaw > 90) {
            val spin = ((absYaw + 90) / 180).toInt() * 180
            if (yaw > 0) trickParts.add("BS $spin") else trickParts.add("FS $spin")
        }
        
        // Roll (Flip Tricks)
        // Kickflip (negative roll?), Heelflip (positive roll?)
        // Standard goofy/regular logic applies but let's simplify.
        // Left/Right flips.
        val absRoll = abs(roll)
        if (absRoll > 180) { // Full flip
             val flips = ((absRoll + 90) / 360).toInt()
             if (flips > 0) {
                 if (roll > 0) trickParts.add("Heelflip") else trickParts.add("Kickflip")
                 if (flips > 1) trickParts.add("x$flips")
             }
        }
        
        // Pitch (Backflip/Frontflip)
        // Usually simpler names like "Backflip"
         val absPitch = abs(pitch)
        if (absPitch > 180) {
            if (pitch > 0) trickParts.add("Backflip") else trickParts.add("Frontflip")
        }
        
        if (trickParts.isEmpty()) {
            lastTrickName = "Ollie"
        } else {
            lastTrickName = trickParts.joinToString(" + ")
        }
    }

    override fun imgui() {
        imgui.ImGui.text("Current Trick: $lastTrickName")
        if (isAirborne) {
            imgui.ImGui.text("Rotation: %.1f, %.1f, %.1f".format(currentAirRotation.x, currentAirRotation.y, currentAirRotation.z))
        }
    }
}
