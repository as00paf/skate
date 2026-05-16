package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.data.PrefabData
import com.pafoid.skate.editor.ui.windows.assetBrowser.PrefabType
import com.pafoid.skate.editor.ui.windows.viewport.ViewportRenderer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.editor.events.ViewportDropAnimation
import com.pafoid.skate.editor.events.ViewportDropSound
import com.pafoid.skate.editor.events.ViewportDropTexture
import com.pafoid.skate.editor.events.ViewportSpawnPrefab
import imgui.ImGui
import imgui.ImVec2
import org.joml.Vector3f
import kotlin.math.abs

/**
 * Handles drag-drop operations in the viewport.
 * Publishes ViewportAction events for prefab spawning and asset drops.
 */
class ViewportDragDropHandler(
    private val viewportRenderer: ViewportRenderer,
    private val eventSystem: EventSystem,
) {

    // Reusable buffers
    private val tempMousePos = ImVec2()

    fun renderDragDropTarget(scene: Scene?) {
        if (scene == null) return

        ImGui.setCursorPos(viewportRenderer.imageScreenPosX, viewportRenderer.imageScreenPosY + 40f)
        if (ImGui.beginDragDropTarget()) {
            // Accept prefab payloads
            val payloadLedge = ImGui.acceptDragDropPayload<PrefabData>("PREFAB_LEDGE")
            val payloadRail = ImGui.acceptDragDropPayload<PrefabData>("PREFAB_RAIL")
            val payloadKicker = ImGui.acceptDragDropPayload<PrefabData>("PREFAB_KICKER")
            val payloadManualPad = ImGui.acceptDragDropPayload<PrefabData>("PREFAB_MANUAL_PAD")
            val payloadBank = ImGui.acceptDragDropPayload<PrefabData>("PREFAB_BANK")
            val payloadQuarterPipe = ImGui.acceptDragDropPayload<PrefabData>("PREFAB_QUARTER_PIPE")
            val payloadSkateboard = ImGui.acceptDragDropPayload<PrefabData>("PREFAB_SKATEBOARD")

            val payloadTexture = ImGui.acceptDragDropPayload<String>("TEXTURE")
            val payloadSound = ImGui.acceptDragDropPayload<String>("SOUND")
            val payloadAnimation = ImGui.acceptDragDropPayload<String>("ANIMATION")

            val prefabPayload = payloadRail ?: payloadLedge ?: payloadKicker ?: payloadManualPad ?: payloadBank ?: payloadQuarterPipe ?: payloadSkateboard

            if (prefabPayload != null) {
                ImGui.getMousePos(tempMousePos)
                val hitPoint = computeDropPosition(scene, tempMousePos.x, tempMousePos.y)

                if (hitPoint != null) {
                    val prefabType = when {
                        payloadRail != null -> PrefabType.RAIL
                        payloadLedge != null -> PrefabType.LEDGE
                        payloadKicker != null -> PrefabType.KICKER
                        payloadManualPad != null -> PrefabType.MANUAL_PAD
                        payloadBank != null -> PrefabType.BANK
                        payloadQuarterPipe != null -> PrefabType.QUARTER_PIPE
                        payloadSkateboard != null -> PrefabType.SKATEBOARD
                        else -> null
                    }
                    prefabType?.let {
                        eventSystem.publish(ViewportSpawnPrefab(it, hitPoint))
                    }
                }
            }

            if (payloadTexture != null) {
                val hoveredObject = scene.hoveredGameObject
                val dropPosition = computeDropPosition(scene, tempMousePos.x, tempMousePos.y)
                eventSystem.publish(ViewportDropTexture(payloadTexture, hoveredObject, dropPosition))
            }

            if (payloadSound != null) {
                val hoveredObject = scene.hoveredGameObject
                if (hoveredObject != null) {
                    eventSystem.publish(ViewportDropSound(payloadSound, hoveredObject))
                }
            }

            if (payloadAnimation != null) {
                val hoveredObject = scene.hoveredGameObject
                if (hoveredObject != null) {
                    eventSystem.publish(ViewportDropAnimation(payloadAnimation, hoveredObject))
                }
            }

            ImGui.endDragDropTarget()
        }
    }

    private fun computeDropPosition(scene: Scene, mouseX: Float, mouseY: Float): Vector3f? {
        val relX = mouseX - viewportRenderer.imageScreenPosX
        val relY = mouseY - viewportRenderer.imageScreenPosY
        val ray = scene.camera.screenToRay(relX, relY, viewportRenderer.imageSizeX, viewportRenderer.imageSizeY)

        if (abs(ray.direction.y) > 0.0001f) {
            val t = -ray.origin.y / ray.direction.y
            if (t > 0) {
                return Vector3f(ray.direction).mul(t).add(ray.origin)
            }
        }
        return null
    }
}
