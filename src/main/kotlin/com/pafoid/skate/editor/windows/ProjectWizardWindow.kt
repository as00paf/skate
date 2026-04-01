package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.LogLevel
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.BuildConfig
import com.pafoid.skate.game.project.ProjectManager
import com.pafoid.skate.game.project.ProjectWizard
import imgui.ImGui
import imgui.ImVec2
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

/**
 * Project wizard window for creating new projects.
 * 
 * Shows a multi-step wizard:
 * - Step 0: Welcome & Project Name
 * - Step 1: Project Location
 * - Step 2: Project Settings (resolution, quality)
 * - Step 3: Summary & Create
 */
class ProjectWizardWindow : IWindow, KoinComponent {
    
    private val projectManager: ProjectManager by inject()
    private val projectWizard: ProjectWizard by inject()
    private val logger: LoggerService by inject()
    private val stringManager: StringManager by inject()
    
    private val isOpen = ImBoolean(false)
    private val projectNameInput = ImString(128)
    private val projectPathInput = ImString(512)
    
    /**
     * Render the project wizard window.
     */
    override fun imgui(pOpen: ImBoolean?) {
        // Auto-open wizard if no project is loaded
        if (!projectManager.hasProject() && !projectWizard.isOpen.get()) {
            projectWizard.isOpen.set(true)
        }
    
        if (!projectWizard.isOpen.get()) return

        val viewport = ImGui.getMainViewport()
        val centerX = if (viewport.sizeX > 0) viewport.centerX else 400f
        val centerY = if (viewport.sizeY > 0) viewport.centerY else 300f

        // Only set position on first frame, then allow user to move it
        ImGui.setNextWindowPos(centerX, centerY, ImGuiCond.FirstUseEver, 0.5f, 0.5f)
        ImGui.setNextWindowSize(600f, 450f)
        ImGui.setNextWindowBgAlpha(0.95f)  // Ensure window has visible background

        val isOpen = ImGui.begin(
            stringManager.getString("wizard.project.title"),
            projectWizard.isOpen,
            ImGuiWindowFlags.NoResize or ImGuiWindowFlags.Modal
        )
        
        if (isOpen) {
            renderHeader()
            ImGui.separator()

            renderStepContent()

            ImGui.separator()
            renderFooter()

            ImGui.end()
        }

        // Close wizard if project was created
        if (projectManager.hasProject()) {
            projectWizard.isOpen.set(false)
        }
    }
    
    /**
     * Render wizard header with step indicator.
     */
    private fun renderHeader() {
        // Step indicator
        val totalSteps = projectWizard.totalSteps
        for (i in 0 until totalSteps) {
            ImGui.pushStyleColor(
                imgui.flag.ImGuiCol.PlotHistogram,
                if (i <= projectWizard.currentStep) 0.3f else 0.1f,
                if (i <= projectWizard.currentStep) 0.6f else 0.3f,
                if (i <= projectWizard.currentStep) 0.9f else 0.5f,
                1f
            )
            ImGui.progressBar(1f / totalSteps, 80f, 20f, "")
            ImGui.popStyleColor()
            if (i < totalSteps - 1) {
                ImGui.sameLine()
            }
        }
        
        // Step title
        ImGui.spacing()
        ImGui.textColored(0.3f, 0.6f, 0.9f, 1f, "Step ${projectWizard.currentStep + 1} of $totalSteps")
        ImGui.sameLine()
        ImGui.textColored(0.7f, 0.7f, 0.7f, 1f, "- ${projectWizard.getStepTitle()}")
        
        // Step description
        ImGui.textColored(0.5f, 0.5f, 0.5f, 1f, projectWizard.getStepDescription())
    }
    
    /**
     * Render current step content.
     */
    private fun renderStepContent() {
        ImGui.spacing()
        ImGui.spacing()
        
        when (projectWizard.currentStep) {
            0 -> renderStep0_Welcome()
            1 -> renderStep1_Location()
            2 -> renderStep2_Settings()
            3 -> renderStep3_Summary()
        }
    }
    
    /**
     * Step 0: Welcome & Project Name
     */
    private fun renderStep0_Welcome() {
        ImGui.text("Welcome to SkateSim Engine!")
        ImGui.spacing()
        ImGui.textWrapped("Let's create a new project. First, choose a name for your project.")
        ImGui.spacing()
        ImGui.spacing()
        
        ImGui.text("Project Name:")
        ImGui.sameLine()
        ImGui.pushItemWidth(300f)
        
        val flags = ImGuiInputTextFlags.EnterReturnsTrue or ImGuiInputTextFlags.AutoSelectAll
        if (ImGui.inputText("##ProjectName", projectNameInput, flags)) {
            // Enter pressed - validate and go to next step
            projectWizard.setProjectName(projectNameInput.get())
            if (projectWizard.isCurrentStepValid()) {
                projectWizard.nextStep()
            }
        }
        ImGui.popItemWidth()
        
        // Update wizard state
        projectWizard.setProjectName(projectNameInput.get())
        
        // Show validation error
        if (projectNameInput.get().isNotBlank() && !projectWizard.isProjectNameValid()) {
            ImGui.textColored(1f, 0.3f, 0.3f, 1f, "Invalid characters in project name")
        }
    }
    
    /**
     * Step 1: Project Location
     */
    private fun renderStep1_Location() {
        ImGui.text("Choose where to save your project:")
        ImGui.spacing()
        ImGui.spacing()
        
        ImGui.text("Project Location:")
        ImGui.sameLine()
        ImGui.pushItemWidth(400f)
        
        if (ImGui.inputText("##ProjectPath", projectPathInput, ImGuiInputTextFlags.None)) {
            projectWizard.setProjectLocation(projectPathInput.get())
        }
        ImGui.popItemWidth()
        
        ImGui.sameLine()
        if (ImGui.button("Browse...")) {
            browseForFolder()
        }
        
        // Update wizard state
        projectWizard.setProjectLocation(projectPathInput.get())
        
        // Show validation
        if (projectPathInput.get().isNotBlank()) {
            val folder = File(projectPathInput.get())
            if (!folder.exists()) {
                ImGui.textColored(1f, 0.3f, 0.3f, 1f, "Folder does not exist")
            } else if (!folder.canWrite()) {
                ImGui.textColored(1f, 0.3f, 0.3f, 1f, "Folder is not writable")
            } else {
                ImGui.textColored(0.3f, 0.9f, 0.3f, 1f, "✓ Valid location")
            }
        }
    }
    
    /**
     * Step 2: Project Settings
     */
    private fun renderStep2_Settings() {
        ImGui.text("Configure your project settings:")
        ImGui.spacing()
        ImGui.spacing()
        
        // Resolution
        ImGui.text("Default Resolution:")
        ImGui.sameLine()
        val currentRes = imgui.type.ImInt(projectWizard.selectedResolution)
        if (ImGui.combo("##Resolution", currentRes, projectWizard.resolutionOptions.toTypedArray())) {
            projectWizard.setSelectedResolution(currentRes.get())
        }

        ImGui.spacing()

        // Graphics Quality
        ImGui.text("Graphics Quality:")
        ImGui.sameLine()
        val currentQuality = imgui.type.ImInt(projectWizard.selectedQuality)
        if (ImGui.combo("##Quality", currentQuality, projectWizard.qualityOptions.toTypedArray())) {
            projectWizard.setSelectedQuality(currentQuality.get())
        }
        
        ImGui.spacing()
        ImGui.spacing()
        ImGui.textColored(0.5f, 0.5f, 0.5f, 1f, "These settings can be changed later in the Settings window.")
    }
    
    /**
     * Step 3: Summary & Create
     */
    private fun renderStep3_Summary() {
        ImGui.text("Review your settings and create the project:")
        ImGui.spacing()
        ImGui.spacing()
        
        // Summary text
        val summary = projectWizard.getSummaryText()
        val summaryLines = summary.split("\n")
        for (line in summaryLines) {
            ImGui.text(line)
        }
        
        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()
        
        // Engine version
        ImGui.textColored(0.5f, 0.5f, 0.5f, 1f, "Engine Version: ${BuildConfig.ENGINE_VERSION}")
    }
    
    /**
     * Render wizard footer with navigation buttons.
     */
    private fun renderFooter() {
        val isLastStep = projectWizard.currentStep == projectWizard.totalSteps - 1
        val isFirstStep = projectWizard.currentStep == 0
        val canProceed = projectWizard.isCurrentStepValid()
        
        // Calculate button positions
        val cancelButtonWidth = 100f
        val backButtonWidth = 100f
        val nextButtonWidth = if (isLastStep) 120f else 100f
        val spacing = 10f
        val totalWidth = cancelButtonWidth + backButtonWidth + nextButtonWidth + (2 * spacing)
        
        // Position cursor for right-aligned buttons
        val contentRegionWidth = ImGui.getContentRegionAvailX()
        ImGui.setCursorPosX(ImGui.getCursorPosX() + contentRegionWidth - totalWidth)
        
        // Cancel button
        if (ImGui.button("Cancel", cancelButtonWidth, 0f)) {
            projectWizard.isOpen.set(false)
            projectWizard.reset()
        }
        
        ImGui.sameLine(0f, spacing)
        
        // Back button
        ImGui.beginDisabled(isFirstStep)
        if (ImGui.button("Back", backButtonWidth, 0f)) {
            projectWizard.previousStep()
        }
        ImGui.endDisabled()
        
        ImGui.sameLine(0f, spacing)
        
        // Next/Create button
        ImGui.beginDisabled(!canProceed)
        val buttonText = if (isLastStep) "${Icons.PLUS} Create" else "Next >"
        if (ImGui.button(buttonText, nextButtonWidth, 0f)) {
            if (isLastStep) {
                createProject()
            } else {
                projectWizard.nextStep()
            }
        }
        ImGui.endDisabled()
    }
    
    /**
     * Browse for folder using system file chooser.
     */
    private fun browseForFolder() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            
            val fileChooser = JFileChooser()
            fileChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            fileChooser.dialogTitle = "Select Project Location"
            
            val result = fileChooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                val folder = fileChooser.selectedFile
                projectPathInput.set(folder.absolutePath)
                projectWizard.setProjectLocation(folder.absolutePath)
            }
        } catch (e: Exception) {
            logger.logEditor("Error browsing for folder: ${e.message}")
        }
    }
    
    /**
     * Create the project with current wizard settings.
     */
    private fun createProject() {
        val name = projectWizard.projectName
        val path = File(projectWizard.projectPath)
        
        logger.logEditor("Creating project: $name at ${path.absolutePath}")
        
        val result = projectManager.createProject(name, path, BuildConfig.ENGINE_VERSION)
        
        result.onSuccess { project ->
            logger.logEditor("Project created successfully: ${project.metadata.name}")
            // Project will be loaded, scene will be initialized by ImGuiLayer
        }

        result.onFailure { error ->
            logger.logEngine("Failed to create project: ${error.message}", LogLevel.ERROR)
        }
    }
}
