package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.Transform
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** The editor camera of one scene: where it is, where it looks, and its projection mode (EDT-58). */
@Serializable
data class EditorCameraState(
    val position: List<Float>,
    val rotation: List<Float>,
    val orthographic: Boolean,
    val zoom: Float,
    val fov: Float,
) {
    fun applyTo(camera: CameraComponent, transform: Transform) {
        transform.translation.set(position[0], position[1], position[2])
        transform.rotation.set(rotation[0], rotation[1], rotation[2])
        camera.isOrthographic = orthographic
        camera.zoom = zoom
        camera.fov = fov
    }

    companion object {
        fun of(camera: CameraComponent, transform: Transform) = EditorCameraState(
            listOf(transform.translation.x, transform.translation.y, transform.translation.z),
            listOf(transform.rotation.x, transform.rotation.y, transform.rotation.z),
            camera.isOrthographic, camera.zoom, camera.fov,
        )
    }
}

@Serializable
private data class EditorCameraFile(val version: Int = 1, val scenes: Map<String, EditorCameraState> = emptyMap())

/**
 * Remembers the editor camera per scene (EDT-58). Leaving a scene stores its camera, entering one restores it.
 * The states are editor-only data: they live in [FILE_NAME] inside the project's hidden [DIR] folder, which the
 * exporter never packs (hidden folders are skipped), so exported games never see them. They are written on every
 * scene switch and when the current scene closes, so they survive an editor restart.
 *
 * [projectDir] is the open project's folder (null without a project). Scenes are keyed by name, like their files.
 */
class EditorCameraMemory(
    private val camera: () -> CameraComponent,
    private val transform: () -> Transform,
    private val projectDir: () -> File?,
    private val warn: (String) -> Unit = {},
) {
    private val states = LinkedHashMap<String, EditorCameraState>()
    private var loadedFrom: File? = null

    /** The scene whose camera is live in the editor right now. */
    var currentScene: String? = null
        private set

    fun stateOf(scene: String): EditorCameraState? {
        ensureLoaded()
        return states[scene]
    }

    /** The editor now shows [scene]: keep the previous scene's camera, then restore [scene]'s if it has one. */
    fun switchTo(scene: String) {
        ensureLoaded()
        if (scene == currentScene) return
        storeCurrent()
        currentScene = scene
        restore(scene)
        save()
    }

    /** Re-applies [scene]'s saved camera (after something reset the camera, e.g. a scene's default projection). */
    fun restore(scene: String) {
        stateOf(scene)?.applyTo(camera(), transform())
    }

    /** [scene] closes: keep its camera if it is the live one. */
    fun onClosing(scene: String) {
        if (scene != currentScene) return
        storeCurrent()
        save()
        currentScene = null
    }

    /** Keeps the live camera on disk (editor shutdown). */
    fun flush() {
        storeCurrent()
        save()
    }

    /** The project closed: forget everything; the next project loads its own file. */
    fun reset() {
        states.clear()
        loadedFrom = null
        currentScene = null
    }

    private fun storeCurrent() {
        currentScene?.let { states[it] = EditorCameraState.of(camera(), transform()) }
    }

    private fun file(): File? = projectDir()?.let { File(File(it, DIR), FILE_NAME) }

    private fun ensureLoaded() {
        val file = file()
        if (file == loadedFrom) return
        states.clear()
        currentScene = null
        loadedFrom = file
        if (file == null || !file.isFile) return
        try {
            states.putAll(json.decodeFromString(EditorCameraFile.serializer(), file.readText()).scenes)
        } catch (e: Exception) {
            warn("Could not read the editor cameras in ${file.path}: ${e.message}")
        }
    }

    private fun save() {
        val file = file() ?: return
        if (file != loadedFrom) return
        try {
            file.parentFile.mkdirs()
            file.writeText(json.encodeToString(EditorCameraFile.serializer(), EditorCameraFile(scenes = states)))
        } catch (e: Exception) {
            warn("Could not save the editor cameras to ${file.path}: ${e.message}")
        }
    }

    companion object {
        /** Hidden, so [com.pafoid.skate.engine.utils.AssetsPacker] never ships it. */
        const val DIR = ".editor"
        const val FILE_NAME = "scene-cameras.json"
        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    }
}
