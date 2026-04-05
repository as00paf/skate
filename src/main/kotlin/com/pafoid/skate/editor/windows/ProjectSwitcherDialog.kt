package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.data.UiConstants
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.LogLevel
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.game.project.ProjectManager
import com.pafoid.skate.game.project.RecentProjectDisplayInfo
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

class ProjectSwitcherDialog : KoinComponent {

    private val projectManager: ProjectManager by inject()
    private val wizard: com.pafoid.skate.game.project.ProjectWizard by inject()
    private val logger: LoggerService by inject()
    private val stringManager: StringManager by inject()

    private var isOpen = false
    private val windowOpen = ImBoolean(false)

    /**
     * Open the project switcher dialog.
     */
    fun open() {
        isOpen = true
        windowOpen.set(true)
    }

    /**
     * Close the project switcher dialog.
     */
    fun close() {
        isOpen = false
        windowOpen.set(false)
    }

    /**
     * Render the project switcher dialog.
     */
    fun render() {
        if (!isOpen) return

        val centerX = ImGui.getMainViewport().centerX
        val centerY = ImGui.getMainViewport().centerY

        ImGui.setNextWindowPos(centerX, centerY, ImGuiCond.Always, 0.5f, 0.5f)
        ImGui.setNextWindowSize(500f, 400f)

        if (ImGui.begin(
                stringManager.getString("window.switch_project"),
                windowOpen,
                ImGuiWindowFlags.NoResize or ImGuiWindowFlags.Modal
            )
        ) {
            ImGui.text(stringManager.getString("lbl.switch_project.select_or_create"))
            ImGui.spacing()

            // Recent projects list
            val recentProjects = getRecentProjectsDisplayInfo()
            if (recentProjects.isNotEmpty()) {
                ImGui.text(stringManager.getString("lbl.switch_project.recent"))
                ImGui.spacing()

                for (project in recentProjects) {
                    renderRecentProjectItem(project)
                }
            } else {
                MImGui.textDisabled(stringManager.getString("lbl.no_recent_projects"))
            }

            ImGui.separator()
            ImGui.spacing()

            // Action buttons
            val buttonHeight = UiConstants.DEFAULT_BUTTON_HEIGHT
            val newButtonWidth = 150f
            val openButtonWidth = 130f
            val cancelWidth = 100f
            val spacing = UiConstants.SECTION_SPACING
            val totalWidth = newButtonWidth + openButtonWidth + cancelWidth + (2 * spacing)

            val contentRegionWidth = ImGui.getContentRegionAvailX()
            ImGui.setCursorPosX(ImGui.getCursorPosX() + contentRegionWidth - totalWidth)

            if (ImGui.button("${Icons.PLUS} ${stringManager.getString("btn.new_project")}", newButtonWidth, buttonHeight)) {
                close()
                wizard.open()
            }

            ImGui.sameLine(0f, spacing)

            if (ImGui.button("${Icons.FOLDER_OPEN} ${stringManager.getString("btn.open_project")}", openButtonWidth, buttonHeight)) {
                openProjectDialog()
            }

            ImGui.sameLine(0f, spacing)

            if (ImGui.button(stringManager.getString("btn.cancel"), cancelWidth, buttonHeight)) {
                close()
            }

            ImGui.end()
        }

        // Detect if user closed the window via the X button
        if (!windowOpen.get()) {
            close()
        }
    }

    /**
     * Render a single recent project item.
     */
    private fun renderRecentProjectItem(project: RecentProjectDisplayInfo) {
        ImGui.pushID(project.path)
        
        val isClickable = project.exists
        
        if (!isClickable) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1f)
        }
        
        // Project name
        ImGui.textColored(
            if (isClickable) 0.3f else 0.5f,
            if (isClickable) 0.6f else 0.5f,
            if (isClickable) 0.9f else 0.5f,
            1f,
            project.name
        )
        
        // Project path
        ImGui.textColored(0.5f, 0.5f, 0.5f, 1f, project.path)
        
        // Last opened
        ImGui.textColored(0.4f, 0.4f, 0.4f, 1f, "${stringManager.getString("lbl.switch_project.last_opened").format(project.getLastOpenedString())}")

        if (!isClickable) {
            ImGui.popStyleColor()
            MImGui.errorText(stringManager.getString("lbl.switch_project.not_found"))
        } else {
            // Make clickable
            if (ImGui.isItemHovered()) {
                ImGui.setMouseCursor(imgui.flag.ImGuiMouseCursor.Hand)
            }
            
            if (ImGui.isItemClicked()) {
                openProject(File(project.path))
            }
        }
        
        ImGui.separator()
        ImGui.popID()
    }
    
    /**
     * Get recent projects with display info.
     */
    private fun getRecentProjectsDisplayInfo(): List<RecentProjectDisplayInfo> {
        return projectManager.getRecentProjects().map { recent ->
            val exists = File(recent.path).exists()
            RecentProjectDisplayInfo(
                name = recent.name,
                path = recent.path,
                lastOpened = recent.lastOpened,
                exists = exists
            )
        }
    }
    
    /**
     * Open a project from file.
     */
    private fun openProject(projectFile: File) {
        logger.logEditor("Opening project: ${projectFile.absolutePath}")
        
        val success = projectManager.openProject(projectFile)
        
        if (success) {
            logger.logEditor("Project opened: ${projectManager.getProjectName()}")
            close()
        } else {
            logger.logEngine("Failed to open project: ${projectFile.absolutePath}", LogLevel.ERROR)
        }
    }
    
    /**
     * Open file chooser dialog to select a project.
     */
    private fun openProjectDialog() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())

            val fileChooser = JFileChooser()
            fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
            fileChooser.dialogTitle = stringManager.getString("dialog.open_project")
            fileChooser.addChoosableFileFilter(object : javax.swing.filechooser.FileFilter() {
                override fun accept(file: File): Boolean {
                    return file.isDirectory || file.extension == "skateproject"
                }
                override fun getDescription(): String {
                    return stringManager.getString("dialog.skateproject_filter")
                }
            })

            val result = fileChooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                val projectFile = fileChooser.selectedFile
                openProject(projectFile)
            }
        } catch (e: Exception) {
            logger.logEditor("Error opening project dialog: ${e.message}", LogLevel.ERROR)
        }
    }
}
