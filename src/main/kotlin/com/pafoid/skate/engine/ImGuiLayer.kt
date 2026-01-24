package com.pafoid.skate.engine

import com.pafoid.skate.engine.controls.KeyListener
import com.pafoid.skate.engine.controls.MouseListener
import com.pafoid.skate.engine.scenes.Scene
import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiConfigFlags
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import imgui.type.ImBoolean
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL11.*

class ImGuiLayer {

    private val imGuiGlfw = ImGuiImplGlfw()
    private val imGuiGl3 = ImGuiImplGl3()
    private val glslVersion = "#version 330"

    fun init(glfwWindow: Long) {
        ImGui.createContext()

        val io = ImGui.getIO()
        io.iniFilename = "imgui.ini"
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable or ImGuiConfigFlags.ViewportsEnable)
        io.backendPlatformName = "imgui_java_impl_glfw"

        imGuiGlfw.init(glfwWindow, true) // TRUE means install callbacks
        setupStyle()
        imGuiGl3.init(glslVersion)
    }

    private fun setupStyle() {
        val style = ImGui.getStyle()
        style.windowRounding = 0f
        style.childRounding = 0f
        style.frameRounding = 0f
        style.grabRounding = 0f
        style.popupRounding = 0f
        style.scrollbarRounding = 0f

        ImGui.styleColorsDark()
    }

    fun update(dt: Float, currentScene: Scene) {
        startFrame()

        setupDockSpace()
        currentScene.imgui()
        
        // Simple demo window
        ImGui.showDemoWindow()

        endFrame()
    }

    private fun startFrame() {
        imGuiGlfw.newFrame()
        ImGui.newFrame()
    }

    private fun endFrame() {
        ImGui.render()
        imGuiGl3.renderDrawData(ImGui.getDrawData())

        if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            val backupWindowPtr = glfwGetCurrentContext()
            ImGui.updatePlatformWindows()
            ImGui.renderPlatformWindowsDefault()
            glfwMakeContextCurrent(backupWindowPtr)
        }
    }

    private fun setupDockSpace() {
        var windowFlags = ImGuiWindowFlags.MenuBar or ImGuiWindowFlags.NoDocking
        
        val viewport = ImGui.getMainViewport()
        ImGui.setNextWindowPos(viewport.workPosX, viewport.workPosY)
        ImGui.setNextWindowSize(viewport.workSizeX, viewport.workSizeY)
        ImGui.setNextWindowViewport(viewport.id)
        ImGui.setNextWindowPos(0.0f, 0.0f, ImGuiCond.Always)
        
        windowFlags = windowFlags or (ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.NoCollapse or 
                ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoMove or 
                ImGuiWindowFlags.NoBringToFrontOnFocus or ImGuiWindowFlags.NoNavFocus)

        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0.0f)
        ImGui.begin("DockSpace Demo", ImBoolean(true), windowFlags)
        ImGui.popStyleVar(2)

        ImGui.dockSpace(ImGui.getID("DockSpace"))
        ImGui.end()
    }

    fun destroy() {
        // imGuiGl3.shutdown()
        // imGuiGlfw.shutdown()
        // ImGui.destroyContext()
    }
}