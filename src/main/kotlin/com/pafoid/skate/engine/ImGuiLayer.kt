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
import imgui.flag.*
import imgui.type.ImBoolean
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL11.*

class ImGuiLayer {

    private val imGuiGlfw = ImGuiImplGlfw()
    private val imGuiGl3 = ImGuiImplGl3()
    private val glslVersion = "#version 330"
    
    private var firstFrame = true
    
    val propertiesWindow = com.pafoid.skate.engine.editor.PropertiesWindow()
    private val hierarchyWindow = com.pafoid.skate.engine.editor.SceneHierarchyWindow(propertiesWindow)
    val gameViewWindow = com.pafoid.skate.engine.editor.GameViewWindow()

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

    private fun setupLayout(dockspaceId: Int) {
        if (!firstFrame) return
        firstFrame = false

        imgui.internal.ImGui.dockBuilderRemoveNode(dockspaceId)
        imgui.internal.ImGui.dockBuilderAddNode(dockspaceId, ImGuiDockNodeFlags.None)
        imgui.internal.ImGui.dockBuilderSetNodeSize(dockspaceId, ImGui.getMainViewport().sizeX, ImGui.getMainViewport().sizeY)

        val leftId = imgui.internal.ImGui.dockBuilderSplitNode(dockspaceId, ImGuiDir.Left, 0.2f, null, null)
        val rightId = imgui.internal.ImGui.dockBuilderSplitNode(dockspaceId, ImGuiDir.Right, 0.25f, null, null)
        val bottomId = imgui.internal.ImGui.dockBuilderSplitNode(dockspaceId, ImGuiDir.Down, 0.25f, null, null)

        imgui.internal.ImGui.dockBuilderDockWindow("Scene Hierarchy", leftId)
        imgui.internal.ImGui.dockBuilderDockWindow("Properties", rightId)
        imgui.internal.ImGui.dockBuilderDockWindow("Objects", bottomId)
        imgui.internal.ImGui.dockBuilderDockWindow("Game Viewport", dockspaceId)

        imgui.internal.ImGui.dockBuilderFinish(dockspaceId)
    }

    fun update(dt: Float, currentScene: Scene) {
        startFrame()

        setupDockSpace(currentScene)
        currentScene.imgui()
        hierarchyWindow.imgui(currentScene)
        propertiesWindow.imgui()
        gameViewWindow.imgui()
        
        // Simple demo window
        // ImGui.showDemoWindow()

        endFrame()
    }

    private fun startFrame() {
        imGuiGlfw.newFrame()
        imGuiGl3.newFrame()
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

    private fun setupDockSpace(currentScene: Scene) {
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
        setupLayout(ImGui.getID("DockSpace"))

        if (ImGui.beginMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("Save Level")) {
                    currentScene.save()
                }
                if (ImGui.menuItem("Load Level")) {
                    currentScene.load()
                }
                ImGui.endMenu()
            }
            if (ImGui.beginMenu("Create")) {
                if (ImGui.menuItem("Cube")) {
                    // Spawning a cube logic
                }
                if (ImGui.menuItem("Sprite")) {
                    // Spawning a sprite logic
                }
                ImGui.endMenu()
            }
            ImGui.endMenuBar()
        }

        ImGui.end()
    }

    fun destroy() {
        imGuiGl3.shutdown()
        imGuiGlfw.shutdown()
        ImGui.destroyContext()
    }
}