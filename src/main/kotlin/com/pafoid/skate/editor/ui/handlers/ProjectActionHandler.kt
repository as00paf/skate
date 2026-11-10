package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.project.SetGraphicsBackendCommand
import com.pafoid.skate.editor.commands.project.SetRandomSeedCommand
import com.pafoid.skate.engine.ecs.random.RandomService
import com.pafoid.skate.editor.commands.project.SetProjectGraphicsQualityCommand
import com.pafoid.skate.editor.commands.project.SetProjectMsaaSamplesCommand
import com.pafoid.skate.editor.commands.project.SetProjectTextureQualityCommand
import com.pafoid.skate.editor.commands.project.SetProjectShadowQualityCommand
import com.pafoid.skate.editor.commands.ExecutionTrackedCommand
import com.pafoid.skate.editor.commands.project.CloseProjectCommand
import com.pafoid.skate.editor.commands.project.PhysicsRates
import com.pafoid.skate.editor.commands.project.SetDefaultSceneCommand
import com.pafoid.skate.editor.commands.project.SetGameLanguageCommand
import com.pafoid.skate.editor.commands.project.SetPhysicsRateCommand
import com.pafoid.skate.editor.commands.project.CreateFileCommand
import com.pafoid.skate.editor.commands.project.CreateProjectCommand
import com.pafoid.skate.editor.commands.project.DeleteFileCommand
import com.pafoid.skate.editor.commands.project.MoveFileCommand
import com.pafoid.skate.editor.commands.project.LoadLastProjectCommand
import com.pafoid.skate.editor.commands.project.OpenProjectFileCommand
import com.pafoid.skate.editor.commands.project.RenameFileCommand
import com.pafoid.skate.editor.commands.project.SaveProjectCommand
import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.editor.events.FileSystemEvent
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.events.ProjectEvent.CloseProjectRequested
import com.pafoid.skate.editor.events.ProjectEvent.CreateFileRequested
import com.pafoid.skate.editor.events.ProjectEvent.CreateProjectFailed
import com.pafoid.skate.editor.events.ProjectEvent.CreateProjectRequested
import com.pafoid.skate.editor.events.ProjectEvent.CreateProjectSucceeded
import com.pafoid.skate.editor.events.ProjectEvent.DeleteFileRequested
import com.pafoid.skate.editor.events.ProjectEvent.LoadLastProjectRequested
import com.pafoid.skate.editor.events.ProjectEvent.MoveFileRequested
import com.pafoid.skate.editor.events.ProjectEvent.OpenProjectFailed
import com.pafoid.skate.editor.events.ProjectEvent.OpenProjectRequested
import com.pafoid.skate.editor.events.ProjectEvent.OpenProjectSucceeded
import com.pafoid.skate.editor.events.ProjectEvent.RenameFileRequested
import com.pafoid.skate.editor.events.ProjectEvent.SaveRequested
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.loading.LoadingOperation
import com.pafoid.skate.editor.loading.LoadingRunner
import com.pafoid.skate.editor.loading.StepExecutor
import com.pafoid.skate.editor.project.ExportWizard
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.systems.PreparedProject
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.utils.ExportDetail
import com.pafoid.skate.engine.utils.ExportFailure
import com.pafoid.skate.engine.utils.ExportResult
import com.pafoid.skate.engine.utils.ExportStep
import com.pafoid.skate.engine.utils.ProjectExporter
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager
import javax.swing.filechooser.FileFilter

class ProjectActionHandler(
    private val engine: Engine,
    private val projectManager: ProjectManager,
    private val settingsManager: EditorSettingsManager,
    private val undoRedoManager: UndoRedoManager,
    private val stringManager: StringManager,
    /** Runs project create / open with the loading screen (EDT-56); shared with the scene handler. */
    private val loading: LoadingRunner = LoadingRunner(engine.eventSystem, StepExecutor.Jobs(engine.jobSystem)),
) {
    private val jobSystem = engine.jobSystem
    private val eventSystem = engine.eventSystem
    private val random by lazy { engine.random }
    private val logger = engine.logger

    /** True from an export request until its result is published (main thread only). */
    private var exportRunning = false

    init {
        eventSystem.subscribe<OpenProjectRequested> { event ->
            executeOnMainThread {
                val file = File(event.projectPath)
                openWithLoading(file, { OpenProjectFileCommand(projectManager, file, it) }) { opened ->
                    if (opened) {
                        eventSystem.publish(WindowAction.Hide("project_switcher"))
                        eventSystem.publish(WindowAction.Hide("project_wizard"))
                        eventSystem.publish(WindowAction.ShowDefault)
                        eventSystem.publish(OpenProjectSucceeded(event.projectPath))
                    } else {
                        eventSystem.publish(OpenProjectFailed(event.projectPath, projectManager.lastError ?: "Failed to open project"))
                    }
                }
            }
        }
        eventSystem.subscribe<CreateProjectRequested> { event ->
            executeOnMainThread { createWithLoading(event) }
        }
        eventSystem.subscribe<ProjectEvent.SetGraphicsBackendRequested> { event ->
            val project = projectManager.currentProject ?: return@subscribe
            val command = SetGraphicsBackendCommand(project, event.backend)
            if (!command.isNoOp) undoRedoManager.executeCommand(command)
        }
        eventSystem.subscribe<ProjectEvent.SetRandomSeedRequested> { event ->
            val project = projectManager.currentProject ?: return@subscribe
            val command = SetRandomSeedCommand(project, event.seed) { random.reseed(RandomService.resolve(it)) }
            if (!command.isNoOp) undoRedoManager.executeCommand(command)
        }
        eventSystem.subscribe<ProjectEvent.SetDefaultSceneRequested> { event ->
            val project = projectManager.currentProject ?: return@subscribe
            val command = SetDefaultSceneCommand(project, event.scenePath)
            if (!command.isNoOp) undoRedoManager.executeCommand(command)
        }
        eventSystem.subscribe<ProjectEvent.SetPhysicsRateRequested> { event ->
            val project = projectManager.currentProject ?: return@subscribe
            val command = SetPhysicsRateCommand(project, event.stepsPerSecond, ::applyPhysicsRate)
            if (!command.isNoOp) undoRedoManager.executeCommand(command)
        }
        eventSystem.subscribe<ProjectEvent.SetGameLanguageRequested> { event ->
            val project = projectManager.currentProject ?: return@subscribe
            val command = SetGameLanguageCommand(project.name, engine.gameLocale.strings.locale, event.locale, engine.gameLocale::set)
            if (!command.isNoOp) undoRedoManager.executeCommand(command)
        }
        // The project's physics rate drives the engine's fixed step while it is open (EDT-57).
        eventSystem.subscribe<ProjectEvent.Opened> { applyPhysicsRate(it.project.physicsFPS) }
        eventSystem.subscribe<ProjectEvent.Created> { applyPhysicsRate(it.project.physicsFPS) }
        eventSystem.subscribe<ProjectEvent.SetGraphicsQualityRequested> { event ->
            val project = projectManager.currentProject ?: return@subscribe
            val command = SetProjectGraphicsQualityCommand(project, event.quality)
            if (!command.isNoOp) undoRedoManager.executeCommand(command)
        }
        eventSystem.subscribe<ProjectEvent.SetMsaaSamplesRequested> { event ->
            val project = projectManager.currentProject ?: return@subscribe
            val command = SetProjectMsaaSamplesCommand(project, event.samples)
            if (!command.isNoOp) {
                undoRedoManager.executeCommand(command)
                settingsManager.onMsaaChanged?.invoke(event.samples)
            }
        }
        eventSystem.subscribe<ProjectEvent.SetTextureQualityRequested> { event ->
            val project = projectManager.currentProject ?: return@subscribe
            val command = SetProjectTextureQualityCommand(project, event.quality)
            if (!command.isNoOp) undoRedoManager.executeCommand(command)
        }
        eventSystem.subscribe<ProjectEvent.SetShadowQualityRequested> { event ->
            val project = projectManager.currentProject ?: return@subscribe
            val command = SetProjectShadowQualityCommand(project, event.quality)
            if (!command.isNoOp) undoRedoManager.executeCommand(command)
        }
        eventSystem.subscribe<ProjectEvent.SetFullscreenRequested> { event ->
            val project = projectManager.currentProject ?: return@subscribe
            project.fullscreen = event.enabled
        }
        eventSystem.subscribe<ProjectEvent.SetVsyncRequested> { event ->
            val project = projectManager.currentProject ?: return@subscribe
            project.vsync = event.enabled
        }
        eventSystem.subscribe<CreateFileRequested> { event ->
            val command = CreateFileCommand(event.path, event.isDirectory, logger)
            executeAndPublishOnSuccess(command, event.path, "create")
        }
        eventSystem.subscribe<RenameFileRequested> { event ->
            val command = RenameFileCommand(event.path, event.newName, logger, projectManager.assetRegistry())
            executeAndPublishOnSuccess(command, event.path, "rename")
        }
        eventSystem.subscribe<MoveFileRequested> { event ->
            val command = MoveFileCommand(event.path, event.targetDir, logger, projectManager.assetRegistry())
            executeAndPublishOnSuccess(command, event.path, "move")
        }
        eventSystem.subscribe<DeleteFileRequested> { event ->
            val command = DeleteFileCommand(event.path, logger, projectManager.assetRegistry())
            executeAndPublishOnSuccess(command, event.path, "delete")
        }
        eventSystem.subscribe<CloseProjectRequested> {
            executeOnMainThread {
                if (projectManager.hasProject()) {
                    val command = CloseProjectCommand(projectManager)
                    undoRedoManager.executeCommand(command)
                }
            }
        }
        eventSystem.subscribe<ProjectEvent.Closed> {
            eventSystem.publish(WindowAction.HideAll)
            eventSystem.publish(WindowAction.Show("window.project_wizard"))
        }
        eventSystem.subscribe<SaveRequested> {
            executeOnMainThread {
                val command = SaveProjectCommand(projectManager)
                undoRedoManager.executeCommand(command)
            }
        }
        eventSystem.subscribe<LoadLastProjectRequested> {
            executeOnMainThread {
                val file = settingsManager.lastProjectFile()
                if (file == null) {
                    undoRedoManager.executeCommand(LoadLastProjectCommand(projectManager, settingsManager))
                } else {
                    openWithLoading(file, { LoadLastProjectCommand(projectManager, settingsManager, it) }) {}
                }
            }
        }

        eventSystem.subscribe<ProjectEvent.OpenProjectFileRequested> { event ->
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())

                val fileChooser = JFileChooser()
                fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
                fileChooser.dialogTitle = stringManager.getString("dialog.open_project")
                fileChooser.addChoosableFileFilter(object : FileFilter() {
                    override fun accept(file: File): Boolean {
                        return file.isDirectory || file.extension in FileType.PROJECT_FILE.extensions
                    }

                    override fun getDescription(): String {
                        return stringManager.getString("dialog.skateproject_filter")
                    }
                })

                val result = fileChooser.showOpenDialog(null)
                if (result == JFileChooser.APPROVE_OPTION) {
                    val projectFile = fileChooser.selectedFile
                    eventSystem.publish(OpenProjectRequested(projectFile.absolutePath))
                }
            } catch (e: Exception) {
                logger.logEditor("Error opening project dialog: ${e.message}", LogLevel.ERROR)
            }
        }

        eventSystem.subscribe<ProjectEvent.ExportRequested> { event ->
            val project = projectManager.currentProject
            if (project == null) {
                // The wizard already shows "saving": always answer so it can leave the busy state.
                eventSystem.publish(ProjectEvent.ExportFinished(ExportResult(File(""), ExportFailure.IO_ERROR, ExportDetail.NO_PROJECT)))
                return@subscribe
            }
            if (exportRunning) return@subscribe // one export at a time into the same folder
            val outputDir = ProjectExporter.outputDir(project, event.options)
            // Save on the main thread so the scene is not serialized while it changes; pack and copy in the background.
            if (!projectManager.saveProject()) {
                eventSystem.publish(ProjectEvent.ExportFinished(ExportResult(outputDir, ExportFailure.SAVE_FAILED)))
                return@subscribe
            }
            exportRunning = true
            // Null while a project or scene is loading: the export still runs, the wizard shows its progress.
            val tracker = loading.track("loading.export.title", project.name)
            jobSystem.runIO {
                // Anything thrown (validation, I/O, OutOfMemoryError while packing) still ends the export.
                val result = try {
                    val locator = ExportWizard.gameJarLocator { settingsManager.editorSettings.gameJarPath }
                    ProjectExporter(logger, gameJarSearch = locator::locate).export(project, event.options) { progress ->
                        executeOnMainThread {
                            eventSystem.publish(ProjectEvent.ExportProgressed(progress))
                            tracker?.progress(progress.overall, exportStepKey(progress.step), progress.detail)
                        }
                    }
                } catch (t: Throwable) {
                    logger.logEditor("Export failed: ${t.message}", LogLevel.ERROR)
                    ExportResult(outputDir, ExportFailure.IO_ERROR, ExportDetail.IO_FAILURE, listOf(t.message ?: t.javaClass.simpleName))
                }
                executeOnMainThread {
                    exportRunning = false
                    tracker?.finish()
                    eventSystem.publish(ProjectEvent.ExportFinished(result))
                }
            }
        }
    }

    /**
     * Opens [file] behind the loading screen (EDT-56): the project file and its scenes are read in the background,
     * then [command] opens them on the main thread. [onResult] gets whether the project opened.
     */
    private fun openWithLoading(
        file: File,
        command: (PreparedProject?) -> ExecutionTrackedCommand,
        onResult: (Boolean) -> Unit,
    ) {
        var prepared: PreparedProject? = null
        var opened = false
        val operation = LoadingOperation.Builder("loading.open_project.title", file.nameWithoutExtension)
            .background("loading.step.read_project", weight = 2f) { prepared = projectManager.prepareProject(file) }
            .main("loading.step.open_scenes") {
                // Without a prepared project the command reads the file again and reports why it failed.
                val open = command(prepared)
                undoRedoManager.executeCommand(open)
                opened = open.wasSuccessful()
            }
            .build()
        val started = loading.run(operation, onDone = { onResult(opened) }, onFailed = { error ->
            logger.logEditor("Opening ${file.path} failed: ${error.message}", LogLevel.ERROR)
            onResult(false)
        })
        if (!started) logger.logEditor("Busy loading; ignored opening ${file.path}", LogLevel.WARN)
    }

    /**
     * Creates a project behind the loading screen (EDT-56): folders and the bundled assets are written in the
     * background, then the default scenes (which load textures) are made and opened on the main thread.
     */
    private fun createWithLoading(event: CreateProjectRequested) {
        var preparedDir: Result<File>? = null
        var command: CreateProjectCommand? = null
        val operation = LoadingOperation.Builder("loading.create_project.title", event.name)
            .background("loading.step.copy_assets", weight = 3f) {
                preparedDir = projectManager.prepareProjectFolder(event.name, File(event.folderPath))
            }
            .main("loading.step.create_scenes") {
                val dir = preparedDir?.getOrNull()
                // A failed folder step leaves dir null: the command retries it and reports the reason.
                command = CreateProjectCommand(event, projectManager, dir).also { undoRedoManager.executeCommand(it) }
            }
            .build()
        val started = loading.run(operation, onDone = { reportCreated(event, command) }, onFailed = { error ->
            logger.logEditor("Could not create project: ${error.message}", LogLevel.ERROR)
            reportCreated(event, null, error.message)
        })
        if (!started) logger.logEditor("Busy loading; ignored creating ${event.name}", LogLevel.WARN)
    }

    private fun reportCreated(event: CreateProjectRequested, command: CreateProjectCommand?, error: String? = null) {
        if (command?.wasSuccessful() == true) {
            eventSystem.publish(CreateProjectSucceeded(event.name, event.folderPath))
            eventSystem.publish(WindowAction.Hide("window.project_wizard"))
            eventSystem.publish(WindowAction.ShowDefault)
        } else {
            val reason = command?.getFailureReason() ?: error ?: "Failed to create project"
            eventSystem.publish(CreateProjectFailed(event.name, event.folderPath, reason))
        }
    }

    private fun executeAndPublishOnSuccess(command: ExecutionTrackedCommand, affectedPath: String, operation: String) {
        undoRedoManager.executeCommand(command)
        if (!command.wasSuccessful()) {
            eventSystem.publish(FileSystemEvent.FileSystemOperationFailed(
                path = affectedPath,
                operation = operation,
                reason = command.getFailureReason() ?: "Unknown error"
            ))
            return
        }
        eventSystem.publish(FileSystemEvent.FileSystemChangedEvent(affectedPath))
    }

    companion object {
        /** The loading screen's label of an export step. */
        fun exportStepKey(step: ExportStep): String = "loading.export.step.${step.name.lowercase()}"
    }

    private fun executeOnMainThread(block: () -> Unit) {
        val jobs = jobSystem
        if (jobs.isMainThread()) {
            block()
            return
        }
        jobs.runOnMain {
            block()
        }
    }

    private fun applyPhysicsRate(stepsPerSecond: Int) {
        engine.systemManager.fixedTimeStep = PhysicsRates.fixedStep(stepsPerSecond)
    }
}
