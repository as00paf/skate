package com.pafoid.skate.app

import com.pafoid.skate.editor.data.EditorInputState
import com.pafoid.skate.editor.gizmos.EditorCamera
import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.editor.imgui.WindowRegistry
import com.pafoid.skate.editor.project.EngineAssetCopier
import com.pafoid.skate.editor.search.SearchEngine
import com.pafoid.skate.editor.search.providers.ActionSearchProvider
import com.pafoid.skate.editor.search.providers.AssetSearchProvider
import com.pafoid.skate.editor.search.providers.ComponentSearchProvider
import com.pafoid.skate.editor.search.providers.GameObjectSearchProvider
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.DisplayService
import com.pafoid.skate.editor.systems.EditorMutationGate
import com.pafoid.skate.editor.systems.GizmoSystem
import com.pafoid.skate.editor.systems.PrefabsGenerator
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.handlers.ConsoleActionHandler
import com.pafoid.skate.editor.ui.handlers.EditorEventHandler
import com.pafoid.skate.editor.ui.handlers.EditorInputHandler
import com.pafoid.skate.editor.ui.handlers.EnvironmentActionHandler
import com.pafoid.skate.editor.ui.handlers.ProjectActionHandler
import com.pafoid.skate.editor.ui.handlers.SceneActionHandler
import com.pafoid.skate.editor.ui.handlers.UndoRedoActionHandler
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.utils.DefaultJobSystem
import com.pafoid.skate.engine.utils.IJobSystem
import org.joml.Vector3f
import org.koin.dsl.module

val editorModule = module {

    // Editor-only rendering tools (moved from engineModule)
    single { PrefabsGenerator(get()) }
    single { EngineAssetCopier() }

    single { ClipboardService(get()) }
    single { EditorMutationGate(get(), get()) }
    single { UndoRedoManager(get(), get()) }
    single { SettingsManager(get(), get(), get()) }
    single { DisplayService() }

    single(createdAtStart = true) { SceneActionHandler(get(), get(), get(), get(), get(), get()).also { it.init() } }
    single(createdAtStart = true) { ProjectActionHandler(get(), get(), get(), get(), get(), get()) }
    single(createdAtStart = true) { EnvironmentActionHandler(get(), get()).also { it.init() } }
    single(createdAtStart = true) { ConsoleActionHandler().also { it.init() } }
    single(createdAtStart = true) { UndoRedoActionHandler().also { it.init() } }

    // Editor Workspace
    single { EditorInputState() }
    single { EditorCamera(Camera().also { it.position.set(Vector3f(0f, 5f, 20f)) }, get()) }
    single { EditorInputHandler(get(), get(), get(), get(), get(), get(), get()) }
    single { EditorEventHandler(get(), get(), get()) }
    single { GizmoSystem(get(), get(), get(), get(), get()) }

    // Window registry
    single {
        WindowRegistry(
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
            get()
        )
    }
    single { ImGuiLayer(get(), get(), get(), get(), get(), get(), get()) }

    // Project management
    single { ProjectManager(get(), get(), get(), get(), get(), get()) }

    // Search infrastructure
    // TODO: cleanup?
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
    single { ActionSearchProvider(get(), get(), get()) }
}

val engineModule = module {
    // Core
    single<IJobSystem> { DefaultJobSystem() }
    single { StringManager() }
    single { EventSystem() }
    single { LoggerService() }
    single { Serializer() }

    //Engine
    single {
        Engine(
            serializer = get(),
            jobSystem = get(),
            logger = get(),
            eventSystem = get(),
        )
    }
}
