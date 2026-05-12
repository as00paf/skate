package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.`object`.AddAudioComponentCommand
import com.pafoid.skate.editor.commands.`object`.ApplyAnimationCommand
import com.pafoid.skate.editor.commands.`object`.ApplyTextureCommand
import com.pafoid.skate.editor.commands.scene.CreateGameObjectCommand
import com.pafoid.skate.editor.commands.scene.CreateLightCommand
import com.pafoid.skate.editor.commands.scene.CreatePrimitiveCommand
import com.pafoid.skate.editor.commands.scene.DeleteGameObjectCommand
import com.pafoid.skate.editor.commands.scene.DuplicateGameObjectCommand
import com.pafoid.skate.editor.commands.scene.SpawnPrefabCommand
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.PrefabsGenerator
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.windows.assetBrowser.PrefabType
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.events.SelectionCleared
import com.pafoid.skate.engine.events.ViewportCreateCamera
import com.pafoid.skate.engine.events.ViewportCreateEmpty
import com.pafoid.skate.engine.events.ViewportCreateLight
import com.pafoid.skate.engine.events.ViewportCreatePrimitive
import com.pafoid.skate.engine.events.ViewportDelete
import com.pafoid.skate.engine.events.ViewportDropAnimation
import com.pafoid.skate.engine.events.ViewportDropSound
import com.pafoid.skate.engine.events.ViewportDropTexture
import com.pafoid.skate.engine.events.ViewportDuplicate
import com.pafoid.skate.engine.events.ViewportFocusSelected
import com.pafoid.skate.engine.events.ViewportResetCamera
import com.pafoid.skate.engine.events.ViewportSpawnPrefab
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.render.CameraManager
import com.pafoid.skate.engine.utils.JobSystem
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ViewportActionHandler : KoinComponent {
    private val sceneManager: SceneManager by inject()
    private val undoRedoManager: UndoRedoManager by inject()
    private val eventSystem: EventSystem by inject()
    private val logger: LoggerService by inject()
    private val prefabsGenerator: PrefabsGenerator by inject()
    private val resourceManager: ResourceManager by inject()
    private val gameObjectManager: GameObjectManager by inject()
    private val cameraManager: CameraManager by inject()

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

        eventSystem.subscribe<ViewportFocusSelected> {
            handleFocusSelected()
        }
        eventSystem.subscribe<ViewportResetCamera> {
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

    private fun handleCreateLight(name: String, type: com.pafoid.skate.editor.ui.windows.LightType) {
        val scene = sceneManager.currentScene ?: return
        val command = CreateLightCommand(name, type, scene, gameObjectManager)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Created light: $name")
    }

    private fun handleCreateCamera(scene: Scene) {
        val cameraObj = GameObject("Camera")
        undoRedoManager.executeCommand(CreateGameObjectCommand(cameraObj, scene, gameObjectManager))
        logger.logEditor("Created camera: ${cameraObj.name}")
    }

    private fun handleSpawnPrefab(prefabType: PrefabType, position: Vector3f?) {
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

        JobSystem.runAsync {
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

            JobSystem.runOnMain {
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
