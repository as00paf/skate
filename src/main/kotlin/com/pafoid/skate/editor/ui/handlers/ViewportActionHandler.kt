package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.`object`.AddAudioComponentCommand
import com.pafoid.skate.editor.commands.`object`.AddComponentCommand
import com.pafoid.skate.editor.commands.`object`.ApplyAnimationCommand
import com.pafoid.skate.editor.commands.`object`.ApplyTextureCommand
import com.pafoid.skate.editor.commands.`object`.LockToggleCommand
import com.pafoid.skate.editor.commands.`object`.RemoveComponentCommand
import com.pafoid.skate.editor.commands.`object`.RenameGameObjectCommand
import com.pafoid.skate.editor.commands.`object`.ReparentGameObjectCommand
import com.pafoid.skate.editor.commands.`object`.SetGameObjectEnabledCommand
import com.pafoid.skate.editor.commands.`object`.TransformCommand
import com.pafoid.skate.editor.commands.`object`.VisibilityToggleCommand
import com.pafoid.skate.editor.commands.scene.CreateGameObjectCommand
import com.pafoid.skate.editor.commands.scene.CreateLightCommand
import com.pafoid.skate.editor.commands.scene.CreatePrimitiveCommand
import com.pafoid.skate.editor.commands.scene.DeleteGameObjectCommand
import com.pafoid.skate.editor.commands.scene.DuplicateGameObjectCommand
import com.pafoid.skate.editor.commands.scene.SpawnPrefabCommand
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.EditorMutationGate
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.PrefabsGenerator
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.windows.assetBrowser.PrefabType
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.TimeComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.ecs.systems.GizmoSystem
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.editor.events.SelectionCleared
import com.pafoid.skate.editor.events.ViewportAddComponent
import com.pafoid.skate.editor.events.ViewportCreateEmptyChild
import com.pafoid.skate.editor.events.ViewportCreateCamera
import com.pafoid.skate.editor.events.ViewportCreateEmpty
import com.pafoid.skate.editor.events.ViewportCreateLight
import com.pafoid.skate.editor.events.ViewportCreatePrimitive
import com.pafoid.skate.editor.events.ViewportDelete
import com.pafoid.skate.editor.events.ViewportDropAnimation
import com.pafoid.skate.editor.events.ViewportDropSound
import com.pafoid.skate.editor.events.ViewportDropTexture
import com.pafoid.skate.editor.events.ViewportDuplicate
import com.pafoid.skate.editor.events.ViewportFocusSelected
import com.pafoid.skate.editor.events.ViewportPasteClipboard
import com.pafoid.skate.editor.events.ViewportRemoveComponent
import com.pafoid.skate.editor.events.ViewportRenameGameObject
import com.pafoid.skate.editor.events.ViewportReparent
import com.pafoid.skate.editor.events.ViewportResetScene
import com.pafoid.skate.editor.events.ViewportResetCamera
import com.pafoid.skate.editor.events.ViewportResetTransform
import com.pafoid.skate.editor.events.ViewportSetGameObjectEnabled
import com.pafoid.skate.editor.events.ViewportSetRuntimePlaying
import com.pafoid.skate.editor.events.ViewportSetSimulationTimeScale
import com.pafoid.skate.editor.events.ViewportSpawnPrefab
import com.pafoid.skate.editor.events.ViewportToggleGizmo
import com.pafoid.skate.editor.events.ViewportToggleLock
import com.pafoid.skate.editor.events.ViewportTogglePhysicsDebug
import com.pafoid.skate.editor.events.ViewportToggleVisibility
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.render.CameraManager
import com.pafoid.skate.engine.render.data.LightType
import com.pafoid.skate.engine.utils.IJobSystem
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ViewportActionHandler : KoinComponent {
    private val engine: Engine by inject()
    private val sceneManager: SceneManager by inject()
    private val undoRedoManager: UndoRedoManager by inject()
    private val eventSystem: EventSystem by inject()
    private val logger: LoggerService by inject()
    private val clipboardService: ClipboardService by inject()
    private val mutationGate: EditorMutationGate by inject()
    private val prefabsGenerator: PrefabsGenerator by inject()
    private val resourceManager: ResourceManager by inject()
    private val gameObjectManager: GameObjectManager by inject()
    private val cameraManager: CameraManager by inject()
    private val systemManager: SystemManager by inject()
    private val jobSystem: IJobSystem by inject()

    fun init() {
        eventSystem.subscribe<ViewportCreateEmpty> { event ->
            handleCreateEmpty(event.scene)
        }
        eventSystem.subscribe<ViewportCreatePrimitive> { event ->
            handleCreatePrimitive(event.name, event.halfExtents)
        }
        eventSystem.subscribe<ViewportCreateLight> { event ->
            handleCreateLight(event.name, event.type)
        }
        eventSystem.subscribe<ViewportCreateCamera> { event ->
            handleCreateCamera(event.scene)
        }

        eventSystem.subscribe<ViewportSpawnPrefab> { event ->
            handleSpawnPrefab(event.prefabType, event.position)
        }

        eventSystem.subscribe<ViewportDropTexture> { event ->
            handleDropTexture(event.texturePath, event.targetObject, event.dropPosition)
        }
        eventSystem.subscribe<ViewportDropSound> { event ->
            handleDropSound(event.soundPath, event.targetObject)
        }
        eventSystem.subscribe<ViewportDropAnimation> { event ->
            handleDropAnimation(event.animationPath, event.targetObject)
        }

        eventSystem.subscribe<ViewportDuplicate> { event ->
            handleDuplicate(event.gameObject)
        }
        eventSystem.subscribe<ViewportDelete> { event ->
            handleDelete(event.gameObject, event.scene)
        }
        eventSystem.subscribe<ViewportRenameGameObject> { event ->
            handleRename(event.gameObject, event.newName)
        }
        eventSystem.subscribe<ViewportSetGameObjectEnabled> { event ->
            handleSetEnabled(event.gameObject, event.enabled)
        }
        eventSystem.subscribe<ViewportAddComponent> { event ->
            handleAddComponent(event.gameObject, event.componentType)
        }
        eventSystem.subscribe<ViewportRemoveComponent> { event ->
            handleRemoveComponent(event.gameObject, event.componentType)
        }
        eventSystem.subscribe<ViewportToggleVisibility> { event ->
            handleToggleVisibility(event.gameObject, event.visible)
        }
        eventSystem.subscribe<ViewportToggleLock> { event ->
            handleToggleLock(event.gameObject, event.locked)
        }
        eventSystem.subscribe<ViewportReparent> { event ->
            handleReparent(event.child, event.newParent)
        }
        eventSystem.subscribe<ViewportCreateEmptyChild> { event ->
            handleCreateEmptyChild(event.parent)
        }
        eventSystem.subscribe<ViewportPasteClipboard> { event ->
            handlePasteClipboard(event.parent)
        }
        eventSystem.subscribe<ViewportSetRuntimePlaying> { event ->
            handleSetRuntimePlaying(event.playing)
        }
        eventSystem.subscribe<ViewportSetSimulationTimeScale> { event ->
            handleSetSimulationTimeScale(event.timeScale)
        }
        eventSystem.subscribe<ViewportResetTransform> { event ->
            handleResetTransform(event.gameObject)
        }
        eventSystem.subscribe<ViewportResetScene> {
            handleResetSkateScene()
        }
        eventSystem.subscribe<ViewportTogglePhysicsDebug> {
            handleTogglePhysicsDebug()
        }
        eventSystem.subscribe<ViewportToggleGizmo> { event ->
            handleToggleGizmo(event.gizmoId)
        }

        eventSystem.subscribe<ViewportFocusSelected> {
            handleFocusSelected()
        }
        eventSystem.subscribe<ViewportResetCamera> {
            handleResetCamera()
        }
    }

    private fun handleCreateEmpty(scene: Scene) {
        if (mutationGate.blockIfPlaying("create empty object")) return
        val newObj = GameObject("GameObject")
        undoRedoManager.executeCommand(CreateGameObjectCommand(newObj, scene, gameObjectManager))
        logger.logEditor("Created empty GameObject: ${newObj.name}")
    }

    private fun handleCreatePrimitive(name: String, halfExtents: Vector3f) {
        if (mutationGate.blockIfPlaying("create primitive")) return
        val scene = sceneManager.currentScene ?: return
        val command = CreatePrimitiveCommand(name, halfExtents, scene, gameObjectManager)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Created primitive: $name")
    }

    private fun handleCreateLight(name: String, type: LightType) {
        if (mutationGate.blockIfPlaying("create light")) return
        val scene = sceneManager.currentScene ?: return
        val command = CreateLightCommand(name, type, scene, gameObjectManager)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Created light: $name")
    }

    private fun handleCreateCamera(scene: Scene) {
        if (mutationGate.blockIfPlaying("create camera")) return
        val cameraObj = GameObject("Camera")
        undoRedoManager.executeCommand(CreateGameObjectCommand(cameraObj, scene, gameObjectManager))
        logger.logEditor("Created camera: ${cameraObj.name}")
    }

    private fun handleSpawnPrefab(prefabType: PrefabType, position: Vector3f?) {
        if (mutationGate.blockIfPlaying("spawn prefab")) return
        if (prefabType == PrefabType.SKATEBOARD) {
            prefabsGenerator.spawnSkateboard()
            logger.logEditor("Spawned skateboard")
            return
        }

        val command = SpawnPrefabCommand(prefabType, position, prefabsGenerator, gameObjectManager)
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

    private fun handleDropSound(soundPath: String, targetObject: GameObject) {
        addSoundToObject(targetObject, soundPath)
    }

    private fun handleDropAnimation(animationPath: String, targetObject: GameObject) {
        applyAnimationToObject(targetObject, animationPath)
    }

    private fun handleDuplicate(gameObject: GameObject) {
        if (mutationGate.blockIfPlaying("duplicate game object")) return
        val scene = sceneManager.currentScene ?: return
        val command = DuplicateGameObjectCommand(gameObject, scene, gameObjectManager)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Duplicated GameObject: ${gameObject.name}")
    }

    private fun handleDelete(gameObject: GameObject, scene: Scene) {
        if (mutationGate.blockIfPlaying("delete game object")) return
        undoRedoManager.executeCommand(DeleteGameObjectCommand(gameObject, scene, gameObjectManager))
        eventSystem.publish(SelectionCleared)
        logger.logEditor("Deleted GameObject: ${gameObject.name}")
    }

    private fun handleRename(gameObject: GameObject, newName: String) {
        if (mutationGate.blockIfPlaying("rename game object")) return
        val oldName = gameObject.name
        if (newName.isBlank() || oldName == newName) return
        undoRedoManager.executeCommand(RenameGameObjectCommand(gameObject, newName, oldName))
    }

    private fun handleSetEnabled(gameObject: GameObject, enabled: Boolean) {
        if (mutationGate.blockIfPlaying("set game object enabled")) return
        undoRedoManager.executeCommand(SetGameObjectEnabledCommand(gameObject, enabled))
    }

    private fun handleAddComponent(gameObject: GameObject, componentType: ComponentType) {
        if (mutationGate.blockIfPlaying("add component")) return
        undoRedoManager.executeCommand(AddComponentCommand(gameObject, componentType))
    }

    private fun handleRemoveComponent(gameObject: GameObject, componentType: ComponentType) {
        if (mutationGate.blockIfPlaying("remove component")) return
        undoRedoManager.executeCommand(RemoveComponentCommand(gameObject, componentType))
    }

    private fun handleToggleVisibility(gameObject: GameObject, visible: Boolean) {
        if (mutationGate.blockIfPlaying("toggle visibility")) return
        undoRedoManager.executeCommand(VisibilityToggleCommand(gameObject, visible))
    }

    private fun handleToggleLock(gameObject: GameObject, locked: Boolean) {
        if (mutationGate.blockIfPlaying("toggle lock")) return
        undoRedoManager.executeCommand(LockToggleCommand(gameObject, locked))
    }

    private fun handleReparent(child: GameObject, newParent: GameObject) {
        if (mutationGate.blockIfPlaying("reparent game object")) return
        undoRedoManager.executeCommand(ReparentGameObjectCommand(child, newParent))
    }

    private fun handleCreateEmptyChild(parent: GameObject) {
        if (mutationGate.blockIfPlaying("create empty child")) return
        val scene = sceneManager.currentScene ?: return
        val childObj = GameObject("GameObject")
        childObj.addComponent(Transform())
        childObj.parent = parent
        undoRedoManager.executeCommand(CreateGameObjectCommand(childObj, scene, gameObjectManager))
    }

    private fun handlePasteClipboard(parent: GameObject?) {
        if (mutationGate.blockIfPlaying("paste game object")) return
        val scene = sceneManager.currentScene ?: return
        val cloned = clipboardService.paste() ?: return
        parent?.let { cloned.parent = it }
        cloned.getComponent<Transform>()?.translation?.set(0f, 0f, 0f)
        undoRedoManager.executeCommand(CreateGameObjectCommand(cloned, scene, gameObjectManager))
    }

    private fun handleSetRuntimePlaying(playing: Boolean) {
        engine.runtimePlaying = playing
        if (!playing) {
            sceneManager.currentScene?.getComponent<TimeComponent>()?.timeScale = 1.0f
        }
    }

    private fun handleSetSimulationTimeScale(timeScale: Float) {
        sceneManager.currentScene?.getComponent<TimeComponent>()?.timeScale = timeScale
    }

    private fun handleResetSkateScene() {
        if (mutationGate.blockIfPlaying("reset skate scene")) return
        val scene = sceneManager.currentScene ?: return
        val skate = scene.gameObjects.find { obj -> obj.name == "Skateboard" } ?: return
        val transform = skate.getComponent<Transform>() ?: return
        transform.translation.set(0f, 0.5f, 0f)
        transform.rotation.set(0f, 0f, 0f)
        val rb = skate.getComponent<RigidBody3D>()
        rb?.linearVelocity = Vector3f(0f, 0f, 0f)
        rb?.angularVelocity = Vector3f(0f, 0f, 0f)
        scene.camera.position.set(0f, 5f, 20f)
        scene.camera.yaw = 0f
    }

    private fun handleTogglePhysicsDebug() {
        val scene = sceneManager.currentScene ?: return
        scene.physics3d.debugEnabled = !scene.physics3d.debugEnabled
    }

    private fun handleToggleGizmo(gizmoId: Int) {
        if (mutationGate.blockIfPlaying("toggle gizmo")) return
        systemManager.getSystem<GizmoSystem>()?.toggleGizmo(gizmoId)
    }

    private fun handleResetTransform(gameObject: GameObject) {
        if (mutationGate.blockIfPlaying("reset transform")) return
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

        val camera = cameraManager.getActiveCamera() ?: return
        val offset = Vector3f(5f, 5f, 5f)
        camera.position.set(Vector3f(pos).add(offset))
        camera.lookAt(pos)
        logger.logEditor("Focused on: ${selected.name}")
    }

    private fun handleResetCamera() {
        val camera = cameraManager.getActiveCamera() ?: return
        camera.position.set(0f, 5f, 20f)
        camera.yaw = 0f
        logger.logEditor("Reset camera")
    }

    private fun applyTextureToObject(gameObject: GameObject, texturePath: String) {
        val renderComponent = gameObject.getComponent<RenderComponent>() ?: run {
            logger.logEditor("Object has no RenderComponent")
            return
        }

        undoRedoManager.executeCommand(
            ApplyTextureCommand(gameObject, null, texturePath, resourceManager, eventSystem)
        )
        logger.logEditor("Applied texture to ${gameObject.name}: $texturePath")
    }

    private fun createTexturedPlane(position: Vector3f, texturePath: String) {
        val scene = sceneManager.currentScene ?: return

        jobSystem.runAsync {
            val planeObj = GameObject("TexturedPlane")
            val transform = Transform()
            transform.translation.set(position)
            transform.scale.set(10f, 0.1f, 10f)
            planeObj.addComponent(transform)

            val texture = resourceManager.loadTexture(texturePath)
            val baseModel = resourceManager.loadModel(Assets.Models.CUBE)
            val texturedModel = TexturedModel(
                baseModel.mesh[0].rawModel,
                texture
            )

            jobSystem.runOnMain {
                planeObj.addComponent(RenderComponent(model = texturedModel, castShadow = false, receiveShadow = true))
                planeObj.addComponent(RigidBody3D(0f).apply { friction = 0.5f; bodyType = BodyType.Static })
                planeObj.addComponent(BoxCollider3D(Vector3f(5f, 0.05f, 5f)))
                undoRedoManager.executeCommand(CreateGameObjectCommand(planeObj, scene, gameObjectManager))
                logger.logEditor("Created textured plane at ${position.x}, ${position.y}, ${position.z}")
            }
        }
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
            undoRedoManager.executeCommand(
                ApplyAnimationCommand(gameObject, null, animationPath, resourceManager, eventSystem)
            )
            logger.logEditor("Added animation to ${gameObject.name}: $animationPath")
        } else {
            logger.logEditor("Object has no Animator component")
        }
    }
}
