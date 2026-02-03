package com.pafoid.skate.engine

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.controls.input.IInputBuffer
import com.pafoid.skate.engine.controls.input.InputBuffer
import com.pafoid.skate.engine.controls.listeners.JoystickListener
import com.pafoid.skate.engine.controls.listeners.KeyListener
import com.pafoid.skate.engine.controls.listeners.MouseListener
import com.pafoid.skate.engine.imgui.ImGuiLayer
import com.pafoid.skate.engine.render.Renderer
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.utils.JobSystem
import com.pafoid.skate.engine.utils.JobSystem.runOnMain
import com.pafoid.skate.engine.utils.SettingsManager
import com.pafoid.skate.engine.utils.Time
import java.nio.ByteBuffer
import org.joml.Vector2f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWImage
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS
import org.lwjgl.opengl.GLUtil
import org.lwjgl.stb.STBImage
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.NULL

class Window(
    val width: Int = 1920,
    val height: Int = 1080,
    val title: String
): KoinComponent {

    private val inputBuffer: IInputBuffer by inject()
    private val joystickListener: JoystickListener by inject()
    private val keyListener: KeyListener by inject()
    private val mouseListener: MouseListener by inject()
    private val settingsManager: SettingsManager by inject()
    private val sceneManager: SceneManager by inject()
    private val renderer: Renderer by inject()
    private val imGuiLayer: ImGuiLayer by inject()

    private var glfwWindow: Long = -1L
    private var isFirstDraw = true

    private val openGLDebug = GLFW_FALSE

    fun run() {
        init()
        loop()
    }

    private fun init() {
        settingsManager.load()
        val settings = settingsManager.settings

        // Error callback
        GLFWErrorCallback.createPrint(System.err).set()

        if (!glfwInit()) throw IllegalStateException("Unable to initialize GLFW.")

        // Configure GLFW
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
        glfwWindowHint(GLFW_MAXIMIZED, GLFW_TRUE)
        glfwWindowHint(GLFW_DECORATED, GLFW_TRUE)
        glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, openGLDebug)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)

        // Get the primary monitor and its video mode
        val primaryMonitor = glfwGetPrimaryMonitor()
        val videoMode = glfwGetVideoMode(primaryMonitor)
        
        val winWidth = videoMode?.width() ?: width
        val winHeight = videoMode?.height() ?: height

        // Create the window (passing NULL for monitor makes it windowed)
        glfwWindow = glfwCreateWindow(winWidth, winHeight, title, NULL, NULL)
        if (glfwWindow == NULL) throw IllegalStateException("Unable to create the GLFW window.")
        
        // Center/Position at 0,0
        glfwSetWindowPos(glfwWindow, 0, 0) // Corrected yPos to integer 0
        
        sceneManager.currentWidth = winWidth
        sceneManager.currentHeight = winHeight

        // Set the window icon
        setWindowIcon(Assets.Textures.APP_ICON)

        // Make OpenGL context current
        glfwMakeContextCurrent(glfwWindow)

        // Enable v-sync
        glfwSwapInterval(if (settings.vsync) 1 else 0)

        // This is needed for OpenGL
        GL.createCapabilities()
        if(openGLDebug == GLFW_TRUE) {
            GLUtil.setupDebugMessageCallback(System.err)
        }

        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS)

        installCallbacks()
        joystickListener.init()
        imGuiLayer.init(glfwWindow, ::setFullscreen, ::setVSync)
    }

    private fun setWindowIcon(iconPath: String) {
        MemoryStack.stackPush().use { stack ->
            val w = stack.mallocInt(1)
            val h = stack.mallocInt(1)
            val comp = stack.mallocInt(1)

            // Ensure flip vertical is disabled for UI images
            STBImage.stbi_set_flip_vertically_on_load(false)
            val pixels: ByteBuffer? = STBImage.stbi_load(iconPath, w, h, comp, 4) // Force 4 channels (RGBA)
            STBImage.stbi_set_flip_vertically_on_load(true) // Re-enable for other textures

            if (pixels == null) {
                System.err.println("Failed to load image at path: $iconPath")
                return
            }

            val icon = GLFWImage.malloc(stack)
            icon.width(w.get(0))
            icon.height(h.get(0))
            icon.pixels(pixels)

            val icons = GLFWImage.malloc(1, stack)
            icons.put(0, icon)

            glfwSetWindowIcon(glfwWindow, icons)

            STBImage.stbi_image_free(pixels) // Free the image data
        }
    }

    private fun installCallbacks() {
        glfwSetWindowSizeCallback(glfwWindow) { w: Long, newWidth: Int, newHeight: Int ->
            sceneManager.currentWidth = newWidth
            sceneManager.currentHeight = newHeight
            glViewport(0, 0, newWidth, newHeight)
        }
        glfwSetCursorPosCallback(glfwWindow, mouseListener::mousePosCallback)
        glfwSetMouseButtonCallback(glfwWindow, mouseListener::mouseButtonCallback)
        glfwSetScrollCallback(glfwWindow, mouseListener::mouseScrollCallback)
        glfwSetKeyCallback(glfwWindow, keyListener::keyCallback)
    }

    private fun loop() {
        var beginTime = Time.getTime()
        var endTime: Float
        var dt = 0.0f

        while (!glfwWindowShouldClose(glfwWindow)) {
            glfwPollEvents()
            joystickListener.update()
            JobSystem.update()
            
            // Record high-frequency input
            inputBuffer.push(
                Time.getTime(),
                Vector2f(mouseListener.getX(), mouseListener.getY()),
                joystickListener.getAxes(GLFW_JOYSTICK_1)
            )

            if (isFirstDraw) {
                // Set viewport if not already initialized
                glViewport(0, 0, sceneManager.currentWidth, sceneManager.currentHeight)
                runOnMain {
                    show()
                    sceneManager.initializeScene()
                }
                isFirstDraw = false
            }

            sceneManager.draw(dt, imGuiLayer)
            glfwSwapBuffers(glfwWindow)

            keyListener.endFrame()
            mouseListener.endFrame()

            endTime = Time.getTime()
            dt = endTime - beginTime
            beginTime = endTime
        }

        destroy()
    }

    private fun show() {
        glfwShowWindow(glfwWindow)
        glfwFocusWindow(glfwWindow)
        glfwRequestWindowAttention(glfwWindow)
    }

    fun setFullscreen(enabled: Boolean) {
        val monitor = glfwGetPrimaryMonitor()
        val vidMode = glfwGetVideoMode(monitor) ?: return
        if (enabled) {
            glfwSetWindowMonitor(glfwWindow, monitor, 0, 0, vidMode.width(), vidMode.height(), vidMode.refreshRate())
        } else {
            glfwSetWindowMonitor(glfwWindow, NULL, 100, 100, width, height, GLFW_DONT_CARE)
        }
    }

    fun setVSync(enabled: Boolean) {
        glfwSwapInterval(if (enabled) 1 else 0)
        // Corrected call to use instance and ensure initCallback runs on main thread
        runOnMain {
            sceneManager.initializeScene()
        }
    }

    private fun destroy() {
        imGuiLayer.destroy()
        sceneManager.destroy()

        // Free memory
        glfwFreeCallbacks(glfwWindow)
        glfwDestroyWindow(glfwWindow)

        // Terminate GLFW and free the error callback
        glfwTerminate()
        glfwSetErrorCallback(null)?.free()
    }
}