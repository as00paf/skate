package com.pafoid.skate.editor.project

import com.pafoid.skate.engine.utils.ExportArt
import com.pafoid.skate.engine.utils.ExportNotice
import com.pafoid.skate.engine.utils.ExportOptions
import com.pafoid.skate.engine.utils.PackEstimate
import com.pafoid.skate.engine.utils.ProjectExporter
import java.io.File

/**
 * The export dialog's Summary (EDT-91, AST-15 §3): asset count, estimated size, the icon and splash that will ship
 * and the warnings, computed on a worker whenever an input changes (the files are read there, never on the main
 * thread). [runAsync] runs the computation off the main thread and [runOnMain] publishes it back; no ImGui here.
 */
class ExportSummary(
    private val runAsync: (() -> Unit) -> Unit,
    private val runOnMain: (() -> Unit) -> Unit,
    private val compute: (Project, ExportOptions) -> Pair<ExportArt, PackEstimate> = { p, o ->
        val exporter = ProjectExporter(com.pafoid.skate.engine.core.LoggerService())
        exporter.art(p) to exporter.estimate(p, o)
    },
) {
    /** One computed summary; [staleJar] is the game jar when it is older than the editor build. */
    data class Snapshot(val art: ExportArt, val estimate: PackEstimate, val warnings: List<ExportNotice>)

    /** What the summary depends on: a different key starts a new computation. */
    private data class Key(
        val projectPath: String, val iconPath: String, val splashPath: String, val splashSeconds: Float,
        val physicsBackend: String, val options: ExportOptions, val staleJar: String?,
    )

    @Volatile
    var snapshot: Snapshot? = null
        private set

    /** True while a computation for the current inputs runs. */
    @Volatile
    var computing: Boolean = false
        private set

    private var requested: Key? = null
    private var running: Key? = null

    /** Called every frame: starts a computation when [project] or [options] changed since the last one. */
    fun refresh(project: Project, options: ExportOptions, staleJar: File?) {
        val key = Key(project.projectPath, project.iconPath, project.splashPath, project.splashSeconds,
            project.physicsBackend.name, options, staleJar?.path)
        if (key == requested) return
        requested = key
        if (running != null) return // the running job checks [requested] when it finishes
        start(project, options, staleJar, key)
    }

    /** Forgets the summary (another project, or after an export). */
    fun clear() {
        snapshot = null
        requested = null
    }

    private fun start(project: Project, options: ExportOptions, staleJar: File?, key: Key) {
        running = key
        computing = true
        val copy = project.copy()
        runAsync {
            val result = runCatching { compute(copy, options) }.getOrNull()
            runOnMain {
                running = null
                computing = false
                if (result != null && requested == key) {
                    snapshot = Snapshot(result.first, result.second, ProjectExporter.warnings(copy, result.first, staleJar))
                }
                // Inputs changed while computing: the next refresh starts again (requested != key).
                if (requested != key) requested = null
            }
        }
    }
}
