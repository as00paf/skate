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
import com.pafoid.skate.game.prefabs.MaterialType
import com.pafoid.skate.game.prefabs.PrefabType
import com.pafoid.skate.editor.data.PrimitiveType
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
import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.commands.CompositeCommand
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.SelectionLogic
import com.pafoid.skate.editor.systems.GizmoSystem
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.windows.viewport.ViewportRenderer
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.Material
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
import com.pafoid.skate.engine.getAllComponents
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.render.data.LightType
import org.joml.Vector3f
import java.io.File

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
    private val assetsManager = engine.assetsManager

    fun init() {
        eventSystem.subscribe<GameObjectSelected> { event ->
            sceneManager.currentScene?.selectedGameObject = event.gameObject
        }

        eventSystem.subscribe<ViewportAction.MultiSelectionChanged> { event ->
            sceneManager.currentScene?.setSelection(event.selectedObjects)
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
            handleCreatePrimitive(event.name, event.type, event.halfExtents)
        }
        eventSystem.subscribe<CreateLight> { event ->
            handleCreateLight(event.name, event.type)
        }
        eventSystem.subscribe<CreateCamera> { event ->
            handleCreateCamera(event.scene)
        }

        eventSystem.subscribe<SpawnPrefab> { event ->
            sceneManager.currentScene?.let { handleSpawnPrefab(event.prefabType, event.position, event.material, it) }
        }

        eventSystem.subscribe<ViewportAction.DropModel> { event ->
            sceneManager.currentScene?.let { handleDropModel(event.modelPath, event.dropPosition, it) }
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
        // A user delete asks first (DeleteObjectsDialog, EDT-62); Cut deletes right away.
        eventSystem.subscribe<Delete> { event ->
            if (!event.confirm) handleDelete(SelectionLogic.targets(event.scene, event.gameObject), event.scene)
        }
        eventSystem.subscribe<ViewportAction.DeleteConfirmed> { event ->
            handleDelete(event.gameObjects, event.scene)
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
            undoRedoManager.executeCommand(AddComponentCommand(event.gameObject, event.componentType, logger))
            sceneManager.currentScene?.isDirty = true
        }
        eventSystem.subscribe<RemoveComponent> { event ->
            undoRedoManager.executeCommand(RemoveComponentCommand(event.component))
            sceneManager.currentScene?.isDirty = true
        }
        eventSystem.subscribe<ToggleVisibility> { event ->
            undoRedoManager.executeCommand(VisibilityToggleCommand(event.gameObject, event.visible))
            sceneManager.currentScene?.isDirty = true
        }
        eventSystem.subscribe<ToggleLock> { event ->
            undoRedoManager.executeCommand(LockToggleCommand(event.gameObject, event.locked))
        }
        eventSystem.subscribe<Reparent> { event ->
            val scene = sceneManager.currentScene ?: return@subscribe
            handleReparent(SelectionLogic.targets(scene, event.child), event.newParent, scene)
        }
        eventSystem.subscribe<ViewportAction.CreateUi> { event ->
            val scene = sceneManager.currentScene ?: return@subscribe
            undoRedoManager.executeCommand(com.pafoid.skate.editor.commands.scene.CreateUiCommand(event.kind, scene, event.parent ?: scene.selectedGameObject))
        }
        eventSystem.subscribe<CreateEmptyChild> { event ->
            handleCreateEmptyChild(event.parent)
        }
        eventSystem.subscribe<ViewportAction.CutClipboard> { event ->
            handleCutClipboard(event.gameObject)
        }
        eventSystem.subscribe<ViewportAction.CopyClipboard> { event ->
            clipboardService.copy(SelectionLogic.targets(sceneManager.currentScene, event.gameObject))
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

    private fun handleCreatePrimitive(name: String, type: PrimitiveType, halfExtents: Vector3f) {
        val scene = sceneManager.currentScene ?: return
        val command = CreatePrimitiveCommand(name, type, halfExtents, scene, gameObjectManager, assetsManager)
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
        undoRedoManager.executeCommand(AddComponentCommand(scene, ComponentType.CAMERA, logger))
        logger.logEditor("Created camera: ${cameraObj.name}")
        scene.isDirty = true
    }

    private fun handleSpawnPrefab(prefabType: PrefabType, position: Vector3f?, material: MaterialType?, scene: Scene) {
        val command = SpawnPrefabCommand(prefabType, position, material, engine.prefabsGenerator, gameObjectManager, scene)
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

    /** One command for one object; a [CompositeCommand] (one undo step) for several (EDT-74). */
    private fun executeAll(
        key: String,
        name: String,
        objects: List<GameObject>,
        scene: Scene,
        selectAfter: ((List<Command>) -> List<GameObject>)? = null,
        step: (GameObject) -> Command?,
    ) {
        if (objects.isEmpty()) return
        val single = objects.singleOrNull()
        val command = if (single != null) step(single) ?: return
        else CompositeCommand(key, name, objects.map { go -> { step(go) } }, scene, selectAfter)
        undoRedoManager.executeCommand(command)
    }

    private fun handleDuplicate(gameObject: GameObject) {
        val scene = sceneManager.currentScene ?: return
        val targets = SelectionLogic.targets(scene, gameObject)
        executeAll("command.CompositeCommand.duplicate", "Duplicate", targets, scene, selectAfter = { cmds ->
            cmds.mapNotNull { (it as? DuplicateGameObjectCommand)?.gameObject }
        }) { DuplicateGameObjectCommand(it, scene, gameObjectManager) }
        logger.logEditor("Duplicated ${targets.joinToString { it.name }}")
    }

    private fun handleDelete(gameObjects: List<GameObject>, scene: Scene) {
        executeAll("command.CompositeCommand.delete", "Delete", gameObjects, scene) { DeleteGameObjectCommand(it, scene, gameObjectManager) }
        eventSystem.publish(SelectionCleared)
        logger.logEditor("Deleted ${gameObjects.joinToString { it.name }}")
    }

    /** Moves [children] under [newParent] as one step; moves that would do nothing or make a cycle are skipped. */
    private fun handleReparent(children: List<GameObject>, newParent: GameObject, scene: Scene) {
        val valid = children.filter { ReparentGameObjectCommand.isValid(it, newParent) }
        // Built when it runs, so each move sees where the previous moves left its siblings (CompositeCommand).
        executeAll("command.CompositeCommand.reparent", "Reparent", valid, scene, selectAfter = { _ -> scene.selectedGameObjects }) {
            if (ReparentGameObjectCommand.isValid(it, newParent)) ReparentGameObjectCommand(it, newParent, scene) else null
        }
        if (valid.isNotEmpty()) scene.isDirty = true
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
        undoRedoManager.executeCommand(CreateGameObjectCommand(childObj, scene, gameObjectManager, parent))
    }

    private fun handleCutClipboard(go: GameObject) {
        val scene = sceneManager.currentScene ?: return
        val targets = SelectionLogic.targets(scene, go)
        clipboardService.copy(targets)
        handleDelete(targets, scene)
    }

    /**
     * Pastes every copied object under [parent]. The first lands at the origin, as before (EDT-74); the others keep
     * their offsets from it, so a pasted group keeps its layout. All pasted objects end up selected.
     */
    private fun handlePasteClipboard(parent: GameObject?) {
        val scene = sceneManager.currentScene ?: return
        val pasted = clipboardService.pasteAll()
        if (pasted.isEmpty()) return
        val offset = pasted.first().getComponent<Transform>()?.translation?.let { Vector3f(it).negate() } ?: Vector3f()
        pasted.forEach { it.getComponent<Transform>()?.translation?.add(offset) }
        executeAll("command.CompositeCommand.paste", "Paste", pasted, scene, selectAfter = { pasted }) {
            CreateGameObjectCommand(it, scene, gameObjectManager, parent ?: scene)
        }
    }

    /** Camera and play speed follow Play; the pre-play snapshot is [PlayModeHandler]'s (EDT-45). */
    private fun handleSetRuntimePlaying(playing: Boolean) {
        if (!playing) {
            // Only the play-mode speed resets, on every scene that may have played; the saved timeScale stays (REN-23)
            sceneManager.openScenes.forEach { it.getComponent<DayNightCycleComponent>()?.playTimeScale = 1.0f }
            eventSystem.publish(CameraAction.SetCamera(editorCamera.camera, editorCamera.transform))
        } else {
            val sceneCameras = sceneManager.currentScene?.getAllComponents<CameraComponent>().orEmpty()
            val sceneCamera = sceneCameras.firstOrNull { it.isDefault } ?: sceneCameras.firstOrNull()
            sceneCamera?.let {
                eventSystem.publish(
                    CameraAction.SetCamera(
                        it,
                        it.gameObject?.getComponent<Transform>()
                    )
                )
            }
        }
    }

    private fun handleSetSimulationTimeScale(timeScale: Float) {
        sceneManager.currentScene?.getComponent<DayNightCycleComponent>()?.playTimeScale = timeScale
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

        val offset = Vector3f(5f, 5f, 5f)
        editorCamera.transform.translation.set(Vector3f(pos).add(offset))
        editorCamera.transform.lookAt(pos)
        logger.logEditor("Focused on: ${selected.name}")
    }

    private fun handleResetCamera() {
        editorCamera.transform.translation.set(0f, 5f, 20f)// Should be initial values from scene file
        editorCamera.transform.rotation.set(0f, 0f, 0f)
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
        sceneManager.currentScene?.isDirty = true
        logger.logEditor("Applied texture to ${gameObject.name}: $texturePath")
    }

    /** Spawns the model file at [path] (a `.glb` imported from a `.blend`, AST-19, or any model) at the drop point. */
    private fun handleDropModel(path: String, position: Vector3f?, scene: Scene) {
        val model = runCatching { engine.assetsManager.loadModel(path) }.getOrElse {
            logger.logEditor("Cannot load model $path: ${it.message}", LogLevel.ERROR)
            return
        }
        val obj = GameObject(File(path).nameWithoutExtension)
            .addComponent(Transform().apply { position?.let { translation.set(it) } })
            .addComponent(RenderComponent(model))
        undoRedoManager.executeCommand(CreateGameObjectCommand(obj, scene, gameObjectManager))
        logger.logEditor("Added model ${File(path).name}")
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
            val model = `3dModel`(path = Assets.Models.CUBE, mesh = baseModel.mesh)

            engine.jobSystem.runOnMain {
                val renderComponent =
                    RenderComponent(model, castShadow = false, receiveShadow = true, material = Material(texture))
                planeObj.addComponent(renderComponent)
                planeObj.addComponent(RigidBody3D(0f, BodyType.Static).apply { friction = 0.5f; })
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
        sceneManager.currentScene?.isDirty = true

        if (audioComponent != null) {
            logger.logEditor("Updated sound for ${gameObject.name}: $soundPath")
        } else {
            logger.logEditor("Added AudioComponent to ${gameObject.name}: $soundPath")
        }
    }

    private fun applyAnimationToObject(gameObject: GameObject, animation: Animation) {
        // The command adds an Animator when the object has none; undo removes it again.
        undoRedoManager.executeCommand(ApplyAnimationCommand(gameObject, animation, logger))
        sceneManager.currentScene?.isDirty = true
    }
}
