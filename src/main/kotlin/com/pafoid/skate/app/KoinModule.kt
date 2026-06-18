package com.pafoid.skate.app

import com.pafoid.skate.editor.data.EditorInputState
import com.pafoid.skate.editor.gizmos.EditorCamera
import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.editor.imgui.WindowRegistry
import com.pafoid.skate.editor.project.EngineAssetCopier
import com.pafoid.skate.editor.project.ProjectWizard
import com.pafoid.skate.editor.project.SceneSerializer
import com.pafoid.skate.editor.search.SearchEngine
import com.pafoid.skate.editor.search.history.SearchHistory
import com.pafoid.skate.editor.search.providers.ActionSearchProvider
import com.pafoid.skate.editor.search.providers.AssetSearchProvider
import com.pafoid.skate.editor.search.providers.ComponentSearchProvider
import com.pafoid.skate.editor.search.providers.GameObjectSearchProvider
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.DisplayService
import com.pafoid.skate.editor.systems.EditorMutationGate
import com.pafoid.skate.editor.systems.FileSystemScanner
import com.pafoid.skate.editor.systems.GizmoSystem
import com.pafoid.skate.editor.systems.PrefabsGenerator
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.ThumbnailCache
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.handlers.ConsoleActionHandler
import com.pafoid.skate.editor.ui.handlers.EditorEventHandler
import com.pafoid.skate.editor.ui.handlers.EditorInputHandler
import com.pafoid.skate.editor.ui.handlers.EnvironmentActionHandler
import com.pafoid.skate.editor.ui.handlers.ProjectActionHandler
import com.pafoid.skate.editor.ui.handlers.SceneActionHandler
import com.pafoid.skate.editor.ui.handlers.UndoRedoActionHandler
import com.pafoid.skate.editor.ui.handlers.ViewportActionHandler
import com.pafoid.skate.editor.ui.handlers.ViewportDragDropHandler
import com.pafoid.skate.editor.ui.menus.EditMenuBuilder
import com.pafoid.skate.editor.ui.menus.FileMenuBuilder
import com.pafoid.skate.editor.ui.menus.SettingsMenuBuilder
import com.pafoid.skate.editor.ui.menus.ViewMenuBuilder
import com.pafoid.skate.editor.ui.menus.ViewportContextMenu
import com.pafoid.skate.editor.ui.menus.WindowControlsRenderer
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
import com.pafoid.skate.editor.ui.windows.assetBrowser.AnimationsTab
import com.pafoid.skate.editor.ui.windows.assetBrowser.PrefabsTab
import com.pafoid.skate.editor.ui.windows.assetBrowser.SoundsTab
import com.pafoid.skate.editor.ui.windows.assetBrowser.TexturesTab
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
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.systems.AnimationSystem
import com.pafoid.skate.engine.ecs.systems.AudioSystem
import com.pafoid.skate.engine.ecs.systems.DayNightCycleSystem
import com.pafoid.skate.engine.ecs.systems.DirectionalLightSystem
import com.pafoid.skate.engine.ecs.systems.EnvironmentSystem
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.ecs.systems.GridLines
import com.pafoid.skate.engine.ecs.systems.InputSystem
import com.pafoid.skate.engine.ecs.systems.PhysicsSystem
import com.pafoid.skate.engine.ecs.systems.RagdollSystem
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.input.IInputBuffer
import com.pafoid.skate.engine.input.IInputProvider
import com.pafoid.skate.engine.input.InputBuffer
import com.pafoid.skate.engine.input.InputProvider
import com.pafoid.skate.engine.input.listeners.GamepadListener
import com.pafoid.skate.engine.input.listeners.KeyListener
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.physics3d.BulletPhysics3DFactory
import com.pafoid.skate.engine.physics3d.Physics3DFactory
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.render.CameraManager
import com.pafoid.skate.engine.render.RenderResourcesFactory
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.render.renderer.ModelRenderer
import com.pafoid.skate.engine.render.renderer.PickingRenderer
import com.pafoid.skate.engine.render.renderer.Renderer
import com.pafoid.skate.engine.render.renderer.SplashRenderer
import com.pafoid.skate.engine.render.renderer.ThumbnailRenderer
import com.pafoid.skate.engine.utils.DefaultJobSystem
import com.pafoid.skate.engine.utils.IJobSystem
import com.pafoid.skate.game.trick.TrickManager
import org.joml.Vector3f
import org.koin.dsl.module

val editorModule = module {
    single { Serializer() }

    // Editor-only rendering tools (moved from engineModule)
    single { PickingRenderer(get(), get(), get()) }
    single { ThumbnailRenderer(get(), get(), get()) }
    single { ThumbnailCache(get()) }
    single { PrefabsGenerator(get(), get(), get(), get()) }
    single { EngineAssetCopier() }

    single { SceneSerializer(get(), get(), get(), get(), get()) }
    single { PoseSerializer() }
    single { ClipboardService(get()) }
    single { EditorMutationGate(get(), get()) }
    single { UndoRedoManager(get(), get()) }
    single { SettingsManager(get(), get(), get()) }
    single { DisplayService() }
    single { TrickManager() }

    single(createdAtStart = true) { SceneActionHandler().also { it.init() } }
    single(createdAtStart = true) { ProjectActionHandler(get(), get(), get(), get(), get()).also { it.init() } }
    single(createdAtStart = true) { EnvironmentActionHandler(get(), get()).also { it.init() } }
    single(createdAtStart = true) { ConsoleActionHandler().also { it.init() } }
    single(createdAtStart = true) { UndoRedoActionHandler().also { it.init() } }
    single(createdAtStart = true) {
        ViewportActionHandler(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
        )
            .also { it.init() }
    }

    // Viewport components for GameViewWindow
    factory { ViewportRenderer(get(), get()) }
    factory { ViewportToolbar(get(), get(), get(), get(), get(), get()) }
    factory { ViewportContextMenu(get(), get()) }
    factory { ViewportOverlays(get(), get()) }
    factory { ViewportDragDropHandler(get(), get()) }

    // Editor windows
    single { ProjectWizardWindow(get(), get(), get(), get(), get()) }
    single { SceneHierarchyWindow(get(), get(), get(), get(), get(), get()) }
    single { PropertiesWindow(get(), get(), get()) }
    single { GameViewWindow(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { AnimationsTab(get(), get(), get(), get(), get()) }
    single { TexturesTab(get(), get(), get(), get(), get(), get()) }
    single { PrefabsTab(get(), get(), get(), get(), get(), get()) }
    single { SoundsTab(get(), get(), get(), get()) }
    single { AssetBrowserWindow(get(), get(), get(), get(), get()) }
    single { EnvironmentWindow(get(), get(), get()) }
    single { ProfilerWindow(get()) }
    single { ConsoleWindow(get(), get(), get()) }
    single { PhysicsTunerWindow(get(), get()) }
    single { InputTestingWindow(get(), get(), get()) }
    single { SystemsWindow(get(), get()) }
    single { EditorSettingsWindow(get(), get()) }
    single { ProjectSettingsWindow(get(), get(), get(), get(), get()) }
    single { KeyBindingsWindow(get(), get()) }
    single { CommandHistoryWindow(get(), get(), get()) }
    single { RenderGraphWindow(get(), get()) }
    single { AudioInspectorWindow(get()) }
    single { ProjectWindow(get(), get(), get(), get(), get()) }
    single { TrickUIWindow(get()) }

    // FileSystem service
    single { FileSystemScanner(get(), get(), get()) }

    // Editor Workspace
    single { EditorInputState() }
    single { EditorCamera(Camera(Vector3f(0f, 5f, 20f)), get()) }
    single { EditorInputHandler(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { EditorEventHandler(get(), get(), get()) }
    single { GizmoSystem(get(), get(), get(), get(), get(), get(), get(), get(), get()) }

    // Window registry
    single { WindowRegistry(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { ImGuiLayer(get(), get(), get(), get(), get(), get(), get(), get(), get()) }

    // Menu
    single { EditMenuBuilder(get(), get(), get()) }
    single { ViewMenuBuilder(get(), get()) }
    single { FileMenuBuilder(get(), get()) }
    single { SettingsMenuBuilder(get(), get(), get()) }
    single { WindowControlsRenderer(get(), get()) }

    // Project management
    single { ProjectManager(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { ProjectWizard() }
    single { ProjectSwitcherDialog() }

    // Search infrastructure
    single {
        SearchEngine().apply {
            registerProvider(get<GameObjectSearchProvider>())
            registerProvider(get<AssetSearchProvider>())
            registerProvider(get<ComponentSearchProvider>())
            registerProvider(get<ActionSearchProvider>())
        }
    }
    single { GameObjectSearchProvider(get(), get(), get()) }
    single { AssetSearchProvider(get()) }
    single { ComponentSearchProvider(get(), get(), get()) }
    single { ActionSearchProvider(get(), get(), get()) }
    single { SearchEverywhereWindow(SearchHistory(serializer = get())) }
}

val engineModule = module {
    // Core
    single<IJobSystem> { DefaultJobSystem() }
    single { StringManager() }
    single { EventSystem() }
    single { LoggerService() }

    single { AudioEngine(get()) }

    // Input
    single { GamepadListener(get()) }
    single { KeyListener() }
    single { MouseListener(get()) }
    single<IInputProvider> { InputProvider(get(), get()) }
    single<IInputBuffer> { InputBuffer() }

    // Managers
    single { CameraManager(get()) }
    single { SystemManager() }
    single { SceneManager(get(), get(), get(), get(), get(), get()) }
    single { ResourceManager(get(), get(), get(), get(), assetDatabase = get(), jobSystem = get()) }

    single<Physics3DFactory> { BulletPhysics3DFactory(get(), { get() }) }
    single { NativeLibraryLoader() }
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

    // Rendering
    single { Renderer(get()) }
    single { DebugRenderer(get(), get(), get()) }
    single { SplashRenderer(get()) }
    single { ModelRenderer(get(), get()) }
    single { RenderResourcesFactory(get(), get(), get(), get(), get(), get(), get<SplashRenderer>(), get()) }

    //Engine
    single {
        Engine(
            get(), get(), get(), get(), get(), get(), get(), get(),
            listOf(
                GameObjectManager(), // Core
                InputSystem(get(), get(), get()),
                AudioSystem(get(), get(), get()),
                EnvironmentSystem(),
                PhysicsSystem(),
                DayNightCycleSystem(null, get()),
                DirectionalLightSystem(get()),
                AnimationSystem(),
                RagdollSystem(),
                GridLines(get(), get(), get()),
            )
        )
    }
}
