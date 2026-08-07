package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.editor.TogglePhysicsDebugCommand
import com.pafoid.skate.editor.commands.objects.AddAudioComponentCommand
import com.pafoid.skate.editor.commands.objects.AddComponentCommand
import com.pafoid.skate.editor.commands.objects.ApplyAnimationCommand
import com.pafoid.skate.editor.commands.objects.ApplyTextureCommand
import com.pafoid.skate.editor.commands.objects.LockToggleCommand
import com.pafoid.skate.editor.commands.objects.RemoveComponentCommand
import com.pafoid.skate.editor.commands.objects.RenameComponentCommand
import com.pafoid.skate.editor.commands.objects.RenameGameObjectCommand
import com.pafoid.skate.editor.commands.objects.ReparentGameObjectCommand
import com.pafoid.skate.editor.commands.objects.SetComponentEnabledCommand
import com.pafoid.skate.editor.commands.objects.SetGameObjectEnabledCommand
import com.pafoid.skate.editor.commands.objects.TransformCommand
import com.pafoid.skate.editor.commands.objects.VisibilityToggleCommand
import com.pafoid.skate.editor.commands.scene.CreateGameObjectCommand
import com.pafoid.skate.editor.commands.scene.CreateLightCommand
import com.pafoid.skate.editor.commands.scene.CreatePrimitiveCommand
import com.pafoid.skate.editor.commands.scene.DeleteGameObjectCommand
import com.pafoid.skate.editor.commands.scene.DuplicateGameObjectCommand
import com.pafoid.skate.editor.commands.scene.SpawnPrefabCommand
import com.pafoid.skate.editor.data.PrefabType
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.events.ViewportAction.AddComponent
import com.pafoid.skate.editor.events.ViewportAction.ApplyAnimation
import com.pafoid.skate.editor.events.ViewportAction.CreateCamera
import com.pafoid.skate.editor.events.ViewportAction.CreateEmpty
import com.pafoid.skate.editor.events.ViewportAction.CreateEmptyChild
import com.pafoid.skate.editor.events.ViewportAction.CreateLight
import com.pafoid.skate.editor.events.ViewportAction.CreatePrimitive
import com.pafoid.skate.editor.events.ViewportAction.Delete
import com.pafoid.skate.editor.events.ViewportAction.DropSound
import com.pafoid.skate.editor.events.ViewportAction.DropTexture
import com.pafoid.skate.editor.events.ViewportAction.Duplicate
import com.pafoid.skate.editor.events.ViewportAction.FocusSelected
import com.pafoid.skate.editor.events.ViewportAction.GameObjectSelected
import com.pafoid.skate.editor.events.ViewportAction.PasteClipboard
import com.pafoid.skate.editor.events.ViewportAction.RemoveComponent
import com.pafoid.skate.editor.events.ViewportAction.RenameGameObject
import com.pafoid.skate.editor.events.ViewportAction.Reparent
import com.pafoid.skate.editor.events.ViewportAction.ResetCamera
import com.pafoid.skate.editor.events.ViewportAction.ResetTransform
import com.pafoid.skate.editor.events.ViewportAction.ScreenshotRequested
import com.pafoid.skate.editor.events.ViewportAction.SelectionCleared
import com.pafoid.skate.editor.events.ViewportAction.SetGameObjectEnabled
import com.pafoid.skate.editor.events.ViewportAction.SetSimulationTimeScale
import com.pafoid.skate.editor.events.ViewportAction.SpawnPrefab
import com.pafoid.skate.editor.events.ViewportAction.ToggleGizmo
import com.pafoid.skate.editor.events.ViewportAction.ToggleLock
import com.pafoid.skate.editor.events.ViewportAction.TogglePhysicsDebug
import com.pafoid.skate.editor.events.ViewportAction.ToggleVisibility
import com.pafoid.skate.editor.gizmos.EditorCamera
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.GizmoSystem
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.windows.viewport.ViewportRenderer
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.events.CameraAction
import com.pafoid.skate.engine.events.EngineAction
import com.pafoid.skate.engine.events.SceneAction.ResetScene
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.render.data.LightType
import org.joml.Vector3f

class ViewportActionHandler(
    private val engine: Engine,
    private val undoRedoManager: UndoRedoManager,
    private val clipboardService: ClipboardService,
    private val editorCamera: EditorCamera,
    private val viewportRenderer: ViewportRenderer,
    private val gizmoSystem: GizmoSystem,
) {
    private val eventSystem = engine.eventSystem
    private val logger = engine.logger
    private val sceneManager = engine.sceneManager
    private val gameObjectManager = engine.gameObjectManager

    fun init() {
        eventSystem.subscribe<GameObjectSelected> { event ->
            sceneManager.currentScene?.selectedGameObject = event.gameObject
        }

        eventSystem.subscribe<SelectionCleared> {
            sceneManager.currentScene?.selectedGameObject = null
        }

        eventSystem.subscribe<ScreenshotRequested> {
            viewportRenderer.captureScreenshot()
        }

        eventSystem.subscribe<CreateEmpty> { event ->
            handleCreateEmpty(event.scene)
        }
        eventSystem.subscribe<CreatePrimitive> { event ->
            handleCreatePrimitive(event.name, event.halfExtents)
        }
        eventSystem.subscribe<CreateLight> { event ->
            handleCreateLight(event.name, event.type)
        }
        eventSystem.subscribe<CreateCamera> { event ->
            handleCreateCamera(event.scene)
        }

        eventSystem.subscribe<SpawnPrefab> { event ->
            handleSpawnPrefab(event.prefabType, event.position)
        }

        eventSystem.subscribe<DropTexture> { event ->
            handleDropTexture(event.texturePath, event.targetObject, event.dropPosition)
        }
        eventSystem.subscribe<DropSound> { event ->
            addSoundToObject(event.targetObject, event.soundPath)
        }
        eventSystem.subscribe<ApplyAnimation> { event ->
            handleApplyAnimation(event.animation, event.targetObject)
        }

        eventSystem.subscribe<Duplicate> { event ->
            handleDuplicate(event.gameObject)
        }
        eventSystem.subscribe<Delete> { event ->
            handleDelete(event.gameObject, event.scene)
        }
        eventSystem.subscribe<RenameGameObject> { event ->
            handleRename(event.gameObject, event.newName)
        }
        eventSystem.subscribe<SetGameObjectEnabled> { event ->
            undoRedoManager.executeCommand(SetGameObjectEnabledCommand(event.gameObject, event.enabled))
        }
        eventSystem.subscribe<ViewportAction.RenameComponent> { event ->
            undoRedoManager.executeCommand(RenameComponentCommand(event.component, event.newName, event.component.name))
        }
        eventSystem.subscribe<ViewportAction.SetComponentEnabled> { event ->
            undoRedoManager.executeCommand(SetComponentEnabledCommand(event.component, event.enabled))
        }
        eventSystem.subscribe<AddComponent> { event ->
            undoRedoManager.executeCommand(AddComponentCommand(event.gameObject, event.componentType))
        }
        eventSystem.subscribe<RemoveComponent> { event ->
            undoRedoManager.executeCommand(RemoveComponentCommand(event.gameObject, event.componentType))
        }
        eventSystem.subscribe<ToggleVisibility> { event ->
            undoRedoManager.executeCommand(VisibilityToggleCommand(event.gameObject, event.visible))
        }
        eventSystem.subscribe<ToggleLock> { event ->
            undoRedoManager.executeCommand(LockToggleCommand(event.gameObject, event.locked))
        }
        eventSystem.subscribe<Reparent> { event ->
            undoRedoManager.executeCommand(ReparentGameObjectCommand(event.child, event.newParent))
        }
        eventSystem.subscribe<CreateEmptyChild> { event ->
            handleCreateEmptyChild(event.parent)
        }
        eventSystem.subscribe<ViewportAction.CutClipboard> { event ->
            handleCutClipboard(event.gameObject)
        }
        eventSystem.subscribe<ViewportAction.CopyClipboard> { event ->
            clipboardService.copy(event.gameObject)
        }
        eventSystem.subscribe<PasteClipboard> { event ->
            handlePasteClipboard(event.parent)
        }
        eventSystem.subscribe<EngineAction.SetRuntimePlaying> { event ->
            handleSetRuntimePlaying(event.playing)
        }
        eventSystem.subscribe<SetSimulationTimeScale> { event ->
            handleSetSimulationTimeScale(event.timeScale)
        }
        eventSystem.subscribe<ResetTransform> { event ->
            handleResetTransform(event.gameObject)
        }
        eventSystem.subscribe<ResetScene> {
            handleResetSkateScene()
        }
        eventSystem.subscribe<TogglePhysicsDebug> {
            undoRedoManager.executeCommand(TogglePhysicsDebugCommand(engine.systemManager))
        }
        eventSystem.subscribe<ToggleGizmo> { event ->
            gizmoSystem.toggleGizmo(event.gizmoId)
        }

        eventSystem.subscribe<FocusSelected> {
            handleFocusSelected()
        }
        eventSystem.subscribe<ResetCamera> {
            handleResetCamera()
        }
    }

    private fun handleCreateEmpty(scene: Scene) {
        val newObj = GameObject("GameObject")
        undoRedoManager.executeCommand(CreateGameObjectCommand(newObj, scene, gameObjectManager))
        logger.logEditor("Created empty GameObject: ${newObj.name}")
    }

    private fun handleCreatePrimitive(name: String, halfExtents: Vector3f) {
        val scene = sceneManager.currentScene ?: return
        val command = CreatePrimitiveCommand(name, halfExtents, scene, gameObjectManager)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Created primitive: $name")
    }

    private fun handleCreateLight(name: String, type: LightType) {
        val scene = sceneManager.currentScene ?: return
        val command = CreateLightCommand(name, type, scene, gameObjectManager)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Created light: $name")
    }

    private fun handleCreateCamera(scene: Scene) {
        val cameraObj = CameraComponent()
        undoRedoManager.executeCommand(AddComponentCommand(scene, ComponentType.CAMERA))
        logger.logEditor("Created camera: ${cameraObj.name}")
    }

    private fun handleSpawnPrefab(prefabType: PrefabType, position: Vector3f?) {
        val command = SpawnPrefabCommand(prefabType, position, engine.prefabsGenerator, gameObjectManager)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Spawned prefab: ${prefabType.name}")
    }

    private fun handleDropTexture(texturePath: String, targetObject: GameObject?, dropPosition: Vector3f?) {
        targetObject?.let { obj ->
            applyTextureToObject(obj, texturePath)
        } ?: run {
            dropPosition?.let { pos ->
                createTexturedPlane(pos, texturePath)
            }
        }
    }

    private fun handleApplyAnimation(animation: Animation, targetObject: GameObject) {
        applyAnimationToObject(targetObject, animation)
    }

    private fun handleDuplicate(gameObject: GameObject) {
        val scene = sceneManager.currentScene ?: return
        val command = DuplicateGameObjectCommand(gameObject, scene, gameObjectManager)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Duplicated GameObject: ${gameObject.name}")
    }

    private fun handleDelete(gameObject: GameObject, scene: Scene) {
        undoRedoManager.executeCommand(DeleteGameObjectCommand(gameObject, scene, gameObjectManager))
        eventSystem.publish(SelectionCleared)
        logger.logEditor("Deleted GameObject: ${gameObject.name}")
    }

    private fun handleRename(gameObject: GameObject, newName: String) {
        val oldName = gameObject.name
        if (newName.isBlank() || oldName == newName) return
        undoRedoManager.executeCommand(RenameGameObjectCommand(gameObject, newName, oldName))
    }

    private fun handleCreateEmptyChild(parent: GameObject) {
        val scene = sceneManager.currentScene ?: return
        val childObj = GameObject("GameObject")
        childObj.addComponent(Transform())
        childObj.parent = parent
        undoRedoManager.executeCommand(CreateGameObjectCommand(childObj, scene, gameObjectManager))
    }

    private fun handleCutClipboard(go: GameObject) {
        val scene = sceneManager.currentScene ?: return
        clipboardService.cut(go)
        undoRedoManager.executeCommand(DeleteGameObjectCommand(go, scene, gameObjectManager))
    }

    private fun handlePasteClipboard(parent: GameObject?) {
        val scene = sceneManager.currentScene ?: return
        val cloned = clipboardService.paste() ?: return
        parent?.let { cloned.parent = it }
        cloned.getComponent<Transform>()?.translation?.set(0f, 0f, 0f)
        undoRedoManager.executeCommand(CreateGameObjectCommand(cloned, scene, gameObjectManager))
    }

    private fun handleSetRuntimePlaying(playing: Boolean) {
        if (!playing) {
            sceneManager.currentScene?.getComponent<DayNightCycleComponent>()?.timeScale = 1.0f
            eventSystem.publish(CameraAction.SetCamera(editorCamera.camera))
        } else {
            val sceneCamera = sceneManager.currentScene?.getComponent<CameraComponent>()
            sceneCamera?.let { eventSystem.publish(CameraAction.SetCamera(it)) }
        }
    }

    private fun handleSetSimulationTimeScale(timeScale: Float) {
        sceneManager.currentScene?.getComponent<DayNightCycleComponent>()?.timeScale = timeScale
    }

    private fun handleResetSkateScene() {
        val scene = sceneManager.currentScene ?: return
        scene.reset()
        gameObjectManager.reset()
    }

    private fun handleResetTransform(gameObject: GameObject) {
        val transform = gameObject.getComponent<Transform>() ?: return
        val oldTransform = Transform().apply { copyFrom(transform) }
        val newTransform = Transform().apply {
            translation.set(0f, 0f, 0f)
            rotation.set(0f, 0f, 0f)
            scale.set(1f, 1f, 1f)
        }
        undoRedoManager.executeCommand(TransformCommand(gameObject, oldTransform, newTransform))
    }

    private fun handleFocusSelected() {
        val selected = sceneManager.currentScene?.selectedGameObject ?: return
        val transform = selected.getComponent<Transform>() ?: return
        val pos = transform.translation

        val camera = editorCamera.camera
        val offset = Vector3f(5f, 5f, 5f)
        camera.position.set(Vector3f(pos).add(offset))
        camera.lookAt(pos)
        logger.logEditor("Focused on: ${selected.name}")
    }

    private fun handleResetCamera() {
        val camera = editorCamera.camera
        camera.position.set(0f, 5f, 20f)// Should be initial values from scene file
        camera.yaw = 0f
        logger.logEditor("Reset camera")
    }

    private fun applyTextureToObject(gameObject: GameObject, texturePath: String) {
        if (gameObject.getComponent<RenderComponent>() == null) {
            logger.logEditor("Object has no RenderComponent")
            return
        }

        undoRedoManager.executeCommand(
            ApplyTextureCommand(gameObject, texturePath, engine.assetsManager, eventSystem)
        )
        logger.logEditor("Applied texture to ${gameObject.name}: $texturePath")
    }

    private fun createTexturedPlane(position: Vector3f, texturePath: String) {
        val scene = sceneManager.currentScene ?: return

        engine.jobSystem.runAsync {
            val planeObj = GameObject("TexturedPlane")
            val transform = Transform()
            transform.translation.set(position)
            transform.scale.set(10f, 0.1f, 10f)
            planeObj.addComponent(transform)

            val texture = engine.assetsManager.getTexture(texturePath)
            val baseModel = engine.assetsManager.loadModel(Assets.Models.CUBE)
            val model = TexturedModel(
                path = Assets.Models.CUBE,
                mesh = baseModel.mesh,
                material = Material(texture)
            )

            engine.jobSystem.runOnMain {
                val renderComponent = RenderComponent(model = model, castShadow = false, receiveShadow = true)
                planeObj.addComponent(renderComponent)
                planeObj.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = BodyType.Static })
                planeObj.addComponent(BoxCollider3D(Vector3f(5f, 0.05f, 5f)))
                undoRedoManager.executeCommand(CreateGameObjectCommand(planeObj, scene, gameObjectManager))
                logger.logEditor("Created textured plane at ${position.x}, ${position.y}, ${position.z}")
            }
        }
    }

    private fun addSoundToObject(gameObject: GameObject, soundPath: String) {
        val audioComponent = gameObject.getComponent<AudioComponent>()
        if (audioComponent?.soundFilePath == soundPath) return

        undoRedoManager.executeCommand(
            AddAudioComponentCommand(gameObject, soundPath)
        )

        if (audioComponent != null) {
            logger.logEditor("Updated sound for ${gameObject.name}: $soundPath")
        } else {
            logger.logEditor("Added AudioComponent to ${gameObject.name}: $soundPath")
        }
    }

    private fun applyAnimationToObject(gameObject: GameObject, animation: Animation) {
        val animator = gameObject.getComponent<Animator>()

        if (animator != null) {
            undoRedoManager.executeCommand(
                ApplyAnimationCommand(gameObject, animation, logger)
            )
            logger.logEditor("Added animation to ${gameObject.name}: ${animation.name}")
        } else {
            logger.logEditor("Object has no Animator component")
        }
    }
}
