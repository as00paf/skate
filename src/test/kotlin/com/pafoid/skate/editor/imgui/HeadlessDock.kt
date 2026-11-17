package com.pafoid.skate.editor.imgui

import imgui.ImGui
import imgui.flag.ImGuiConfigFlags
import imgui.flag.ImGuiDir
import imgui.flag.ImGuiDockNodeFlags
import imgui.flag.ImGuiWindowFlags
import imgui.internal.ImGui as Internal
import imgui.type.ImInt

/**
 * A headless ImGui context with docking and a dock space split like the editor's default layout (left / centre /
 * right / bottom). No GPU and no fonts beyond ImGui's default: frames are built and rendered to draw lists only.
 */
class HeadlessDock(
    /** Window name (ImGui ID string) -> zone. */
    private val zones: Map<String, DefaultDockLayout.Zone>,
    /** Flags the layout gives the central node when it is built. */
    private val centralFlags: (imgui.internal.ImGuiDockNode) -> Unit = {},
) {
    var dockspaceId = 0
        private set
    var centralId = 0
        private set

    /** Each window's dock id after the last frame. */
    val dockIds = HashMap<String, Int>()

    /** Windows that are skipped (closed) this frame. */
    val hidden = HashSet<String>()

    /** Called inside the host window, after the dock space, each frame. */
    var extra: () -> Unit = {}

    private var built = false

    fun create() {
        ImGui.createContext()
        val io = ImGui.getIO()
        io.iniFilename = null
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable)
        io.fonts.addFontDefault()
        io.fonts.build()
        io.setDisplaySize(1600f, 1000f)
        io.deltaTime = 1f / 60f
    }

    fun destroy() = ImGui.destroyContext()

    fun frame(dockSpaceFlags: Int = 0, windows: () -> Unit = { drawWindows() }) {
        ImGui.newFrame()
        ImGui.setNextWindowPos(0f, 0f)
        ImGui.setNextWindowSize(1600f, 1000f)
        ImGui.begin(
            "Host", ImGuiWindowFlags.NoDocking or ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.NoResize or
                ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoBringToFrontOnFocus or ImGuiWindowFlags.NoSavedSettings,
        )
        dockspaceId = ImGui.getID("DockSpace")
        if (!built) build()
        ImGui.dockSpace(dockspaceId, 0f, 0f, dockSpaceFlags)
        centralId = Internal.dockBuilderGetCentralNode(dockspaceId).id
        extra()
        ImGui.end()
        windows()
        ImGui.render()
    }

    fun drawWindows() {
        for (name in zones.keys) {
            if (name in hidden) continue
            ImGui.begin(name)
            dockIds[name] = ImGui.getWindowDockID()
            ImGui.text(name)
            ImGui.end()
        }
    }

    fun frames(n: Int) = repeat(n) { frame() }

    private fun build() {
        built = true
        Internal.dockBuilderRemoveNode(dockspaceId)
        Internal.dockBuilderAddNode(dockspaceId, ImGuiDockNodeFlags.PassthruCentralNode or (1 shl 10) /* DockSpace */)
        Internal.dockBuilderSetNodeSize(dockspaceId, 1600f, 1000f)
        val body = ImInt(0)
        val left = Internal.dockBuilderSplitNode(dockspaceId, ImGuiDir.Left, DefaultDockLayout.LEFT_RATIO, null, body)
        val right = Internal.dockBuilderSplitNode(body.get(), ImGuiDir.Right, DefaultDockLayout.RIGHT_RATIO, null, body)
        val bottom = Internal.dockBuilderSplitNode(body.get(), ImGuiDir.Down, DefaultDockLayout.BOTTOM_RATIO, null, body)
        centralFlags(Internal.dockBuilderGetNode(body.get()))
        for ((name, zone) in zones) {
            val id = when (zone) {
                DefaultDockLayout.Zone.LEFT -> left
                DefaultDockLayout.Zone.RIGHT -> right
                DefaultDockLayout.Zone.BOTTOM -> bottom
                DefaultDockLayout.Zone.CENTER -> body.get()
            }
            Internal.dockBuilderDockWindow(name, id)
        }
        Internal.dockBuilderFinish(dockspaceId)
    }
}
