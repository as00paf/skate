package com.pafoid.skate.editor.ui

import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.ui.interfaces.IWindowLifecycle
import com.pafoid.skate.editor.windows.AssetBrowserWindow
import com.pafoid.skate.editor.windows.CommandHistoryWindow
import com.pafoid.skate.editor.windows.ConsoleWindow
import com.pafoid.skate.editor.windows.EnvironmentWindow
import com.pafoid.skate.editor.windows.GameViewWindow
import com.pafoid.skate.editor.windows.InputTestingWindow
import com.pafoid.skate.editor.windows.KeyBindingsWindow
import com.pafoid.skate.editor.windows.PhysicsTunerWindow
import com.pafoid.skate.editor.windows.ProfilerWindow
import com.pafoid.skate.editor.windows.PropertiesWindow
import com.pafoid.skate.editor.windows.RenderGraphWindow
import com.pafoid.skate.editor.windows.SceneHierarchyWindow
import com.pafoid.skate.editor.windows.SearchEverywhereWindow
import com.pafoid.skate.editor.windows.SettingsWindow
import com.pafoid.skate.editor.windows.SystemsWindow
import imgui.type.ImBoolean

/**
 * Registry of all dockable editor windows.
 * 
 * Centralizes window management for rendering, menus, and dock layout.
 * All windows are created via dependency injection and registered here.
 */
class WindowRegistry(
    val hierarchyWindow: SceneHierarchyWindow,
    val propertiesWindow: PropertiesWindow,
    val gameViewWindow: GameViewWindow,
    val assetBrowser: AssetBrowserWindow,
    val environmentWindow: EnvironmentWindow,
    val profilerWindow: ProfilerWindow,
    val consoleWindow: ConsoleWindow,
    val physicsTunerWindow: PhysicsTunerWindow,
    val inputTestingWindow: InputTestingWindow,
    val systemsWindow: SystemsWindow,
    val settingsWindow: SettingsWindow,
    val keyBindingsWindow: KeyBindingsWindow,
    val commandHistoryWindow: CommandHistoryWindow,
    val renderGraphWindow: RenderGraphWindow
) {
    
    /**
     * List of all registered editor windows with their metadata.
     */
    val windows: List<EditorWindow> = listOf(
        EditorWindow("window.hierarchy", hierarchyWindow, ImBoolean(true), requiresScene = true),
        EditorWindow("window.properties", propertiesWindow, ImBoolean(true)),
        EditorWindow("window.game_viewport", gameViewWindow, ImBoolean(true)),
        EditorWindow("window.asset_browser", assetBrowser, ImBoolean(true)),
        EditorWindow("window.environment", environmentWindow, ImBoolean(true), requiresScene = true),
        EditorWindow("window.profiler", profilerWindow, ImBoolean(true)),
        EditorWindow("window.console", consoleWindow, ImBoolean(true)),
        EditorWindow("window.physics_tuner", physicsTunerWindow, ImBoolean(true), requiresScene = true),
        EditorWindow("window.input_testing", inputTestingWindow, ImBoolean(false)),
        EditorWindow("window.systems", systemsWindow, ImBoolean(true), requiresScene = true),
        EditorWindow("window.command_history", commandHistoryWindow, ImBoolean(true)),
        EditorWindow("window.render_graph", renderGraphWindow, ImBoolean(false))
    )
    
    val searchEverywhereWindow = SearchEverywhereWindow()
    
    /**
     * Get a window by its name key.
     * 
     * @param key The window name key (e.g., "window.hierarchy")
     * @return The window instance
     * @throws IllegalArgumentException if window not found
     */
    fun getWindow(key: String): IWindowLifecycle {
        return windows.find { it.nameKey == key }?.instance as? IWindowLifecycle
            ?: throw IllegalArgumentException("Unknown window: $key")
    }
    
    /**
     * Get a window by its type.
     * 
     * @param T The window type
     * @return The window instance or null if not found
     */
    inline fun <reified T> getWindow(): T? {
        return windows.find { it.instance is T }?.instance as? T
    }
    
    /**
     * Initialize all windows.
     * 
     * Calls onInit() on each registered window.
     */
    fun initializeAll() {
        windows.forEach { window ->
            (window.instance as? IWindowLifecycle)?.onInit()
        }
    }
    
    /**
     * Update all windows.
     * 
     * Calls onUpdate(dt) on each registered window.
     * 
     * @param dt Delta time since last frame
     */
    fun updateAll(dt: Float) {
        windows.forEach { window ->
            (window.instance as? IWindowLifecycle)?.onUpdate(dt)
        }
    }
    
    /**
     * Notify all windows of scene change.
     * 
     * Calls onSceneChanged(oldScene, newScene) on each registered window.
     * 
     * @param oldScene The previous scene (null if first scene)
     * @param newScene The new scene (null if scene closed)
     */
    fun onSceneChangedAll(oldScene: com.pafoid.skate.engine.ecs.Scene?, newScene: com.pafoid.skate.engine.ecs.Scene?) {
        windows.forEach { window ->
            (window.instance as? IWindowLifecycle)?.onSceneChanged(oldScene, newScene)
        }
    }
    
    /**
     * Destroy all windows.
     * 
     * Calls onDestroy() on each registered window.
     */
    fun destroyAll() {
        windows.forEach { window ->
            (window.instance as? IWindowLifecycle)?.onDestroy()
        }
    }
}
