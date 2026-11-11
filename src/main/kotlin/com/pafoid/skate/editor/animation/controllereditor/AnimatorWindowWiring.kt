package com.pafoid.skate.editor.animation.controllereditor

import com.pafoid.skate.app.Editor
import com.pafoid.skate.editor.animation.AnimatorControllerCatalog
import com.pafoid.skate.editor.events.AnimatorAction
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.animation.edit.SkAnimFormat
import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.events.Event
import com.pafoid.skate.engine.getComponent
import java.io.File

/**
 * Opens a controller in the Animator window and shows it (Animator inspector Edit button, Project window). With an
 * [animator], it becomes the window's preview character and [path] defaults to its controller.
 */
data class OpenAnimatorController(val path: String?, val animator: Animator? = null) : Event("animator.open_controller")

/** A controller file was saved from the Animator window: Animators using it are rebuilt (ANI-21 §5.4, Q5). */
data class AnimatorControllerSaved(val path: String, val file: File) : Event("animator.controller_saved")

/** Builds the Animator window's controller with the editor's services (ANI-21). */
object AnimatorWindowWiring {
    const val WINDOW = "window.animator"

    fun create(engine: Engine, editor: Editor, projectManager: ProjectManager): AnimatorWindowController {
        val clips = ProjectClipScanner { projectManager.getProjectDirectory() }
        val controller = AnimatorWindowController(
            execute = { editor.undoRedoManager.executeCommand(it) },
            resolve = { resolve(it, projectManager.getProjectDirectory()) },
            animationsDir = { projectManager.getProjectDirectory()?.let { File(it, "Assets${File.separator}Animations") } },
            projectClips = { clips.clips() },
            onSaved = { doc ->
                AnimatorControllerCatalog.shared.refresh()
                doc.file?.let { engine.eventSystem.publish(AnimatorControllerSaved(doc.path, it)) }
            },
            assignController = { animator, path -> engine.eventSystem.publish(AnimatorAction.SetController(animator, path)) },
        )
        engine.eventSystem.subscribe<OpenAnimatorController> { e ->
            val path = e.path ?: e.animator?.controllerPath
            e.animator?.let { controller.preview = it }
            if (path != null) controller.open(path)
            engine.eventSystem.publish(WindowAction.Show(WINDOW))
        }
        engine.eventSystem.subscribe<AnimatorControllerSaved> { e -> reload(engine, projectManager.getProjectDirectory(), e.file) }
        return controller
    }

    /**
     * Finds the controller Animators call [path]: a file (absolute, or relative to the project), else a bundled
     * resource, which is read-only (ANI-21 §5.2).
     */
    fun resolve(path: String, projectDir: File?): ResolvedController? {
        fileFor(path, projectDir)?.let { return ResolvedController(path, it, it.readText()) }
        val text = Animator::class.java.classLoader.getResourceAsStream(path.replace('\\', '/').trimStart('/'))
            ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return null
        return ResolvedController(path, null, text)
    }

    fun fileFor(path: String, projectDir: File?): File? {
        val direct = File(path)
        if (direct.isAbsolute && direct.isFile) return direct
        if (projectDir != null) File(projectDir, path).takeIf { it.isFile }?.let { return it }
        return null
    }

    /** Rebuilds every Animator in the open scenes whose controller is [file] (edit and play mode alike, Q5). */
    fun reload(engine: Engine, projectDir: File?, file: File): Int {
        val target = file.absoluteFile.normalize()
        val assets = engine.assetsManager
        var count = 0
        fun walk(obj: GameObject) {
            obj.getComponent<Animator>()?.let { a ->
                val path = a.controllerPath
                if (path != null && fileFor(path, projectDir)?.absoluteFile?.normalize() == target) {
                    a.resolveController(
                        engine.logger,
                        read = { p -> assets.readText(p)?.ifEmpty { null } },
                        loadClip = { p -> assets.loadAnimationSync(p) },
                    )
                    count++
                }
            }
            obj.children.forEach(::walk)
        }
        engine.sceneManager.openScenes.forEach { scene -> scene.children.forEach(::walk) }
        return count
    }
}

/**
 * The project's `.skanim` clips for the clip picker, by the name stored in each file. Files are re-read only when
 * they change and the folder is scanned at most every [rescanMillis].
 */
class ProjectClipScanner(private val rescanMillis: Long = 2000L, private val projectDir: () -> File?) {
    private val names = HashMap<String, Pair<Long, String>>()
    private var cached: List<ProjectClip> = emptyList()
    private var scannedAt = 0L
    private var scannedRoot: File? = null

    fun clips(now: Long = System.currentTimeMillis()): List<ProjectClip> {
        val root = projectDir()
        if (root == scannedRoot && now - scannedAt < rescanMillis) return cached
        scannedRoot = root
        scannedAt = now
        cached = root?.let { scan(it) } ?: emptyList()
        return cached
    }

    private fun scan(root: File): List<ProjectClip> {
        val assets = File(root, "Assets").takeIf { it.isDirectory } ?: return emptyList()
        return assets.walkTopDown()
            .filter { it.isFile && SkAnimFormat.isSkAnim(it.path) }
            .mapNotNull { f ->
                val key = f.absolutePath
                val name = names[key]?.takeIf { it.first == f.lastModified() }?.second
                    ?: runCatching { SkAnimFormat.decode(f.readText()).name }.getOrNull()?.also { names[key] = f.lastModified() to it }
                name?.let { ProjectClip(it, AssetPaths.relativize(f.absolutePath, root)) }
            }
            .sortedBy { it.name.lowercase() }
            .toList()
    }
}
