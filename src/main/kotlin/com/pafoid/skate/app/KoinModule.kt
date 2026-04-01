package com.pafoid.skate.app

import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.editor.search.SearchEngine
import com.pafoid.skate.editor.search.SearchProvider
import com.pafoid.skate.editor.search.history.SearchHistory
import com.pafoid.skate.editor.search.providers.ActionSearchProvider
import com.pafoid.skate.editor.search.providers.AssetSearchProvider
import com.pafoid.skate.editor.search.providers.ComponentSearchProvider
import com.pafoid.skate.editor.search.providers.GameObjectSearchProvider
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.DisplayService
import com.pafoid.skate.editor.systems.EditorInputHandler
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.PrefabsGenerator
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.ThumbnailCache
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.viewmodels.SelectionViewModel
import com.pafoid.skate.editor.ui.viewmodels.SceneViewModel
import com.pafoid.skate.editor.ui.WindowRegistry
import com.pafoid.skate.editor.ui.imgui.windows.components.ViewportContextMenu
import com.pafoid.skate.editor.ui.imgui.windows.components.ViewportOverlays
import com.pafoid.skate.editor.ui.imgui.windows.components.ViewportRenderer
import com.pafoid.skate.editor.ui.imgui.windows.components.ViewportToolbar
import com.pafoid.skate.editor.ui.imgui.menus.EditMenuBuilder
import com.pafoid.skate.editor.ui.imgui.menus.FileMenuBuilder
import com.pafoid.skate.editor.ui.imgui.menus.SettingsMenuBuilder
import com.pafoid.skate.editor.ui.imgui.menus.ViewMenuBuilder
import com.pafoid.skate.editor.ui.imgui.menus.WindowControlsRenderer
import com.pafoid.skate.editor.windows.CommandHistoryWindow
import com.pafoid.skate.editor.windows.ConsoleWindow
import com.pafoid.skate.editor.windows.EnvironmentWindow
import com.pafoid.skate.editor.windows.InputTestingWindow
import com.pafoid.skate.editor.windows.KeyBindingsWindow
import com.pafoid.skate.editor.windows.PhysicsTunerWindow
import com.pafoid.skate.editor.windows.ProfilerWindow
import com.pafoid.skate.editor.windows.RenderGraphWindow
import com.pafoid.skate.editor.windows.SceneHierarchyWindow
import com.pafoid.skate.editor.windows.SettingsWindow
import com.pafoid.skate.editor.windows.SystemsWindow
import com.pafoid.skate.editor.windows.SearchEverywhereWindow
import com.pafoid.skate.editor.windows.TrickUIWindow
import com.pafoid.skate.editor.windows.PropertiesWindow
import com.pafoid.skate.editor.windows.GameViewWindow
import com.pafoid.skate.editor.windows.AssetBrowserWindow
import com.pafoid.skate.engine.events.EventSystem
import com.pafoid.skate.engine.events.SceneOpened
import com.pafoid.skate.engine.events.SceneChanged
import com.pafoid.skate.engine.events.SceneClosed
import com.pafoid.skate.engine.events.GameObjectSelected
import com.pafoid.skate.engine.events.SelectionCleared
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import com.pafoid.skate.engine.assets.loaders.ShaderLoader
import com.pafoid.skate.engine.assets.serialization.PoseSerializer
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.core.BootManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.systems.AudioSystem
import com.pafoid.skate.engine.ecs.systems.GizmoSystem
import com.pafoid.skate.engine.ecs.systems.InputSystem
import com.pafoid.skate.engine.ecs.systems.MouseControls
import com.pafoid.skate.engine.input.IInputBuffer
import com.pafoid.skate.engine.input.IInputProvider
import com.pafoid.skate.engine.input.InputBuffer
import com.pafoid.skate.engine.input.InputProvider
import com.pafoid.skate.engine.input.listeners.GamepadListener
import com.pafoid.skate.engine.input.listeners.KeyListener
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.render.RenderResourcesFactory
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.render.renderer.PickingRenderer
import com.pafoid.skate.engine.render.renderer.Renderer
import com.pafoid.skate.game.level.LevelManager
import com.pafoid.skate.game.project.ProjectManager
import com.pafoid.skate.game.project.ProjectWizard
import com.pafoid.skate.game.trick.TrickManager
import org.koin.dsl.module

val appModule = module {
    single { Engine() }
    single { SceneManager() }
    single { Serializer() }
    single { LoggerService() }
    single { AudioEngine(get()) }
    single { LevelManager(get(), get()) }
    single { ClipboardService(get()) }
    single { UndoRedoManager() }
    single { EditorInputHandler(get(), get(), get(), get()) }
    single { StringManager() }
    single { SettingsManager(get(), get(), get()) }
    single { DisplayService() }
    single { TrickManager() }
    single { TrickUIWindow() }

    // EventSystem for editor event bus
    single { EventSystem() }
    
    // ViewModels for UI state management
    factory { SelectionViewModel(get(), get()) }
    factory { SceneViewModel(get(), get()) }
    
    // Viewport components for GameViewWindow
    factory { ViewportRenderer(get(), get()) }
    factory { ViewportToolbar(get(), get(), get(), get()) }
    factory { ViewportContextMenu(get()) }
    factory { ViewportOverlays(get(), get()) }
    
    // Menu builders for EditorMenuBar
    factory { FileMenuBuilder(get(), get(), get(), 0L) }
    factory { EditMenuBuilder(get(), get(), get(), get(), get()) }
    factory { SettingsMenuBuilder(get(), get(), get(), get()) }
    factory { ViewMenuBuilder(get(), get()) }
    factory { WindowControlsRenderer(get(), get()) }
    
    // Editor windows
    factory { SceneHierarchyWindow() }
    factory { PropertiesWindow() }
    factory { GameViewWindow() }
    factory { AssetBrowserWindow() }
    factory { EnvironmentWindow() }
    factory { ProfilerWindow() }
    factory { ConsoleWindow() }
    factory { PhysicsTunerWindow() }
    factory { InputTestingWindow(get(), get(), get()) }
    factory { SystemsWindow() }
    factory { SettingsWindow(get(), get()) }
    factory { KeyBindingsWindow(get(), get()) }
    factory { CommandHistoryWindow() }
    factory { RenderGraphWindow() }
    
    // Window registry
    single { WindowRegistry(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { ImGuiLayer(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    
    // Project management
    single { ProjectManager(get(), get(), get()) }
    factory { ProjectWizard() }

    // Search infrastructure
    single {
        SearchEngine().apply {
            registerProvider(get<GameObjectSearchProvider>())
            registerProvider(get<AssetSearchProvider>())
            registerProvider(get<ComponentSearchProvider>())
            registerProvider(get<ActionSearchProvider>())
        }
    }
    single { SearchHistory() }
    single { GameObjectSearchProvider() }
    single { AssetSearchProvider() }
    single { ComponentSearchProvider() }
    single { ActionSearchProvider() }
    single { SearchEverywhereWindow() }
}

val inputModule = module {
    single { GamepadListener(get()) }
    single { KeyListener() }
    single { MouseListener() }
    single<IInputProvider> { InputProvider(get(), get()) }
}

val engineModule = module {
    single<IInputBuffer> { InputBuffer() }
    single { ShaderLoader(false) }
    single { VAOLoader() }
    single { AssimpLoader() }
    single { ResourceManager(get(), get(), get(), get()) }
    single { PoseSerializer() }

    single { DebugRenderer(get(), get(), get()) }
    single { PickingRenderer(get(), get(), get()) }

    single { ThumbnailCache(get()) }
    single { PrefabsGenerator(get(), get()) }
    single { SplashScreen() }

    // Render resources factory - created lazily when Renderer is requested
    single { RenderResourcesFactory(get(), get(), get(), get(), get()) }

    // Renderer is created with the factory, initialization happens in BootManager
    single { Renderer(get()) }

    single { BootManager(get(), get(), get(), get(), get()) }

    // ECS Systems with constructor injection
    single { InputSystem(get(), get(), get(), get()) }
    single { MouseControls(get(), get(), get(), get(), get(), get()) }
    single { GizmoSystem(get(), get(), get(), get(), get(), get(), get()) }
    single { AudioSystem(get(), get()) }
}
