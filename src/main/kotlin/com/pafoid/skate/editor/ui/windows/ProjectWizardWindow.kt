package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.GraphicsBackendField
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.systems.EditorPaths
import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.editor.events.ProjectEvent.CreateProjectRequested
import com.pafoid.skate.editor.events.ProjectEvent.OpenProjectRequested
import com.pafoid.skate.editor.imgui.EditorModal
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.FooterButton
import com.pafoid.skate.editor.imgui.WindowHeader
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.data.UiConstants
import com.pafoid.skate.editor.project.ItemType
import com.pafoid.skate.editor.project.ProjectWizard
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager
import javax.swing.filechooser.FileFilter

class ProjectWizardWindow(
    engine: Engine,
) : EditorWindow("window.project_wizard") {

    private val logger = engine.logger
    private val stringManager = engine.stringManager
    private val eventSystem = engine.eventSystem

    val wizard: ProjectWizard = ProjectWizard()

    private val projectNameInput = ImString(128)
    private val projectPathInput = ImString(512)

    override val icon: String = Icons.PLUS

    override fun imgui() = modal.render()

    /**
     * A real modal (EDT-51 §3), kept minimal until the EDT-59 rewrite: the form, then the standard footer
     * [Open Project] [Cancel] [Create Project]. Esc = Cancel, Enter = Create. Its open state is the window's.
     */
    private val modal = object : EditorModal(stringManager, Icons.PLUS, minWidth = UiConstants.DIALOG_WIDTH) {
        override val titleKey = "wizard.project.title"
        override val wantsOpen: Boolean get() = isOpen.get()
        override val helpWindow: EditorWindow get() = this@ProjectWizardWindow

        override fun content() {
            // Hidden from outside this frame (the project was created or opened): close the popup with it.
            if (!isOpen.get()) {
                ImGui.closeCurrentPopup()
                return
            }
            WindowHeader.render(stringManager, this@ProjectWizardWindow)
            help.render(stringManager)
            renderForm()
        }

        override fun buttons() = listOf(
            FooterButton("wizard.project.open_existing", FooterButton.Role.SECONDARY, closes = false) {
                openExistingProjectDialog()
            },
            FooterButton("wizard.project.cancel", FooterButton.Role.CANCEL) {
                isOpen.set(false)
                wizard.reset()
                projectNameInput.set("")
                projectPathInput.set("")
            },
            // Stays open until the project exists: a successful create hides the wizard.
            FooterButton("wizard.project.create", FooterButton.Role.PRIMARY, enabled = wizard.canCreate(), closes = false) {
                createProject()
            },
        )
    }

    private fun renderForm() {
        ImGui.spacing()

        // Project Name
        ImGui.text(stringManager.getString("wizard.project.name"))
        ImGui.spacing()
        ImGui.pushItemWidth(450f)
        val nameFlags = ImGuiInputTextFlags.AutoSelectAll
        // EDT-43: the name used to be reset to "test" every frame, so typing had no effect.
        ImGui.inputText("##ProjectName", projectNameInput, nameFlags)
        ImGui.popItemWidth()

        // Update wizard state and show validation
        wizard.setProjectName(projectNameInput.get())
        val currentName = projectNameInput.get()
        if (currentName.isNotBlank()) {
            if (!wizard.isProjectNameValid()) {
                MImGui.errorText("${Icons.WINDOW_CLOSE} ${stringManager.getString("wizard.project.invalid_chars")}")
            } else {
                MImGui.successText("${Icons.CHECK} ${stringManager.getString("wizard.project.valid_name")}")
            }
        }

        ImGui.spacing()
        ImGui.spacing()

        // Project Location
        ImGui.text(stringManager.getString("wizard.project.location"))
        ImGui.spacing()
        ImGui.pushItemWidth(350f)
        // Default once, then the field is the user's (typing and Browse used to be overwritten every frame).
        if (projectPathInput.get().isBlank()) projectPathInput.set(defaultLocation())
        ImGui.inputText("##ProjectPath", projectPathInput, ImGuiInputTextFlags.None)
        ImGui.popItemWidth()

        ImGui.sameLine()
        if (stringManager.tipped(ImGui.button(stringManager.getString("wizard.project.browse")), "wizard.project.browse")) {
            browseForFolder()
        }

        // Update wizard state and show validation
        wizard.setProjectLocation(projectPathInput.get())
        val currentPath = projectPathInput.get()
        if (currentPath.isNotBlank()) {
            val folder = File(currentPath)
            if (!folder.exists()) {
                MImGui.errorText("${Icons.WINDOW_CLOSE} ${stringManager.getString("wizard.project.folder_not_exist")}")
            } else if (!folder.isDirectory) {
                MImGui.errorText("${Icons.WINDOW_CLOSE} ${stringManager.getString("wizard.project.not_directory")}")
            } else if (!folder.canWrite()) {
                MImGui.errorText("${Icons.WINDOW_CLOSE} ${stringManager.getString("wizard.project.not_writable")}")
            } else {
                MImGui.successText("${Icons.CHECK} ${stringManager.getString("wizard.project.valid_location")}")
            }
        }

        ImGui.spacing()
        ImGui.spacing()

        // Graphics backend of exported games (EDT-43); the editor itself stays on OpenGL.
        GraphicsBackendField.render(stringManager, wizard.graphicsBackend)?.let { wizard.graphicsBackend = it }

        ImGui.spacing()
        ImGui.spacing()

        // Project Structure Preview
        renderProjectStructurePreview()
    }

    private fun renderProjectStructurePreview() {
        // Draw boxed panel for project structure
        val structureItems = wizard.getProjectStructureItems()
        val panelPadding = 8f
        val itemHeight = 18f
        val headerHeight = 26f
        val panelHeight = headerHeight + (structureItems.size * itemHeight) + (panelPadding * 3)
        val panelWidth = ImGui.getContentRegionAvailX()

        // Draw panel background with border
        ImGui.pushStyleColor(ImGuiCol.Border, 0.35f, 0.35f, 0.35f, 1f)
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.1f, 0.1f, 0.1f, 0.6f)
        ImGui.beginChild("StructurePanel", panelWidth, panelHeight, true, ImGuiWindowFlags.None)

        // Panel header
        ImGui.pushStyleColor(ImGuiCol.Text, 0.85f, 0.85f, 0.85f, 1f)
        ImGui.text("${Icons.FOLDER} ${stringManager.getString("wizard.project.project_structure")}")
        ImGui.popStyleColor()
        ImGui.separator()
        ImGui.spacing()

        // Structure items with icons and colored text
        val projectName = wizard.projectName.ifBlank { stringManager.getString("wizard.project.default_project_name") }

        // Root folder
        ImGui.pushStyleColor(ImGuiCol.Text, 0.9f, 0.8f, 0.4f, 1f)
        ImGui.text("  ${Icons.FOLDER_OPEN} $projectName/")
        ImGui.popStyleColor()
        ImGui.indent()

        for (item in structureItems) {
            val (icon, textColor) = when (item.type) {
                ItemType.DIRECTORY ->
                    Icons.FOLDER to Triple(0.9f, 0.75f, 0.3f)
                ItemType.FILE ->
                    Icons.EDIT to Triple(0.5f, 0.8f, 0.95f)
            }

            ImGui.pushStyleColor(ImGuiCol.Text, textColor.first, textColor.second, textColor.third, 1f)
            ImGui.text("$icon ${item.name}")
            ImGui.popStyleColor()
        }

        ImGui.unindent()
        ImGui.endChild()
        ImGui.popStyleColor(2)
    }

    /** Default parent folder for new projects in the user's documents ([EditorPaths]); created so it validates. */
    private fun defaultLocation(): String =
        EditorPaths.defaultProjectsDir().also { it.mkdirs() }.absolutePath

    private fun openExistingProjectDialog() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())

            val fileChooser = JFileChooser()
            fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
            fileChooser.dialogTitle = stringManager.getString("wizard.project.open_existing_title")
            fileChooser.addChoosableFileFilter(object : FileFilter() {
                override fun accept(file: File): Boolean {
                    return file.isDirectory || file.extension in FileType.PROJECT_FILE.extensions
                }
                override fun getDescription(): String {
                    return "SkateSim Projects (*.skateproject)"
                }
            })

            val result = fileChooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                val projectFile = fileChooser.selectedFile
                logger.logEditor("Opening project: ${projectFile.absolutePath}")
                eventSystem.publish(OpenProjectRequested(projectFile.absolutePath))
            }
        } catch (e: Exception) {
            logger.logEditor("Error opening project dialog: ${e.message}", LogLevel.ERROR)
        }
    }

    private fun browseForFolder() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())

            val fileChooser = JFileChooser()
            fileChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            fileChooser.dialogTitle = stringManager.getString("dialog.select_project_location")

            val result = fileChooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                val folder = fileChooser.selectedFile
                projectPathInput.set(folder.absolutePath)
                wizard.setProjectLocation(folder.absolutePath)
            }
        } catch (e: Exception) {
            logger.logEditor("Error browsing for folder: ${e.message}")
        }
    }

    private fun createProject() {
        val name = wizard.projectName
        val path = File(wizard.projectPath)

        logger.logEditor("Creating project: $name at ${path.absolutePath}")
        eventSystem.publish(CreateProjectRequested(name, path.absolutePath, wizard.graphicsBackend))
    }
}
