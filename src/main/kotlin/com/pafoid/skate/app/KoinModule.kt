package com.pafoid.skate.app

import com.pafoid.skate.editor.EditorSystemFactory
import com.pafoid.skate.editor.LevelEditorSceneInitializer
import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.editor.project.EngineAssetCopier
import com.pafoid.skate.editor.project.ProjectManager
import com.pafoid.skate.editor.project.ProjectWizard
import com.pafoid.skate.editor.search.SearchEngine
import com.pafoid.skate.editor.search.history.SearchHistory
import com.pafoid.skate.editor.search.providers.ActionSearchProvider
import com.pafoid.skate.editor.search.providers.AssetSearchProvider
import com.pafoid.skate.editor.search.providers.ComponentSearchProvider
import com.pafoid.skate.editor.search.providers.GameObjectSearchProvider
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.DisplayService
import com.pafoid.skate.editor.systems.EditorInputHandler
import com.pafoid.skate.editor.systems.FileSystemScanner
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.PrefabsGenerator
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.ThumbnailCache
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.WindowRegistry
import com.pafoid.skate.editor.ui.menus.ViewportContextMenu
import com.pafoid.skate.editor.ui.viewmodels.SceneViewModel
import com.pafoid.skate.editor.ui.viewmodels.SelectionViewModel
import com.pafoid.skate.editor.ui.windows.AssetBrowserWindow
import com.pafoid.skate.editor.ui.windows.AudioInspectorWindow
import com.pafoid.skate.editor.ui.windows.CommandHistoryWindow
import com.pafoid.skate.editor.ui.windows.ConsoleWindow
import com.pafoid.skate.editor.ui.windows.EditorSettingsWindow
import com.pafoid.skate.editor.ui.windows.EnvironmentWindow
import com.pafoid.skate.editor.ui.windows.GameViewWindow
import com.pafoid.skate.editor.ui.windows.InputTestingWindow
import com.pafoid.skate.editor.ui.windows.KeyBindingsWindow
import com.pafoid.skate.editor.ui.windows.PhysicsTunerWindow
import com.pafoid.skate.editor.ui.windows.ProfilerWindow
import com.pafoid.skate.editor.ui.windows.ProjectSettingsWindow
import com.pafoid.skate.editor.ui.windows.ProjectSwitcherDialog
import com.pafoid.skate.editor.ui.windows.ProjectWindow
import com.pafoid.skate.editor.ui.windows.ProjectWizardWindow
import com.pafoid.skate.editor.ui.windows.PropertiesWindow
import com.pafoid.skate.editor.ui.windows.RenderGraphWindow
import com.pafoid.skate.editor.ui.windows.SceneHierarchyWindow
import com.pafoid.skate.editor.ui.windows.SearchEverywhereWindow
import com.pafoid.skate.editor.ui.windows.SystemsWindow
import com.pafoid.skate.editor.ui.windows.TrickUIWindow
import com.pafoid.skate.editor.ui.windows.viewport.ViewportOverlays
import com.pafoid.skate.editor.ui.windows.viewport.ViewportRenderer
import com.pafoid.skate.editor.ui.windows.viewport.ViewportToolbar
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.database.AssetDatabase
import com.pafoid.skate.engine.assets.database.AssetDatabaseImpl
import com.pafoid.skate.engine.assets.database.ImportPipeline
import com.pafoid.skate.engine.assets.database.importers.AudioImporter
import com.pafoid.skate.engine.assets.database.importers.ModelImporter
import com.pafoid.skate.engine.assets.database.importers.ShaderImporter
import com.pafoid.skate.engine.assets.database.importers.TextureImporter
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import com.pafoid.skate.engine.assets.loaders.ShaderLoader
import com.pafoid.skate.engine.assets.serialization.PoseSerializer
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.core.BootManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.systems.AudioSystem
import com.pafoid.skate.engine.ecs.systems.EventSystem
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
import com.pafoid.skate.engine.render.renderer.ModelRenderer
import com.pafoid.skate.engine.render.renderer.PickingRenderer
import com.pafoid.skate.engine.render.renderer.Renderer
import com.pafoid.skate.engine.render.renderer.ThumbnailRenderer
import com.pafoid.skate.editor.project.SceneSerializer
import com.pafoid.skate.game.trick.TrickManager
import org.koin.dsl.module

val appModule = module {
    single { Engine() }
    single { SceneManager() }
    single { Serializer() }
    single { LoggerService() }
    single { AudioEngine(get()) }
    single { SceneSerializer(get(), get(), get(), get()) }
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
    factory { EditorSettingsWindow(get(), get()) }
    factory { ProjectSettingsWindow(get(), get(), get(), get(), get()) }
    factory { KeyBindingsWindow(get(), get()) }
    factory { CommandHistoryWindow() }
    factory { RenderGraphWindow() }
    factory { AudioInspectorWindow() }
    factory { ProjectWindow() }

    // FileSystem service
    single { FileSystemScanner(get(), get(), get()) }

    // Window registry
    single { WindowRegistry(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { ImGuiLayer(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }

    // Project management
    single { ProjectManager(get(), get(), get(), get(), get(), get(), get(), get()) }
    single { ProjectWizard() }
    single { ProjectWizardWindow() }
    single { ProjectSwitcherDialog() }

    // Scene initialization components
    factory { LevelEditorSceneInitializer() }
    factory { EditorSystemFactory() }

    // Search infrastructure
    single {
        SearchEngine().apply {
            registerProvider(get<GameObjectSearchProvider>())
            registerProvider(get<AssetSearchProvider>())
            registerProvider(get<ComponentSearchProvider>())
            registerProvider(get<ActionSearchProvider>())
        }
    }
    single { GameObjectSearchProvider(get(), get()) }
    single { AssetSearchProvider(get()) }
    single { ComponentSearchProvider(get(), get()) }
    single { ActionSearchProvider(get(), get(), get(), get(), get()) }
    single { SearchEverywhereWindow(SearchHistory(serializer = get())) }
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

    // Asset database and import pipeline
    single {
        ImportPipeline(get()).apply {
            registerImporter(TextureImporter())
            registerImporter(ModelImporter())
            registerImporter(AudioImporter())
            registerImporter(ShaderImporter())
        }
    }
    single { AssetDatabaseImpl(get(), get(), get()) as AssetDatabase }

    single { ResourceManager(get(), get(), get(), get(), assetDatabase = get()) }
    single { PoseSerializer() }

    single { DebugRenderer(get(), get(), get()) }
    single { PickingRenderer(get(), get(), get()) }
    single { ModelRenderer(get(), get()) }
    single { ThumbnailRenderer(get(), get(), get()) }
    single { ThumbnailCache(get()) }
    single { PrefabsGenerator(get(), get()) }
    single { EngineAssetCopier() }
    single { SplashScreen() }

    // Render resources factory - created lazily when Renderer is requested
    single { RenderResourcesFactory(get(), get(), get(), get(), get(), get()) }

    // Renderer is created with the factory, initialization happens in BootManager
    single { Renderer(get()) }

    single { BootManager(get(), get(), get(), get(), get(), get()) }

    // ECS Systems with constructor injection
    single { InputSystem(get(), get(), get(), get(), get()) }
    single { MouseControls(get(), get(), get(), get(), get(), get()) }
    single { GizmoSystem(get(), get(), get(), get(), get(), get(), get()) }
    single { AudioSystem(get(), get()) }
}
