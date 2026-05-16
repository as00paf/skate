package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.data.LogLevel
import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.data.UiConstants
import com.pafoid.skate.editor.project.ItemType
import com.pafoid.skate.editor.project.ProjectWizard
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.BuildConfig
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager
import javax.swing.filechooser.FileFilter

/**
 * Project creation dialog window.
 *
 * Shows a single-screen form with all project options:
 * - Project name with live validation
 * - Project location with folder browser
 * - Live preview of project structure
 */
class ProjectWizardWindow : IWindow, KoinComponent {

    private val projectManager: ProjectManager by inject()
    val wizard: ProjectWizard by inject()
    private val logger: LoggerService by inject()
    private val stringManager: StringManager by inject()

    private val projectNameInput = ImString(128)
    private val projectPathInput = ImString(512)

    /**
     * Render the project creation dialog.
     */
    override fun imgui(pOpen: ImBoolean?) {
        if (!wizard.isOpen.get()) return

        val viewport = ImGui.getMainViewport()
        val centerX = if (viewport.sizeX > 0) viewport.centerX else 400f
        val centerY = if (viewport.sizeY > 0) viewport.centerY else 300f

        ImGui.setNextWindowPos(centerX, centerY, ImGuiCond.FirstUseEver, 0.5f, 0.5f)
        ImGui.setNextWindowSize(UiConstants.DIALOG_WIDTH, UiConstants.DIALOG_HEIGHT)
        ImGui.setNextWindowBgAlpha(0.95f)

        val isOpen = ImGui.begin(
            stringManager.getString("wizard.project.title"),
            wizard.isOpen,
            ImGuiWindowFlags.NoResize or ImGuiWindowFlags.Modal
        )

        if (isOpen) {
            renderForm()
            renderFooter()

            ImGui.end()
        }

        // Detect if user closed the window via the X button
        if (!wizard.isOpen.get()) {
            wizard.dismiss()
        }
    }

    /**
     * Render the main form with all inputs.
     */
    private fun renderForm() {
        ImGui.spacing()

        // Project Name
        ImGui.text(stringManager.getString("wizard.project.name"))
        ImGui.spacing()
        ImGui.pushItemWidth(450f)
        val nameFlags = ImGuiInputTextFlags.AutoSelectAll
        ImGui.inputText("##ProjectName", projectNameInput, nameFlags)
        ImGui.popItemWidth()

        // Update wizard state and show validation
        wizard.setProjectName(projectNameInput.get())
        val currentName = projectNameInput.get()
        if (currentName.isNotBlank()) {
            if (!wizard.isProjectNameValid()) {
                MImGui.errorText("${Icons.WINDOW_CLOSE} ${stringManager.getString("wizard.project.invalid_chars")}")
            } else {
                MImGui.successText("${Icons.CHECK} Valid")
            }
        }

        ImGui.spacing()
        ImGui.spacing()

        // Project Location
        ImGui.text(stringManager.getString("wizard.project.location"))
        ImGui.spacing()
        ImGui.pushItemWidth(350f)
        ImGui.inputText("##ProjectPath", projectPathInput, ImGuiInputTextFlags.None)
        ImGui.popItemWidth()

        ImGui.sameLine()
        if (ImGui.button(stringManager.getString("wizard.project.browse"))) {
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

        // Project Structure Preview
        renderProjectStructurePreview()
    }

    /**
     * Render the project structure preview in a boxed panel.
     */
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

    /**
     * Render footer with action buttons.
     */
    private fun renderFooter() {
        val canCreate = wizard.canCreate()

        // Separator before footer
        ImGui.separator()
        ImGui.spacing()

        // Calculate button positions (right-aligned)
        val cancelButtonWidth = 110f
        val openButtonWidth = 150f
        val createButtonWidth = 150f
        val buttonSpacing = 12f
        val buttonHeight = 30f
        val totalButtonWidth = cancelButtonWidth + openButtonWidth + createButtonWidth + buttonSpacing * 2
        val bottomPadding = 12f

        // Calculate Y position to place buttons at the bottom
        val availableHeight = ImGui.getContentRegionAvailY()
        val buttonYPos = if (availableHeight > buttonHeight + bottomPadding) {
            availableHeight - buttonHeight - bottomPadding
        } else {
            0f
        }

        // Move cursor to bottom position
        val currentPos = ImGui.getCursorPos()
        ImGui.setCursorPos(currentPos.x, currentPos.y + buttonYPos)

        // Push buttons to the right side
        val availableWidth = ImGui.getContentRegionAvailX()
        val rightPadding = 15f
        val xPos = availableWidth - totalButtonWidth - rightPadding

        ImGui.setCursorPosX(ImGui.getCursorPosX() + xPos)

        // Cancel button
        if (ImGui.button(stringManager.getString("wizard.project.cancel"), cancelButtonWidth, buttonHeight)) {
            wizard.dismiss()
            wizard.reset()
            projectNameInput.set("")
            projectPathInput.set("")
        }

        ImGui.sameLine(0f, buttonSpacing)

        // Open existing project button
        if (ImGui.button("${Icons.FOLDER_OPEN} Open Project", openButtonWidth, buttonHeight)) {
            openExistingProjectDialog()
        }

        ImGui.sameLine(0f, buttonSpacing)

        // Create button (highlighted)
        ImGui.beginDisabled(!canCreate)
        if (canCreate) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.25f, 0.7f, 0.25f, 1f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.15f, 0.5f, 0.15f, 1f)
        }
        if (ImGui.button("${Icons.PLUS} ${stringManager.getString("wizard.project.create")}", createButtonWidth, buttonHeight)) {
            createProject()
        }
        if (canCreate) {
            ImGui.popStyleColor(3)
        }
        ImGui.endDisabled()
    }

    /**
     * Open file chooser dialog to select an existing .skateproject file.
     */
    private fun openExistingProjectDialog() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())

            val fileChooser = JFileChooser()
            fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
            fileChooser.dialogTitle = "Open Existing Project"
            fileChooser.addChoosableFileFilter(object : FileFilter() {
                override fun accept(file: File): Boolean {
                    return file.isDirectory || file.extension == "skateproject"
                }
                override fun getDescription(): String {
                    return "SkateSim Projects (*.skateproject)"
                }
            })

            val result = fileChooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                val projectFile = fileChooser.selectedFile
                logger.logEditor("Opening project: ${projectFile.absolutePath}")

                val success = projectManager.openProject(projectFile)

                if (success) {
                    logger.logEditor("Project opened: ${projectManager.getProjectName()}")
                } else {
                    logger.logEngine("Failed to open project: ${projectFile.absolutePath}", LogLevel.ERROR)
                }
            }
        } catch (e: Exception) {
            logger.logEditor("Error opening project dialog: ${e.message}", LogLevel.ERROR)
        }
    }

    /**
     * Browse for folder using system file chooser.
     */
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

    /**
     * Create the project with current dialog settings.
     */
    private fun createProject() {
        val name = wizard.projectName
        val path = File(wizard.projectPath)

        logger.logEditor("Creating project: $name at ${path.absolutePath}")

        val result = projectManager.createProject(name, path, BuildConfig.ENGINE_VERSION)

        result.onSuccess { project ->
            logger.logEditor("Project created successfully: ${project.metadata.name}")
        }

        result.onFailure { error ->
            logger.logEngine("Failed to create project: ${error.message}", LogLevel.ERROR)
        }
    }
}
