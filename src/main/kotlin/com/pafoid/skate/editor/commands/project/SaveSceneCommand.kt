package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ReportsFailure
import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import java.io.File

class SaveSceneCommand(
    private val scene: Scene,
    private val projectManager: ProjectManager,
    private val sceneManager: SceneManager
) : ExecuteOnlyCommand, ReportsFailure {
    override val errorTitleKey = "error.save_scene.title"
    private var failure: String? = null

    override fun execute() {
        val project = projectManager.currentProject
        if (project == null) {
            failure = "No project is open."
            return
        }
        val dir = File(project.getProjectDirectory(), "Scenes")
        failure = if (sceneManager.saveScene(scene, dir.absolutePath)) null else File(dir, "${scene.name}.scene").absolutePath
    }

    override fun wasSuccessful(): Boolean = failure == null
    override fun getFailureReason(): String? = failure

    override fun undo() {
        // Save operations are not reversible
    }

    override fun getDisplayName(): String = "Save Scene"
    override fun getTargetName(): String? = scene.name
}
