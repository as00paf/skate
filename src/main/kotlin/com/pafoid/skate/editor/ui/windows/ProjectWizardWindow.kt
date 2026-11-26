package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.label
import imgui.type.ImBoolean
import imgui.type.ImInt
import imgui.flag.ImGuiSelectableFlags
import javax.swing.filechooser.FileNameExtensionFilter
import com.pafoid.skate.engine.physics3d.PhysicsBackend
import com.pafoid.skate.templates.NewProjectSettings
import com.pafoid.skate.templates.TemplateRegistry
import com.pafoid.skate.editor.imgui.GraphicsBackendField
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.systems.EditorPaths
import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.editor.events.ProjectEvent.CreateProjectRequested
import com.pafoid.skate.editor.events.ProjectEvent.OpenProjectRequested
import com.pafoid.skate.editor.imgui.EditorModal
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.FooterButton
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
    private val customFps = ImInt(60)

    override val icon: String = Icons.PLUS

    private companion object {
        const val TEMPLATES_WIDTH = 190f
        const val CARD_HEIGHT = 44f
    }

    override fun imgui() = modal.render()

    /**
     * A real modal (EDT-51 §3), the template cards, the form, then the standard footer
     * [Open Project] [Cancel] [Create Project]. Esc = Cancel, Enter = Create. Its open state is the window's.
     */
    private val modal = object : EditorModal(stringManager, Icons.PLUS, minWidth = UiConstants.DIALOG_WIDTH * 1.5f) {
        override val titleKey = "wizard.project.title"
        override val wantsOpen: Boolean get() = isOpen.get()
        override val helpWindow: EditorWindow get() = this@ProjectWizardWindow

        override fun content() {
            // Hidden from outside this frame (the project was created or opened): close the popup with it.
            if (!isOpen.get()) {
                ImGui.closeCurrentPopup()
                return
            }
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
                customFps.set(60)
            },
            // Stays open until the project exists: a successful create hides the wizard.
            FooterButton("wizard.project.create", FooterButton.Role.PRIMARY, enabled = wizard.canCreate(), closes = false) {
                createProject()
            },
        )
    }

    private fun renderForm() {
        val height = 430f
        ImGui.beginChild("##wizard_templates", TEMPLATES_WIDTH, height, true)
        renderTemplateCards()
        ImGui.endChild()
        ImGui.sameLine()
        ImGui.beginChild("##wizard_settings", 0f, height, false)
        renderNameAndLocation()
        ImGui.separator()
        renderSettings()
        ImGui.separator()
        ImGui.textWrapped(stringManager.getString(wizard.template.descriptionKey))
        ImGui.spacing()
        renderProjectStructurePreview()
        ImGui.endChild()
    }

    private fun renderTemplateCards() {
        ImGui.text(stringManager.getString("wizard.project.templates"))
        ImGui.separator()
        for (template in TemplateRegistry.all()) {
            val label = "${template.icon}  ${stringManager.getString(template.nameKey)}###template.${template.id}"
            if (ImGui.selectable(label, template.id == wizard.templateId, ImGuiSelectableFlags.None, 0f, CARD_HEIGHT)) {
                wizard.templateId = template.id
            }
            Tooltips.text(stringManager.getString(template.descriptionKey))
        }
    }

    private fun renderNameAndLocation() {
        ImGui.text(stringManager.getString("wizard.project.name"))
        ImGui.pushItemWidth(350f)
        // EDT-43: the name used to be reset to "test" every frame, so typing had no effect.
        ImGui.inputText("##ProjectName", projectNameInput, ImGuiInputTextFlags.AutoSelectAll)
        ImGui.popItemWidth()

        wizard.setProjectName(projectNameInput.get())
        if (projectNameInput.get().isNotBlank()) {
            if (!wizard.isProjectNameValid()) {
                MImGui.errorText("${Icons.WINDOW_CLOSE} ${stringManager.getString("wizard.project.invalid_chars")}")
            } else {
                MImGui.successText("${Icons.CHECK} ${stringManager.getString("wizard.project.valid_name")}")
            }
        }

        ImGui.text(stringManager.getString("wizard.project.location"))
        ImGui.pushItemWidth(270f)
        // Default once, then the field is the user's (typing and Browse used to be overwritten every frame).
        if (projectPathInput.get().isBlank()) projectPathInput.set(defaultLocation())
        ImGui.inputText("##ProjectPath", projectPathInput, ImGuiInputTextFlags.None)
        ImGui.popItemWidth()
        ImGui.sameLine()
        if (stringManager.tipped(ImGui.button(stringManager.getString("wizard.project.browse")), "wizard.project.browse")) {
            browseForFolder()
        }

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
            } else if (wizard.projectFileExists()) {
                MImGui.errorText("${Icons.WINDOW_CLOSE} ${stringManager.getString("wizard.project.exists", wizard.projectName)}")
            } else {
                MImGui.successText("${Icons.CHECK} ${stringManager.getString("wizard.project.valid_location")}")
                if (wizard.isTargetFolderNonEmpty()) MImGui.warningText(stringManager.getString("wizard.project.folder_not_empty"))
            }
        }
    }

    private fun renderSettings() {
        renderTargetFps()
        renderPhysicsBackend()
        // Graphics backend of exported games (EDT-43); the editor itself stays on OpenGL.
        GraphicsBackendField.render(stringManager, wizard.graphicsBackend)?.let { wizard.graphicsBackend = it }
        renderSplash()

        // EDT-70: copy the engine defaults or reference them (AST-26 §4.3); applied once, not stored.
        toggleSetting("wizard.project.copy_engine_defaults", wizard.copyEngineDefaults) { wizard.copyEngineDefaults = it }
        toggleSetting("wizard.project.ai_files", wizard.includeAiFiles) { wizard.includeAiFiles = it }
        toggleSetting("wizard.project.version_control", wizard.versionControl) { wizard.versionControl = it }
    }

    private fun toggleSetting(key: String, value: Boolean, onChange: (Boolean) -> Unit) {
        val box = ImBoolean(value)
        if (ImGui.checkbox(stringManager.label(key), box)) onChange(box.get())
        Tooltips.text(stringManager.getString("$key.tooltip"))
    }

    private fun renderTargetFps() {
        ImGui.text(stringManager.getString("wizard.project.target_fps"))
        Tooltips.text(stringManager.getString("wizard.project.target_fps.tooltip"))
        ImGui.sameLine()
        if (ImGui.radioButton(stringManager.label("wizard.project.target_fps.uncapped"), wizard.targetFps == 0)) wizard.targetFps = 0
        ImGui.sameLine()
        if (ImGui.radioButton(stringManager.label("lbl.export.target_fps.custom"), wizard.targetFps != 0)) {
            wizard.targetFps = customFps.get()
        }
        if (wizard.targetFps != 0) {
            ImGui.sameLine()
            ImGui.setNextItemWidth(90f)
            if (ImGui.inputInt("##wizard_fps", customFps, 0, 0)) wizard.targetFps = customFps.get()
            if (!wizard.isTargetFpsValid()) MImGui.errorText(stringManager.getString("wizard.project.fps_invalid"))
        }
    }

    private fun renderPhysicsBackend() {
        ImGui.setNextItemWidth(200f)
        if (ImGui.beginCombo(stringManager.label("lbl.export.physics_backend"), stringManager.getString(ExportWizardWindow.physicsKey(wizard.physicsBackend)))) {
            for (backend in PhysicsBackend.entries) {
                if (ImGui.selectable(stringManager.getString(ExportWizardWindow.physicsKey(backend)), backend == wizard.physicsBackend)) {
                    wizard.physicsBackend = backend
                }
            }
            ImGui.endCombo()
        }
        Tooltips.text(stringManager.getString("lbl.export.physics_backend.tooltip"))
        if (wizard.physicsBackend == PhysicsBackend.JOLT) MImGui.warningText(stringManager.getString("wizard.project.physics_backend.jolt_note"))
    }

    private fun renderSplash() {
        ImGui.text(stringManager.getString("wizard.project.splash"))
        Tooltips.text(stringManager.getString("wizard.project.splash.tooltip"))
        ImGui.sameLine()
        if (stringManager.tipped(ImGui.button(stringManager.label("wizard.project.splash_pick")), "wizard.project.splash_pick")) browseForSplash()
        ImGui.sameLine()
        ImGui.text(wizard.splashImage?.name ?: stringManager.getString("wizard.project.splash_default"))
        if (wizard.splashImage != null) {
            ImGui.sameLine()
            if (stringManager.tipped(ImGui.button(stringManager.label("wizard.project.splash_clear")), "wizard.project.splash_clear")) {
                wizard.splashImage = null
            }
            if (!wizard.isSplashValid()) MImGui.errorText(stringManager.getString("wizard.project.splash_invalid"))
        }
    }

    private fun browseForSplash() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            val fileChooser = JFileChooser()
            fileChooser.dialogTitle = stringManager.getString("wizard.project.splash")
            fileChooser.fileFilter = FileNameExtensionFilter("PNG, JPG", *NewProjectSettings.SPLASH_EXTENSIONS.toTypedArray())
            if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) wizard.splashImage = fileChooser.selectedFile
        } catch (e: Exception) {
            logger.logEditor("Error choosing the splash image: ${e.message}", LogLevel.ERROR)
        }
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
        eventSystem.publish(CreateProjectRequested(name, path.absolutePath, wizard.settings()))
    }
}
