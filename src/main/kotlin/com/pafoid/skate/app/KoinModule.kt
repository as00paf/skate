package com.pafoid.skate.app

import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.EditorInputHandler
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.PrefabsGenerator
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.ThumbnailCache
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import com.pafoid.skate.engine.assets.loaders.ShaderLoader
import com.pafoid.skate.engine.assets.serialization.PoseSerializer
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.BootManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.input.IInputBuffer
import com.pafoid.skate.engine.input.IInputProvider
import com.pafoid.skate.engine.input.InputBuffer
import com.pafoid.skate.engine.input.InputProvider
import com.pafoid.skate.engine.input.listeners.JoystickListener
import com.pafoid.skate.engine.input.listeners.KeyListener
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.render.RenderResourcesFactory
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.render.renderer.PickingRenderer
import com.pafoid.skate.engine.render.renderer.Renderer
import com.pafoid.skate.game.level.LevelManager
import com.pafoid.skate.game.trick.TrickManager
import org.koin.dsl.module

val appModule = module {
    single { Engine() }
    single { SceneManager() }
    single { Serializer() }
    single { LoggerService() }
    single { LevelManager(get(), get()) }
    single { ClipboardService(get()) }
    single { UndoRedoManager() }
    single { EditorInputHandler(get(), get(), get(), get()) }
    single { StringManager() }
    single { SettingsManager(get(), get(), get()) }
    single { TrickManager() }
    single { BootManager(get(), get(), get(), get()) }
}

val engineModule = module {
    single<IInputBuffer> { InputBuffer() }
    single { ShaderLoader(false) }
    single { VAOLoader() }
    single { AssimpLoader() }
    single { ResourceManager(get(), get(), get(), get()) }
    single { PoseSerializer() }

    single { DebugRenderer() }
    single { PickingRenderer() }

    single { ThumbnailCache() }
    single { PrefabsGenerator(get(), get()) }
    single { SplashScreen() }

    // Render resources factory - created lazily when Renderer is requested
    single { RenderResourcesFactory(get(), get(), get(), get(), get()) }

    // Renderer is created with the factory, initialization happens in BootManager
    single { Renderer(get()) }

    single { ImGuiLayer(get(), get(), get(), get(), get(), get(), get(), get()) }
}

val inputModule = module {
    single { JoystickListener(get()) }
    single { KeyListener() }
    single { MouseListener() }
    single<IInputProvider> { InputProvider(get(), get()) }
}
