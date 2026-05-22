package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.game.skateboard.SkateboardPhysics
import com.pafoid.skate.game.trick.TrickManager
import imgui.ImGui
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.abs

@Serializable
class TrickAnalyzer : Component(), KoinComponent {
    @Transient private val stringManager: StringManager by inject()
    @Transient private val trickManager: TrickManager by inject()

    private var physics: SkateboardPhysics? = null
    private var isAirborne = false

    @Transient private val totalRotation = Vector3f()
    @Transient private val lastRotation = Vector3f()
    
    var lastTrickName = ""
    @Transient var currentAirRotation = Vector3f()

    override fun init(gameObject: GameObject) {
        super.init(gameObject)
        physics = gameObject.getComponent<SkateboardPhysics>()
        lastRotation.set(gameObject.getComponent<Transform>()?.rotation)
    }

    override fun update(dt: Float) {
        val phys = physics ?: return

        val transform = gameObject.getComponent<Transform>() ?: return
        val currentRot = transform.rotation
        
        var dx = currentRot.x - lastRotation.x
        var dy = currentRot.y - lastRotation.y
        var dz = currentRot.z - lastRotation.z
        
        if (dx > 180) dx -= 360
        if (dx < -180) dx += 360
        if (dy > 180) dy -= 360
        if (dy < -180) dy += 360
        if (dz > 180) dz -= 360
        if (dz < -180) dz += 360
        
        if (!phys.isGrounded) {
            if (!isAirborne) {
                isAirborne = true
                totalRotation.set(0f, 0f, 0f)
            }
            
            totalRotation.add(dx, dy, dz)
            currentAirRotation.set(totalRotation)
        } else {
            if (isAirborne) {
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
        val absYaw = abs(yaw)
        if (absYaw > 90) {
            val spin = ((absYaw + 90) / 180).toInt() * 180
            if (yaw > 0) {
                trickParts.add(String.format(trickManager.getTrickName("trick.bs"), spin))
            } else {
                trickParts.add(String.format(trickManager.getTrickName("trick.fs"), spin))
            }
        }
        
        // Roll (Flip Tricks)
        val absRoll = abs(roll)
        if (absRoll > 180) { // Full flip
             val flips = ((absRoll + 90) / 360).toInt()
             if (flips > 0) {
                 if (roll > 0) {
                     trickParts.add(trickManager.getTrickName("trick.heelflip"))
                 } else {
                     trickParts.add(trickManager.getTrickName("trick.kickflip"))
                 }
                 if (flips > 1) trickParts.add("x$flips")
             }
        }
        
        // Pitch (Backflip/Frontflip)
         val absPitch = abs(pitch)
        if (absPitch > 180) {
            if (pitch > 0) {
                trickParts.add(trickManager.getTrickName("trick.backflip"))
            } else {
                trickParts.add(trickManager.getTrickName("trick.frontflip"))
            }
        }
        
        if (trickParts.isEmpty()) {
            lastTrickName = trickManager.getTrickName("trick.ollie")
        } else {
            lastTrickName = trickParts.joinToString(" + ")
        }
    }

    override fun imgui() {
        ImGui.text(stringManager.getString("lbl.trick.current", lastTrickName))
        if (isAirborne) {
            ImGui.text(
                "Rotation: %.1f, %.1f, %.1f".format(
                    currentAirRotation.x,
                    currentAirRotation.y,
                    currentAirRotation.z
                )
            )
        }
    }
}
