package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.SceneAction.CloseAllRequested
import com.pafoid.skate.engine.events.SceneAction.CloseOthersRequested
import com.pafoid.skate.engine.events.SceneAction.CloseRequested
import com.pafoid.skate.engine.events.SceneAction.CreateRequested
import com.pafoid.skate.engine.events.SceneAction.DeleteRequested
import com.pafoid.skate.engine.events.SceneAction.RenameRequested
import com.pafoid.skate.engine.events.SceneAction.SaveAsRequested
import com.pafoid.skate.engine.events.SceneAction.SaveRequested
import imgui.ImGui
import imgui.flag.ImGuiTabBarFlags
import imgui.flag.ImGuiTabItemFlags
import imgui.type.ImBoolean

class EditorScenesTabBar(
    private val eventSystem: EventSystem,
    private val stringManager: StringManager
) {
    private var toRemove = -1
    fun render(sceneManager: SceneManager) {
        if (ImGui.beginTabBar("##EditorScenesTabBar", ImGuiTabBarFlags.Reorderable or ImGuiTabBarFlags.AutoSelectNewTabs)) {
            val openScenes = sceneManager.openScenes.toList()

            openScenes.forEachIndexed { index, scene ->
                val open = ImBoolean(true)
                var flags = ImGuiTabItemFlags.None
                if (scene.isDirty) {
                    flags = flags or ImGuiTabItemFlags.UnsavedDocument
                }

                val displayName = scene.name.replace(".scene", "", ignoreCase = true)
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

                if (ImGui.beginTabItem("$displayName###sceneTab_${scene.uId}", open, flags)) {
                    if (sceneManager.activeSceneIndex != index) {
                        eventSystem.publish(ViewportAction.TabSelected(scene))
                    }

                    // Scene tab context menu
                    if (ImGui.beginPopupContextItem()) {
                        if (ImGui.menuItem("${Icons.EDIT} ${stringManager.getString("context.scene_tab.rename")}")) {
                            eventSystem.publish(RenameRequested(scene, scene.name))
                        }
                        ImGui.separator()
                        if (ImGui.menuItem("${Icons.SAVE} ${stringManager.getString("context.scene_tab.save")}")) {
                            eventSystem.publish(SaveRequested(scene))
                        }
                        if (ImGui.menuItem(stringManager.getString("context.scene_tab.save_as"))) {
                            eventSystem.publish(SaveAsRequested(scene))
                        }
                        ImGui.separator()
                        val canClose = sceneManager.openScenes.isNotEmpty()
                        if (ImGui.menuItem(stringManager.getString("context.scene_tab.close"), null, false, canClose)) {
                            if (scene.isDirty) {
                                toRemove = index
                                ImGui.openPopup(stringManager.getString("context.scene_tab.close_confirmation_title"))
                            } else eventSystem.publish(CloseRequested(scene))
                        }
                        if (ImGui.menuItem(stringManager.getString("context.scene_tab.close_others"), null, false, canClose)) {
                            eventSystem.publish(CloseOthersRequested(scene))
                        }
                        if (ImGui.menuItem(stringManager.getString("context.scene_tab.close_all"), null, false, canClose)) {
                            eventSystem.publish(CloseAllRequested)
                        }
                        ImGui.separator()
                        if (ImGui.menuItem("${Icons.TRASH} ${stringManager.getString("context.scene_tab.delete")}")) {
                            eventSystem.publish(DeleteRequested(scene))
                        }
                        ImGui.endPopup()
                    }

                    ImGui.endTabItem()
                }

                if (!open.get()) {
                    if (scene.isDirty) {
                        toRemove = index
                        ImGui.openPopup(stringManager.getString("context.scene_tab.close_confirmation_title"))
                    } else eventSystem.publish(CloseRequested(scene))
                }
            }

            MImGui.showConfirmationModal(
                title = stringManager.getString("context.scene_tab.close_confirmation_title"),
                message = stringManager.getString("context.scene_tab.close_confirmation_message"),
                confirmText = stringManager.getString("lbl.input_system.yes"),
                cancelText = stringManager.getString("lbl.input_system.no"),
                onConfirm = {
                    if (toRemove >= 0) {
                        eventSystem.publish(CloseRequested(openScenes[toRemove]))
                    }
                    toRemove = -1
                }
            )

            val addTabFlags =
                ImGuiTabItemFlags.Trailing or ImGuiTabItemFlags.NoCloseWithMiddleMouseButton or ImGuiTabItemFlags.NoReorder
            if (ImGui.beginTabItem(Icons.PLUS, addTabFlags)) {
                eventSystem.publish(CreateRequested)
                ImGui.endTabItem()
            }

            ImGui.endTabBar()
        }
    }
}
