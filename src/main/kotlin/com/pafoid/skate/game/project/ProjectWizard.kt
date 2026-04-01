package com.pafoid.skate.game.project

import com.pafoid.skate.engine.settings.ProjectSettings
import java.io.File

/**
 * Project wizard logic and state management.
 * 
 * This class handles the multi-step project creation wizard:
 * - Step 0: Welcome & Project Name
 * - Step 1: Project Location
 * - Step 2: Project Settings (resolution, quality)
 * - Step 3: Summary & Create
 * 
 * ## Usage
 * 
 * ```kotlin
 * val wizard = ProjectWizard()
 * 
 * // Set project name
 * wizard.setProjectName("MyProject")
 * 
 * // Set project location
 * wizard.setProjectLocation("/path/to/folder")
 * 
 * // Validate current step
 * if (wizard.isCurrentStepValid()) {
 *     wizard.nextStep()
 * }
 * 
 * // Create project when on final step
 * if (wizard.currentStep == 3) {
 *     val result = projectManager.createProject(
 *         wizard.projectName,
 *         File(wizard.projectPath)
 *     )
 * }
 * ```
 */
class ProjectWizard {
    
    /**
     * Whether the wizard is currently open.
     */
    val isOpen = imgui.type.ImBoolean(false)
    
    /**
     * Current wizard step (0-3).
     */
    var currentStep: Int = 0
        private set
    
    /**
     * Total number of wizard steps.
     */
    val totalSteps: Int = 4
    
    /**
     * Project name entered by user.
     */
    var projectName: String = ""
        private set
    
    /**
     * Project location/folder selected by user.
     */
    var projectPath: String = ""
        private set
    
    /**
     * Selected default resolution index.
     */
    var selectedResolution: Int = 0  // 0=1920x1080, 1=1280x720, 2=2560x1440
        private set
    
    /**
     * Selected graphics quality index.
     */
    var selectedQuality: Int = 2  // 0=LOW, 1=MEDIUM, 2=HIGH, 3=ULTRA
        private set
    
    /**
     * Available resolution options.
     */
    val resolutionOptions: List<String> = listOf(
        "1920 x 1080 (Full HD)",
        "1280 x 720 (HD)",
        "2560 x 1440 (2K)"
    )
    
    /**
     * Available graphics quality options.
     */
    val qualityOptions: List<String> = listOf(
        "Low",
        "Medium",
        "High",
        "Ultra"
    )
    
    /**
     * Set the project name.
     */
    fun setProjectName(name: String) {
        projectName = name.trim()
    }
    
    /**
     * Set the project location/folder.
     */
    fun setProjectLocation(path: String) {
        projectPath = path.trim()
    }
    
    /**
     * Set the selected resolution.
     */
    fun setSelectedResolution(index: Int) {
        selectedResolution = index.coerceIn(0, resolutionOptions.size - 1)
    }
    
    /**
     * Set the selected graphics quality.
     */
    fun setSelectedQuality(index: Int) {
        selectedQuality = index.coerceIn(0, qualityOptions.size - 1)
    }
    
    /**
     * Go to next wizard step.
     * 
     * @return true if moved to next step, false if already on last step
     */
    fun nextStep(): Boolean {
        if (currentStep < totalSteps - 1) {
            currentStep++
            return true
        }
        return false
    }
    
    /**
     * Go to previous wizard step.
     * 
     * @return true if moved to previous step, false if already on first step
     */
    fun previousStep(): Boolean {
        if (currentStep > 0) {
            currentStep--
            return true
        }
        return false
    }
    
    /**
     * Check if current step data is valid.
     */
    fun isCurrentStepValid(): Boolean {
        return when (currentStep) {
            0 -> isProjectNameValid()
            1 -> isProjectPathValid()
            2 -> true  // Settings step always valid (has defaults)
            3 -> isProjectNameValid() && isProjectPathValid()
            else -> false
        }
    }
    
    /**
     * Check if project name is valid.
     * Must be non-empty and contain only valid characters.
     */
    fun isProjectNameValid(): Boolean {
        if (projectName.isBlank()) return false
        
        // Check for invalid characters
        val invalidChars = Regex("[<>:\"/\\\\|?*]")
        return !invalidChars.containsMatchIn(projectName)
    }
    
    /**
     * Check if project path is valid.
     * Must exist and be writable.
     */
    fun isProjectPathValid(): Boolean {
        if (projectPath.isBlank()) return false
        
        val folder = File(projectPath)
        return folder.exists() && folder.isDirectory && folder.canWrite()
    }
    
    /**
     * Get the full project file path.
     */
    fun getProjectFilePath(): String {
        return File(projectPath, "$projectName.skateproject").absolutePath
    }
    
    /**
     * Get the project directory.
     */
    fun getProjectDirectory(): File {
        return File(projectPath, projectName)
    }
    
    /**
     * Reset wizard to initial state.
     */
    fun reset() {
        currentStep = 0
        projectName = ""
        projectPath = ""
        selectedResolution = 0
        selectedQuality = 2
    }
    
    /**
     * Get step title for display.
     */
    fun getStepTitle(): String {
        return when (currentStep) {
            0 -> "Welcome & Project Name"
            1 -> "Project Location"
            2 -> "Project Settings"
            3 -> "Summary & Create"
            else -> "Project Wizard"
        }
    }
    
    /**
     * Get step description for display.
     */
    fun getStepDescription(): String {
        return when (currentStep) {
            0 -> "Enter a name for your new project"
            1 -> "Choose where to save your project"
            2 -> "Configure default resolution and graphics quality"
            3 -> "Review your settings and create the project"
            else -> ""
        }
    }
    
    /**
     * Get summary text for final step.
     */
    fun getSummaryText(): String {
        return buildString {
            appendLine("Project Name: $projectName")
            appendLine("Location: $projectPath")
            appendLine("Resolution: ${resolutionOptions[selectedResolution]}")
            appendLine("Graphics Quality: ${qualityOptions[selectedQuality]}")
            appendLine("")
            appendLine("Project Structure:")
            appendLine("  - Assets/")
            appendLine("  - Scenes/")
            appendLine("  - Builds/")
            appendLine("  - $projectName.skateproject")
        }
    }
}
