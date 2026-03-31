package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.data.PrefabData
import com.pafoid.skate.editor.gizmos.MeasureTool
import com.pafoid.skate.editor.imgui.EditorScenesTabBar
import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.AddAudioComponentCommand
import com.pafoid.skate.editor.systems.CreateGameObjectCommand
import com.pafoid.skate.editor.systems.DeleteGameObjectCommand
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.PrefabsGenerator
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.UndoRedoManager
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
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.render.renderer.Renderer
import com.pafoid.skate.engine.utils.JobSystem
import com.pafoid.skate.engine.utils.ScreenshotUtils
import com.pafoid.skate.engine.utils.UnitSystem
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import org.joml.Vector2f
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.abs
import kotlin.math.roundToInt

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

    var imageScreenPosX = 0f
    var imageScreenPosY = 0f
    var imageSizeX = 0f
    var imageSizeY = 0f

    private val gamepadOverlay = GamepadOverlay()
    private val trickUIWindow = TrickUIWindow()
    private val scenesTabBar = EditorScenesTabBar()
    private var trickUIInitialized = false

    override fun imgui(pOpen: ImBoolean?) {
        // Using literal for NoTabItem (1 << 23) since it's missing in bindings
        val noTabItem = 1 shl 23
        ImGui.begin(stringManager.getString("window.game_viewport"), ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse or ImGuiWindowFlags.NoTitleBar or noTabItem)

        scenesTabBar.render(sceneManager)

        val windowSize = getLargestSizeForViewport()
        val windowPos = ImVec2(0f, TAB_BAR_HEIGHT)

        renderToolbar(windowPos)

        ImGui.setCursorPos(
            windowPos.x + TOOLBAR_BUTTON_SPACING / 2f + ImGui.getStyle().framePaddingX,
            windowPos.y + TOOLBAR_HEIGHT + ImGui.getStyle().framePaddingY
        )

        drawImage(windowSize)

        renderViewportContextMenu(windowPos, windowSize)

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
                    val mousePos = ImVec2()
                    ImGui.getMousePos(mousePos)
                    val relX = mousePos.x - imageScreenPosX
                    val relY = mousePos.y - imageScreenPosY

                    val ray = scene.camera.screenToRay(relX, relY, imageSizeX, imageSizeY)

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

        renderViewportOverlays(windowPos, windowSize)

        if (settingsManager.engine.editor.showGamepadOverlay) {
            gamepadOverlay.imgui(Vector2f(imageScreenPosX, imageScreenPosY), Vector2f(imageSizeX, imageSizeY))
        }

        mouseListener.setGameViewportPos(Vector2f(imageScreenPosX, imageScreenPosY))
        mouseListener.setGameViewportSize(Vector2f(imageSizeX, imageSizeY))

        val editorInput = sceneManager.currentScene?.systemManager?.getSystem<com.pafoid.skate.editor.EditorCamera>()?.editorInput
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

    private fun drawImage(windowSize: ImVec2) {
        val screenPos = ImVec2()
        ImGui.getCursorScreenPos(screenPos)
        imageScreenPosX = screenPos.x
        imageScreenPosY = screenPos.y
        imageSizeX = windowSize.x
        imageSizeY = windowSize.y - TOOLBAR_HEIGHT

        val texId = renderer.frameBuffer.getTextureId()
        ImGui.image(texId.toLong(), imageSizeX, imageSizeY, 0f, 1f, 1f, 0f)
    }

    private fun renderViewportOverlays(windowPos: ImVec2, windowSize: ImVec2) {
        val scene = sceneManager.currentScene

        // Initialize TrickUIWindow with event subscriptions (once per scene)
        if (scene != null && !trickUIInitialized) {
            trickUIWindow.init(scene)
            trickUIInitialized = true
        }

        // FPS Overlay (Top Left - inside game view)
        ImGui.setCursorPos(windowPos.x + OVERLAY_PADDING, windowPos.y + TOOLBAR_HEIGHT + OVERLAY_PADDING)
        ImGui.beginChild(
            "FPS_Overlay",
            FPS_OVERLAY_WIDTH,
            FPS_OVERLAY_HEIGHT,
            false,
            ImGuiWindowFlags.NoBackground or ImGuiWindowFlags.NoDecoration
        )
        ImGui.textColored(0f, 1f, 0f, 1f, "FPS: ${ImGui.getIO().framerate.toInt()}")
        ImGui.endChild()

        // Speedometer Overlay (Bottom Left)
        val skateGo = scene?.gameObjectManager?.gameObjects?.find { it.name == "Skateboard" }
        val rb = skateGo?.getComponent<RigidBody3D>()
        val velocity = rb?.rawBody?.getLinearVelocity(null)
        if (velocity != null) {
            val speedMS = velocity.length()
            val settings = settingsManager.engine.editor
            val (speedDisplay, unitLabel) = if (settings.unitSystem == UnitSystem.METRIC) {
                Pair(speedMS * 3.6f, "km/h")
            } else {
                Pair(speedMS * 2.23694f, "mph")
            }

            ImGui.setCursorPos(
                windowPos.x + OVERLAY_PADDING,
                windowPos.y + windowSize.y - SPEED_OVERLAY_HEIGHT - OVERLAY_PADDING
            )
            ImGui.beginChild(
                "Speed_Overlay",
                SPEED_OVERLAY_WIDTH,
                SPEED_OVERLAY_HEIGHT,
                false,
                ImGuiWindowFlags.NoBackground or ImGuiWindowFlags.NoDecoration
            )
            ImGui.textColored(1f, 1f, 1f, 1f, "${speedDisplay.roundToInt()} $unitLabel")
            ImGui.endChild()
        }

        // Trick UI Overlay (Bottom Left, above Speedometer)
        // TrickUIWindow now uses events - no need to pass GameObject
        val trickX = windowPos.x + OVERLAY_PADDING
        val trickY = windowPos.y + windowSize.y - SPEED_OVERLAY_HEIGHT - TRICK_OVERLAY_HEIGHT - (OVERLAY_PADDING * 2)
        trickUIWindow.imgui(trickX, trickY, TRICK_OVERLAY_WIDTH, TRICK_OVERLAY_HEIGHT)
    }

    fun getHoveredObject(): GameObject? {
        return sceneManager.currentScene?.systemManager?.getSystem<GizmoSystem>()?.getHoveredGameObject()
    }

    private fun getLargestSizeForViewport(): ImVec2 {
        val windowSize = ImVec2()
        ImGui.getContentRegionAvail(windowSize)

        val targetAspectRatio = 1920f / 1080f
        var aspectWidth = windowSize.x
        var aspectHeight = aspectWidth / targetAspectRatio
        if (aspectHeight > windowSize.y) {
            aspectHeight = windowSize.y
            aspectWidth = aspectHeight * targetAspectRatio
        }

        return ImVec2(aspectWidth, aspectHeight)
    }

    private fun renderToolbar(windowPos: ImVec2) {
        val isPlaying = engine.runtimePlaying
        val scene = sceneManager.currentScene
        val toolbarPosY = windowPos.y + TOOLBAR_BUTTON_SPACING / 2f + ImGui.getStyle().framePaddingY

        val buttons = mutableListOf<() -> Unit>()
        val gizmoSystem = scene?.systemManager?.getSystem<GizmoSystem>()

        if (gizmoSystem != null && !isPlaying) {
            // Select Tool
            buttons.add {
                val isActive = gizmoSystem.usingGizmo == GizmoSystem.SELECTION_GIZMO
                if (isActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
                if (ImGui.button(Icons.MOUSE_POINTER, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                    gizmoSystem.toggleGizmo(GizmoSystem.SELECTION_GIZMO)
                }
                if (isActive) ImGui.popStyleColor()
                if (ImGui.isItemHovered()) ImGui.setTooltip("Select Tool (Q)")
            }

            // Translate Tool
            buttons.add {
                val isActive = gizmoSystem.usingGizmo == GizmoSystem.TRANSLATE_GIZMO
                if (isActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
                if (ImGui.button(Icons.MOVE, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                    gizmoSystem.toggleGizmo(GizmoSystem.TRANSLATE_GIZMO)
                }
                if (isActive) ImGui.popStyleColor()
                if (ImGui.isItemHovered()) ImGui.setTooltip("Translate Tool (W)")
            }

            // Rotate Tool
            buttons.add {
                val isActive = gizmoSystem.usingGizmo == GizmoSystem.ROTATION_GIZMO
                if (isActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
                if (ImGui.button(Icons.ROTATE, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                    gizmoSystem.toggleGizmo(GizmoSystem.ROTATION_GIZMO)
                }
                if (isActive) ImGui.popStyleColor()
                if (ImGui.isItemHovered()) ImGui.setTooltip("Rotate Tool (E)")
            }

            // Scale Tool
            buttons.add {
                val isActive = gizmoSystem.usingGizmo == GizmoSystem.SCALE_GIZMO
                if (isActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
                if (ImGui.button(Icons.SCALE, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                    gizmoSystem.toggleGizmo(GizmoSystem.SCALE_GIZMO)
                }
                if (isActive) ImGui.popStyleColor()
                if (ImGui.isItemHovered()) ImGui.setTooltip("Scale Tool (R)")
            }

            // Measure Tool
            buttons.add {
                val isActive = gizmoSystem.usingGizmo == GizmoSystem.MEASURE_GIZMO
                if (isActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
                if (ImGui.button(Icons.RULER, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                    gizmoSystem.toggleGizmo(GizmoSystem.MEASURE_GIZMO)
                }
                if (isActive) {
                    ImGui.popStyleColor()
                    // Render measurement tooltip
                    scene.systemManager.getSystem<MeasureTool>()?.let { tool ->
                        tool.measurementText?.let { text ->
                            tool.measurementPos?.let { pos ->
                                ImGui.setNextWindowPos(pos.x, pos.y)
                                ImGui.beginTooltip()
                                ImGui.text(text)
                                ImGui.endTooltip()
                            }
                        }
                    }
                }
                if (ImGui.isItemHovered()) ImGui.setTooltip("Measure Tool (M)")
            }
        }

        if (isPlaying) {
            buttons.add {
                val timeScale = scene?.getTimeScale() ?: 1.0f
                if (timeScale == 1.0f) {
                    if (ImGui.button(Icons.PAUSE, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                        scene?.setTimeScale(0.0f)
                        logger.logEditor("Simulation paused")
                    }
                    if (ImGui.isItemHovered()) ImGui.setTooltip("Pause Simulation (Time Scale: 0.0)")
                } else {
                    if (ImGui.button(Icons.PLAY, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                        scene?.setTimeScale(1.0f)
                        logger.logEditor("Simulation resumed")
                    }
                    if (ImGui.isItemHovered()) ImGui.setTooltip("Resume Simulation (Time Scale: 1.0)")
                }
            }
            buttons.add {
                if (ImGui.button(Icons.STOP, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                    engine.runtimePlaying = false
                    scene?.setTimeScale(1.0f) // Reset timescale when stopping
                    logger.logEditor("Simulation stopped")
                }
                if (ImGui.isItemHovered()) ImGui.setTooltip("Stop Simulation")
            }
        } else {
            buttons.add {
                if (ImGui.button(Icons.PLAY, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                    engine.runtimePlaying = true
                    logger.logEditor("Simulation started")
                }
                if (ImGui.isItemHovered()) ImGui.setTooltip("Play Simulation")
            }
        }

        buttons.add {
            if (ImGui.button(Icons.GEAR, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                scene?.let{
                    scene.gameObjectManager.gameObjects.find { it.name == "Skateboard" }?.let { skate ->
                        skate.getComponent<Transform>()?.translation?.set(0f, 0.5f, 0f)
                        skate.getComponent<Transform>()?.rotation?.set(0f, 0f, 0f)
                        val rb = skate.getComponent<RigidBody3D>()
                        rb?.linearVelocity = Vector3f(0f, 0f, 0f)
                        rb?.angularVelocity = Vector3f(0f, 0f, 0f)
                        logger.logEditor("Scene reset")
                    }

                    scene.camera.position.set(0f, 5f, 20f)
                    scene.camera.yaw = 0f
                }
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip("Reset Scene")
        }
        buttons.add {
            val physicsDebugEnabled = scene?.physics3d?.debugEnabled ?: false
            if (physicsDebugEnabled) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
            }
            if (ImGui.button(Icons.ATOM, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                scene?.physics3d?.debugEnabled = !physicsDebugEnabled
                logger.logEditor("Physics debug toggled")
            }
            if (physicsDebugEnabled) {
                ImGui.popStyleColor()
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip("Toggle Physics Debug Wireframe")
        }
        buttons.add {
            if (ImGui.button(Icons.CAMERA, TOOLBAR_BUTTON_HEIGHT, TOOLBAR_BUTTON_HEIGHT)) {
                val fbo = renderer.frameBuffer
                ScreenshotUtils.takeScreenshot(fbo.width, fbo.height, fbo.getFboId())
                logger.logEditor("Screenshot taken")
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip("Take Screenshot")
        }
        val totalButtonWidth = (TOOLBAR_BUTTON_HEIGHT * buttons.size) + (TOOLBAR_BUTTON_SPACING * (buttons.size - 1))
        val toolbarPosX = windowPos.x + TOOLBAR_BUTTON_SPACING / 2f + ImGui.getStyle().framePaddingX
        ImGui.setCursorPos(toolbarPosX, toolbarPosY)
        ImGui.beginChild(
            "GameViewportToolbar",
            totalButtonWidth,
            TOOLBAR_HEIGHT,
            false,
            ImGuiWindowFlags.NoBackground or ImGuiWindowFlags.NoDecoration
        )

        buttons.forEachIndexed { index, button ->
            button()
            if (index < buttons.size - 1) {
                ImGui.sameLine(0f, TOOLBAR_BUTTON_SPACING)
            }
        }

        ImGui.endChild()
    }

    companion object {
        // Overlay Constants
        private const val OVERLAY_PADDING = 10f
        private const val FPS_OVERLAY_WIDTH = 80f
        private const val FPS_OVERLAY_HEIGHT = 30f
        private const val SPEED_OVERLAY_WIDTH = 120f
        private const val SPEED_OVERLAY_HEIGHT = 30f
        private const val TRICK_OVERLAY_WIDTH = 200f // Adjusted width for trick names
        private const val TRICK_OVERLAY_HEIGHT = 30f
        private const val TOOLBAR_HEIGHT = 40f
        private const val TAB_BAR_HEIGHT = 25f
        private const val TOOLBAR_BUTTON_HEIGHT = 30f
        private const val TOOLBAR_BUTTON_SPACING = 10f
    }

    private fun renderViewportContextMenu(windowPos: ImVec2, windowSize: ImVec2) {
        // Set cursor pos to the image area for context menu
        ImGui.setCursorPos(windowPos.x, windowPos.y + TOOLBAR_HEIGHT)
        
        if (ImGui.beginPopupContextWindow("ViewportContextMenu")) {
            ImGui.text(stringManager.getString("context.viewport.title"))
            ImGui.separator()
            
            val scene = sceneManager.currentScene
            
            // Create Empty
            if (ImGui.menuItem("${Icons.PLUS} ${stringManager.getString("context.viewport.create_empty")}")) {
                scene?.let {
                    val newObj = GameObject("GameObject")
                    undoRedoManager.executeCommand(CreateGameObjectCommand(newObj, it))
                }
            }
            
            // Create 3D Object submenu
            if (ImGui.beginMenu("${Icons.CUBE} ${stringManager.getString("context.viewport.create_3d_object")}")) {
                if (ImGui.menuItem(stringManager.getString("context.viewport.create_3d_object.cube"))) {
                    createPrimitiveObject("Cube", Vector3f(0.5f, 0.5f, 0.5f))
                }
                if (ImGui.menuItem(stringManager.getString("context.viewport.create_3d_object.sphere"))) {
                    createPrimitiveObject("Sphere", Vector3f(0.5f, 0.5f, 0.5f))
                }
                if (ImGui.menuItem(stringManager.getString("context.viewport.create_3d_object.cylinder"))) {
                    createPrimitiveObject("Cylinder", Vector3f(0.5f, 1f, 0.5f))
                }
                if (ImGui.menuItem(stringManager.getString("context.viewport.create_3d_object.plane"))) {
                    createPrimitiveObject("Plane", Vector3f(5f, 0f, 5f))
                }
                ImGui.endMenu()
            }
            
            // Create Light submenu
            if (ImGui.beginMenu("${Icons.SUN} ${stringManager.getString("context.viewport.create_light")}")) {
                if (ImGui.menuItem(stringManager.getString("context.viewport.create_light.directional"))) {
                    createLightObject("DirectionalLight", LightType.DIRECTIONAL)
                }
                if (ImGui.menuItem(stringManager.getString("context.viewport.create_light.point"))) {
                    createLightObject("PointLight", LightType.POINT)
                }
                if (ImGui.menuItem(stringManager.getString("context.viewport.create_light.spot"))) {
                    createLightObject("SpotLight", LightType.SPOT)
                }
                ImGui.endMenu()
            }
            
            // Create Camera
            if (ImGui.menuItem("${Icons.CAMERA} ${stringManager.getString("context.viewport.create_camera")}")) {
                scene?.let {
                    val cameraObj = GameObject("Camera")
                    // Camera component would be added here when implemented
                    undoRedoManager.executeCommand(CreateGameObjectCommand(cameraObj, it))
                }
            }
            
            ImGui.separator()
            
            // Create Skateboard Obstacle submenu
            if (ImGui.beginMenu("${Icons.GEAR} ${stringManager.getString("context.viewport.create_obstacle")}")) {
                if (ImGui.menuItem(stringManager.getString("context.viewport.create_obstacle.rail"))) {
                    prefabsGenerator.spawnRail(Vector3f(0f, 0.5f, 0f), null)
                }
                if (ImGui.menuItem(stringManager.getString("context.viewport.create_obstacle.ledge"))) {
                    prefabsGenerator.spawnLedge(Vector3f(0f, 0.25f, 0f), null)
                }
                if (ImGui.menuItem(stringManager.getString("context.viewport.create_obstacle.kicker"))) {
                    prefabsGenerator.spawnKicker(Vector3f(0f, 0f, 0f), null)
                }
                if (ImGui.menuItem(stringManager.getString("context.viewport.create_obstacle.manual_pad"))) {
                    prefabsGenerator.spawnManualPad(Vector3f(0f, 0.1f, 0f), null)
                }
                if (ImGui.menuItem(stringManager.getString("context.viewport.create_obstacle.bank"))) {
                    prefabsGenerator.spawnBank(Vector3f(0f, 0f, 0f), null)
                }
                if (ImGui.menuItem(stringManager.getString("context.viewport.create_obstacle.quarter_pipe"))) {
                    prefabsGenerator.spawnQuarterPipe(Vector3f(0f, 0f, 0f), null)
                }
                ImGui.endMenu()
            }
            
            ImGui.separator()
            
            // Object manipulation (only if object is selected)
            val selectedObject = scene?.getSelectedGameObject()
            if (selectedObject != null) {
                if (ImGui.menuItem("${Icons.COPY} ${stringManager.getString("context.viewport.duplicate")}")) {
                    duplicateGameObject(selectedObject)
                }
                if (ImGui.menuItem("${Icons.TRASH} ${stringManager.getString("context.viewport.delete")}")) {
                    undoRedoManager.executeCommand(DeleteGameObjectCommand(selectedObject, scene!!))
                }
                ImGui.separator()
            }
            
            // Focus Selected
            if (ImGui.menuItem("${Icons.EYE} ${stringManager.getString("context.viewport.focus_selected")}")) {
                focusOnSelectedObject()
            }
            
            // Reset Camera
            if (ImGui.menuItem("${Icons.ROTATE} ${stringManager.getString("context.viewport.reset_camera")}")) {
                scene?.camera?.position?.set(0f, 5f, 20f)
                scene?.camera?.yaw = 0f
                scene?.camera?.pitch = 0f
            }
            
            ImGui.endPopup()
        }
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
                // Add physics components
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
                transform.rotation?.set(Math.toRadians(-90.0).toFloat(), 0f, 0f)
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
        val relX = mousePos.x - imageScreenPosX
        val relY = mousePos.y - imageScreenPosY

        val ray = scene.camera.screenToRay(relX, relY, imageSizeX, imageSizeY)

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
        
        // Update the texture - note: this modifies the existing material
        // For a proper implementation, we'd need to update the material's texture path
        logger.logEditor("Applied texture to ${gameObject.name}: $texturePath")
        // TODO: Implement proper material texture update when material system is available
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
            val animation = resourceManager.getAnimation(animationPath)
            if (animation != null) {
                animator.addAnimation(animation)
                logger.logEditor("Added animation to ${gameObject.name}: $animationPath")
            } else {
                logger.logEditor("Failed to load animation: $animationPath")
            }
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

