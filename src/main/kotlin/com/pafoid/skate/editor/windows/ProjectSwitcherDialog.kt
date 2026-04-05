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

/**
 * Project switcher dialog for opening recent projects or creating new ones.
 * 
 * Shows:
 * - List of recent projects (up to 5)
 * - "New Project" button
 * - "Open Project" button
 */
class ProjectSwitcherDialog(
    private val projectWizard: com.pafoid.skate.game.project.ProjectWizard
) : KoinComponent {
    
    private val projectManager: ProjectManager by inject()
    private val logger: LoggerService by inject()
    private val stringManager: StringManager by inject()
    
    private var isOpen = false
    
    /**
     * Open the project switcher dialog.
     */
    fun open() {
        isOpen = true
    }
    
    /**
     * Close the project switcher dialog.
     */
    fun close() {
        isOpen = false
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
                "Switch Project",
                ImBoolean(true),
                ImGuiWindowFlags.NoResize or ImGuiWindowFlags.Modal
            )
        ) {
            ImGui.text("Select a recent project or create a new one:")
            ImGui.spacing()
            
            // Recent projects list
            val recentProjects = getRecentProjectsDisplayInfo()
            if (recentProjects.isNotEmpty()) {
                ImGui.text("Recent Projects:")
                ImGui.spacing()
                
                for (project in recentProjects) {
                    renderRecentProjectItem(project)
                }
            } else {
                MImGui.textDisabled("No recent projects")
            }
            
            ImGui.separator()
            ImGui.spacing()
            
            // Action buttons
            val buttonHeight = UiConstants.DEFAULT_BUTTON_HEIGHT
            val buttonWidth = 150f
            val spacing = UiConstants.SECTION_SPACING
            val totalWidth = (2 * buttonWidth) + spacing
            
            val contentRegionWidth = ImGui.getContentRegionAvailX()
            ImGui.setCursorPosX(ImGui.getCursorPosX() + contentRegionWidth - totalWidth)
            
            if (ImGui.button("${Icons.PLUS} New Project", buttonWidth, buttonHeight)) {
                close()
                projectWizard.open()
            }
            
            ImGui.sameLine(0f, spacing)
            
            if (ImGui.button("${Icons.FOLDER_OPEN} Open Project", buttonWidth, buttonHeight)) {
                openProjectDialog()
            }
            
            ImGui.end()
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
        ImGui.textColored(0.4f, 0.4f, 0.4f, 1f, "Last opened: ${project.getLastOpenedString()}")
        
        if (!isClickable) {
            ImGui.popStyleColor()
            MImGui.errorText("Project not found")
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
            fileChooser.dialogTitle = "Open Project"
            fileChooser.addChoosableFileFilter(object : javax.swing.filechooser.FileFilter() {
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
                openProject(projectFile)
            }
        } catch (e: Exception) {
            logger.logEditor("Error opening project dialog: ${e.message}", LogLevel.ERROR)
        }
    }
}
