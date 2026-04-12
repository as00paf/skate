package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.data.LogLevel
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.data.UiConstants
import com.pafoid.skate.editor.project.ProjectManager
import com.pafoid.skate.editor.project.ProjectWizard
import com.pafoid.skate.editor.project.RecentProjectDisplayInfo
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.StringManager
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiMouseCursor
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager
import javax.swing.filechooser.FileFilter

class ProjectSwitcherDialog : KoinComponent {

    private val projectManager: ProjectManager by inject()
    private val wizard: ProjectWizard by inject()
    private val logger: LoggerService by inject()
    private val stringManager: StringManager by inject()

    private val windowOpen = ImBoolean(false)
    private var lastOpenFailed = false
    private var lastOpenErrorMessage = ""

    val isOpen: Boolean get() = windowOpen.get()

    fun open() {
        lastOpenFailed = false
        lastOpenErrorMessage = ""
        windowOpen.set(true)
    }

    fun close() {
        windowOpen.set(false)
    }

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

            if (lastOpenFailed) {
                MImGui.errorText("${Icons.WINDOW_CLOSE} $lastOpenErrorMessage")
                ImGui.spacing()
            }

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

        if (!windowOpen.get()) {
            close()
        }
    }

    private fun renderRecentProjectItem(project: RecentProjectDisplayInfo) {
        ImGui.pushID(project.path)

        val isClickable = project.exists

        if (!isClickable) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1f)
        }

        ImGui.textColored(
            if (isClickable) 0.3f else 0.5f,
            if (isClickable) 0.6f else 0.5f,
            if (isClickable) 0.9f else 0.5f,
            1f,
            project.name
        )

        ImGui.textColored(0.5f, 0.5f, 0.5f, 1f, project.path)

        ImGui.textColored(0.4f, 0.4f, 0.4f, 1f, "${stringManager.getString("lbl.switch_project.last_opened").format(project.getLastOpenedString())}")

        if (!isClickable) {
            ImGui.popStyleColor()
            MImGui.errorText(stringManager.getString("lbl.switch_project.not_found"))
        } else {
            if (ImGui.isItemHovered()) {
                ImGui.setMouseCursor(ImGuiMouseCursor.Hand)
            }

            if (ImGui.isItemClicked()) {
                openProject(File(project.path))
            }
        }

        ImGui.separator()
        ImGui.popID()
    }

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

    private fun openProject(projectFile: File) {
        logger.logEditor("Opening project: ${projectFile.absolutePath}")

        val success = projectManager.openProject(projectFile)

        if (success) {
            logger.logEditor("Project opened: ${projectManager.getProjectName()}")
            lastOpenFailed = false
            close()
        } else {
            lastOpenFailed = true
            lastOpenErrorMessage = stringManager.getString("lbl.switch_project.open_failed").replace("%s", projectFile.name)
            logger.logEngine("Failed to open project: ${projectFile.absolutePath}", LogLevel.ERROR)
        }
    }

    private fun openProjectDialog() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())

            val fileChooser = JFileChooser()
            fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
            fileChooser.dialogTitle = stringManager.getString("dialog.open_project")
            fileChooser.addChoosableFileFilter(object : FileFilter() {
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
