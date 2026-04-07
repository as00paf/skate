package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.EditorCamera
import com.pafoid.skate.editor.LevelEditorSceneInitializer
import com.pafoid.skate.editor.commands.AddAudioComponentCommand
import com.pafoid.skate.editor.commands.ApplyAnimationCommand
import com.pafoid.skate.editor.commands.ApplyTextureCommand
import com.pafoid.skate.editor.commands.CreateGameObjectCommand
import com.pafoid.skate.editor.commands.DeleteGameObjectCommand
import com.pafoid.skate.editor.data.PrefabData
import com.pafoid.skate.editor.imgui.EditorScenesTabBar
import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.PrefabsGenerator
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.menus.ViewportContextMenu
import com.pafoid.skate.editor.ui.menus.ViewportContextMenuCallbacks
import com.pafoid.skate.editor.ui.windows.viewport.ViewportOverlays
import com.pafoid.skate.editor.ui.windows.viewport.ViewportRenderer
import com.pafoid.skate.editor.ui.windows.viewport.ViewportToolbar
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.scene.getSelectedGameObject
import com.pafoid.skate.engine.ecs.systems.GizmoSystem
import com.pafoid.skate.engine.events.EventSystem
import com.pafoid.skate.engine.events.SelectionCleared
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.render.renderer.Renderer
import com.pafoid.skate.engine.utils.JobSystem
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import org.joml.Vector2f
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.abs

class GameViewWindow : IWindow, KoinComponent {

    private val logger: LoggerService by inject()
    private val mouseListener: MouseListener by inject()
    private val sceneManager: SceneManager by inject()
    private val settingsManager: SettingsManager by inject()
    private val prefabsGenerator: PrefabsGenerator by inject()
    private val stringManager: StringManager by inject()
    private val renderer: Renderer by inject()
    private val engine: Engine by inject()
    private val undoRedoManager: UndoRedoManager by inject()
    private val resourceManager: ResourceManager by inject()
    private val eventSystem: EventSystem by inject()

    // Extracted viewport components
    private val viewportRenderer: ViewportRenderer by inject()
    private val viewportToolbar: ViewportToolbar by inject()
    private val viewportContextMenu: ViewportContextMenu by inject()
    private val viewportOverlays: ViewportOverlays by inject()
    
    // Gamepad overlay and scene tab bar (not extracted yet)
    private val gamepadOverlay = GamepadOverlay()
    private val sceneInitializer: LevelEditorSceneInitializer by inject()
    private val scenesTabBar by lazy { EditorScenesTabBar(sceneInitializer) }

    // Reusable buffers to avoid per-frame allocations
    private val tempVec2 = ImVec2()
    private val tempMousePos = ImVec2()

    override fun imgui(pOpen: ImBoolean?) {
        val noTabItem = 1 shl 23
        ImGui.begin(stringManager.getString("window.game_viewport"), ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse or ImGuiWindowFlags.NoTitleBar or noTabItem)

        scenesTabBar.render(sceneManager)

        val windowSize = getLargestSizeForViewport()
        val windowPos = ImVec2(0f, TAB_BAR_HEIGHT)

        viewportToolbar.render(windowPos)

        ImGui.setCursorPos(
            windowPos.x + 10f / 2f + ImGui.getStyle().framePaddingX,
            windowPos.y + TOOLBAR_HEIGHT + ImGui.getStyle().framePaddingY
        )

        viewportRenderer.render(windowSize)
        viewportRenderer.updateFramebuffer()

        viewportContextMenu.render(windowPos, sceneManager.currentScene, object : ViewportContextMenuCallbacks {
            override fun onCreateEmpty(scene: Scene) {
                val newObj = GameObject("GameObject")
                undoRedoManager.executeCommand(CreateGameObjectCommand(newObj, scene))
            }
            override fun onCreatePrimitive(name: String, halfExtents: Vector3f) {
                createPrimitiveObject(name, halfExtents)
            }
            override fun onCreateLight(name: String, type: LightType) {
                createLightObject(name, type)
            }
            override fun onCreateCamera(scene: Scene) {
                val cameraObj = GameObject("Camera")
                undoRedoManager.executeCommand(CreateGameObjectCommand(cameraObj, scene))
            }
            override fun onSpawnRail() = prefabsGenerator.spawnRail(Vector3f(0f, 0.5f, 0f), null)
            override fun onSpawnLedge() = prefabsGenerator.spawnLedge(Vector3f(0f, 0.25f, 0f), null)
            override fun onSpawnKicker() = prefabsGenerator.spawnKicker(Vector3f(0f, 0f, 0f), null)
            override fun onSpawnManualPad() = prefabsGenerator.spawnManualPad(Vector3f(0f, 0.1f, 0f), null)
            override fun onSpawnBank() = prefabsGenerator.spawnBank(Vector3f(0f, 0f, 0f), null)
            override fun onSpawnQuarterPipe() = prefabsGenerator.spawnQuarterPipe(Vector3f(0f, 0f, 0f), null)
            override fun onDuplicate(gameObject: GameObject) = duplicateGameObject(gameObject)
            override fun onDelete(gameObject: GameObject, scene: Scene) {
                undoRedoManager.executeCommand(DeleteGameObjectCommand(gameObject, scene))
                eventSystem.publish(SelectionCleared)
            }
            override fun onFocusSelected(scene: Scene) = focusOnSelectedObject()
            override fun onResetCamera(scene: Scene) {
                scene.camera.position.set(0f, 5f, 20f)
                scene.camera.yaw = 0f
            }
        })

        ImGui.setCursorPos(windowPos.x, windowPos.y + TOOLBAR_HEIGHT)
        if (ImGui.beginDragDropTarget()) {
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
                val scene = sceneManager.currentScene
                if (scene != null) {
                    ImGui.getMousePos(tempMousePos)
                    val relX = tempMousePos.x - viewportRenderer.imageScreenPosX
                    val relY = tempMousePos.y - viewportRenderer.imageScreenPosY

                    val ray = scene.camera.screenToRay(relX, relY, viewportRenderer.imageSizeX, viewportRenderer.imageSizeY)

                    if (abs(ray.direction.y) > 0.0001f) {
                        val t = -ray.origin.y / ray.direction.y
                        if (t > 0) {
                            val hitPoint = Vector3f(ray.direction).mul(t).add(ray.origin)

                            when {
                                payloadRail != null -> prefabsGenerator.spawnRail(hitPoint, payloadRail.material)
                                payloadLedge != null -> prefabsGenerator.spawnLedge(hitPoint, payloadLedge.material)
                                payloadKicker != null -> prefabsGenerator.spawnKicker(hitPoint, payloadKicker.material)
                                payloadManualPad != null -> prefabsGenerator.spawnManualPad(hitPoint, payloadManualPad.material)
                                payloadBank != null -> prefabsGenerator.spawnBank(hitPoint, payloadBank.material)
                                payloadQuarterPipe != null -> prefabsGenerator.spawnQuarterPipe(hitPoint, payloadQuarterPipe.material)
                                payloadSkateboard != null -> prefabsGenerator.spawnSkateboard()
                            }
                        }
                    }
                }
            }

            if (payloadTexture != null) {
                val scene = sceneManager.currentScene
                val hoveredObject = getHoveredObject()

                if (scene != null) {
                    if (hoveredObject != null) {
                        val renderComponent = hoveredObject.getComponent<RenderComponent>()
                        if (renderComponent != null) {
                            applyTextureToObject(hoveredObject, payloadTexture)
                        } else {
                            createTexturedPlaneAtDropLocation(scene, payloadTexture)
                        }
                    } else {
                        createTexturedPlaneAtDropLocation(scene, payloadTexture)
                    }
                }
            }

            if (payloadSound != null) {
                val hoveredObject = getHoveredObject()
                if (hoveredObject != null) {
                    addSoundToObject(hoveredObject, payloadSound)
                } else {
                    logger.logEditor("Drop sound on an object to add AudioComponent")
                }
            }

            if (payloadAnimation != null) {
                val hoveredObject = getHoveredObject()
                if (hoveredObject != null) {
                    applyAnimationToObject(hoveredObject, payloadAnimation)
                } else {
                    logger.logEditor("Drop animation on a skater object")
                }
            }

            ImGui.endDragDropTarget()
        }

        // Render overlays using extracted component
        viewportOverlays.render(windowPos, windowSize, sceneManager.currentScene)

        if (settingsManager.engine.editor.showGamepadOverlay) {
            gamepadOverlay.imgui(
                Vector2f(viewportRenderer.imageScreenPosX, viewportRenderer.imageScreenPosY),
                Vector2f(viewportRenderer.imageSizeX, viewportRenderer.imageSizeY)
            )
        }

        mouseListener.setGameViewportPos(Vector2f(viewportRenderer.imageScreenPosX, viewportRenderer.imageScreenPosY))
        mouseListener.setGameViewportSize(Vector2f(viewportRenderer.imageSizeX, viewportRenderer.imageSizeY))

        val editorInput = sceneManager.currentScene?.systemManager?.getSystem<EditorCamera>()?.editorInput
        editorInput?.isFocused = ImGui.isWindowFocused()

        val hovered = getHoveredObject()
        if (hovered != null) {
            ImGui.setCursorPos(windowPos.x + 10f, windowPos.y + 20f)
            ImGui.textColored(
                1f,
                1f,
                1f,
                0.5f,
                "Picked ID: ${hovered.getUid()}"
            )
        }

        ImGui.end()
    }

    fun getHoveredObject(): GameObject? {
        return sceneManager.currentScene?.systemManager?.getSystem<GizmoSystem>()?.getHoveredGameObject()
    }

    private fun getLargestSizeForViewport(): ImVec2 {
        ImGui.getContentRegionAvail(tempVec2)

        // Return full available space - no aspect ratio constraint
        return ImVec2(tempVec2.x, tempVec2.y)
    }

    private companion object {
        private const val TOOLBAR_HEIGHT = 40f
        private const val TAB_BAR_HEIGHT = 25f
    }

    private fun createPrimitiveObject(name: String, halfExtents: Vector3f) {
        val scene = sceneManager.currentScene ?: return

        val obj = GameObject(name)
        val transform = Transform()
        transform.translation.set(0f, halfExtents.y, 0f)
        obj.addComponent(transform)

        // Add render component with basic cube model (using JobSystem for async loading)
        JobSystem.runAsync {
            val baseModel = resourceManager.loadModel(Assets.Models.CUBE)
            val texture = resourceManager.loadTexture(Assets.Textures.DEFAULT)
            val texturedModel = TexturedModel(
                baseModel.mesh[0].rawModel,
                texture
            )

            JobSystem.runOnMain {
                obj.addComponent(RenderComponent(model = texturedModel, castShadow = true, receiveShadow = true))
                obj.addComponent(RigidBody3D(1f).apply { friction = 0.5f; bodyType = BodyType.Dynamic })
                obj.addComponent(BoxCollider3D(halfExtents))
                undoRedoManager.executeCommand(CreateGameObjectCommand(obj, scene))
            }
        }
    }

    private fun createLightObject(name: String, type: LightType) {
        val scene = sceneManager.currentScene ?: return

        val lightObj = GameObject(name)
        val transform = Transform()
        when (type) {
            LightType.DIRECTIONAL -> {
                transform.translation.set(0f, 10f, 0f)
                transform.rotation?.set(
                    Math.toRadians(-45.0).toFloat(),
                    Math.toRadians(45.0).toFloat(),
                    0f
                )
            }
            LightType.POINT -> transform.translation.set(0f, 5f, 0f)
            LightType.SPOT -> {
                transform.translation.set(0f, 5f, 0f)
                transform.rotation.set(Math.toRadians(-90.0).toFloat(), 0f, 0f)
            }
        }
        lightObj.addComponent(transform)

        undoRedoManager.executeCommand(CreateGameObjectCommand(lightObj, scene))
    }

    private fun createTexturedPlane(position: Vector3f, texturePath: String) {
        val scene = sceneManager.currentScene ?: return

        JobSystem.runAsync {
            val planeObj = GameObject("TexturedPlane")
            val transform = Transform()
            transform.translation.set(position)
            transform.scale.set(10f, 0.1f, 10f) // Flat plane
            planeObj.addComponent(transform)

            val texture = resourceManager.loadTexture(texturePath)
            val baseModel = resourceManager.loadModel(Assets.Models.CUBE)
            val texturedModel = TexturedModel(
                baseModel.mesh[0].rawModel,
                texture
            )

            JobSystem.runOnMain {
                planeObj.addComponent(RenderComponent(model = texturedModel, castShadow = false, receiveShadow = true))
                planeObj.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = BodyType.Static })
                planeObj.addComponent(BoxCollider3D(Vector3f(5f, 0.05f, 5f)))
                undoRedoManager.executeCommand(CreateGameObjectCommand(planeObj, scene))
                logger.logEditor("Created textured plane at ${position.x}, ${position.y}, ${position.z}")
            }
        }
    }

    private fun createTexturedPlaneAtDropLocation(scene: Scene, texturePath: String) {
        val mousePos = ImVec2()
        ImGui.getMousePos(mousePos)
        val relX = mousePos.x - viewportRenderer.imageScreenPosX
        val relY = mousePos.y - viewportRenderer.imageScreenPosY

        val ray = scene.camera.screenToRay(relX, relY, viewportRenderer.imageSizeX, viewportRenderer.imageSizeY)

        if (abs(ray.direction.y) > 0.0001f) {
            val t = -ray.origin.y / ray.direction.y
            if (t > 0) {
                val hitPoint = Vector3f(ray.direction).mul(t).add(ray.origin)
                createTexturedPlane(hitPoint, texturePath)
            }
        }
    }

    private fun applyTextureToObject(gameObject: GameObject, texturePath: String) {
        val renderComponent = gameObject.getComponent<RenderComponent>() ?: run {
            logger.logEditor("Object has no RenderComponent")
            return
        }

        // For now, pass null for old texture path (undo will be limited)
        // A full implementation would extract the current texture path from the model
        undoRedoManager.executeCommand(
            ApplyTextureCommand(gameObject, null, texturePath, resourceManager, eventSystem)
        )
        
        logger.logEditor("Applied texture to ${gameObject.name}: $texturePath")
    }

    private fun addSoundToObject(gameObject: GameObject, soundPath: String) {
        val audioComponent = gameObject.getComponent<AudioComponent>()
        val hadAudioComponent = audioComponent != null

        undoRedoManager.executeCommand(
            AddAudioComponentCommand(gameObject, soundPath, hadAudioComponent)
        )

        if (hadAudioComponent) {
            logger.logEditor("Updated sound for ${gameObject.name}: $soundPath")
        } else {
            logger.logEditor("Added AudioComponent to ${gameObject.name}: $soundPath")
        }
    }

    private fun applyAnimationToObject(gameObject: GameObject, animationPath: String) {
        val animator = gameObject.getComponent<Animator>()

        if (animator != null) {
            // For now, pass null for old animation path (undo will be limited)
            undoRedoManager.executeCommand(
                ApplyAnimationCommand(gameObject, null, animationPath, resourceManager, eventSystem)
            )
            logger.logEditor("Added animation to ${gameObject.name}: $animationPath")
        } else {
            logger.logEditor("Object has no Animator component")
        }
    }

    private fun duplicateGameObject(gameObject: GameObject) {
        val scene = sceneManager.currentScene ?: return

        val duplicated = GameObject("${gameObject.name}_clone")
        val originalTransform = gameObject.getComponent<Transform>()
        val newTransform = Transform()
        originalTransform?.let { orig ->
            newTransform.copyFrom(orig)
        }
        newTransform.translation.x += 1f // Offset by 1 unit on X

        duplicated.addComponent(newTransform)

        undoRedoManager.executeCommand(CreateGameObjectCommand(duplicated, scene))
    }

    private fun focusOnSelectedObject() {
        val scene = sceneManager.currentScene ?: return
        val selectedObject = scene.getSelectedGameObject() ?: return

        val transform = selectedObject.getComponent<Transform>() ?: return
        val pos = transform.translation

        // Move camera to look at the object from a reasonable distance
        val offset = Vector3f(5f, 5f, 5f)
        scene.camera.position.set(Vector3f(pos).add(offset))
        scene.camera.lookAt(pos)
    }
}
