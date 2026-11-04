package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.data.PrefabData
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.gizmos.EditorCamera
import com.pafoid.skate.editor.ui.windows.viewport.ViewportRenderer
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.input.InputProvider
import imgui.ImGui
import org.joml.Vector3f
import kotlin.math.abs

class ViewportDragDropHandler(
    private val viewportRenderer: ViewportRenderer,
    private val editorCamera: EditorCamera,
    private val eventSystem: EventSystem,
    private val inputProvider: InputProvider
) {
    fun renderDragDropTarget(scene: Scene?) {
        if (scene == null) return

        ImGui.setCursorPos(viewportRenderer.imageScreenPosX, viewportRenderer.imageScreenPosY + 40f)
        if (ImGui.beginDragDropTarget()) {
            // Accept prefab payloads
            val prefabPayload = ImGui.acceptDragDropPayload<PrefabData>(PrefabData.PAYLOAD)

            val payloadTexture = ImGui.acceptDragDropPayload<String>("TEXTURE")
            val payloadSound = ImGui.acceptDragDropPayload<String>("SOUND")
            val payloadAnimation = ImGui.acceptDragDropPayload<Animation>("ANIMATION")

            if (prefabPayload != null) {
                computeDropPosition()?.let { hitPoint ->
                    eventSystem.publish(ViewportAction.SpawnPrefab(prefabPayload.type, hitPoint, prefabPayload.material))
                }
            }

            if (payloadTexture != null) {
                val hoveredObject = scene.hoveredGameObject
                val dropPosition = computeDropPosition()
                eventSystem.publish(ViewportAction.DropTexture(payloadTexture, hoveredObject, dropPosition))
            }

            if (payloadSound != null) {
                val hoveredObject = scene.hoveredGameObject
                if (hoveredObject != null) {
                    eventSystem.publish(ViewportAction.DropSound(payloadSound, hoveredObject))
                }
            }

            if (payloadAnimation != null) {
                val hoveredObject = scene.hoveredGameObject
                if (hoveredObject != null) {
                    eventSystem.publish(ViewportAction.ApplyAnimation(payloadAnimation, hoveredObject))
                }
            }

            ImGui.endDragDropTarget()
        }
    }

    private fun computeDropPosition(): Vector3f? {
        val ray = inputProvider.screenToRay(editorCamera.camera)

        if (abs(ray.direction.y) > 0.0001f) {
            val t = -ray.origin.y / ray.direction.y
            if (t > 0) {
                return Vector3f(ray.direction).mul(t).add(ray.origin)
            }
        }
        return null
    }
}
