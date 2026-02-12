package com.pafoid.skate.engine.di

import com.pafoid.skate.engine.Engine
import com.pafoid.skate.engine.assets.PoseSerializer
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import com.pafoid.skate.engine.assets.loaders.ShaderLoader
import com.pafoid.skate.engine.controls.input.IInputBuffer
import com.pafoid.skate.engine.controls.input.IInputProvider
import com.pafoid.skate.engine.controls.input.InputBuffer
import com.pafoid.skate.engine.controls.input.InputProvider
import com.pafoid.skate.engine.controls.listeners.JoystickListener
import com.pafoid.skate.engine.controls.listeners.KeyListener
import com.pafoid.skate.engine.controls.listeners.MouseListener
import com.pafoid.skate.engine.editor.EditorInputHandler
import com.pafoid.skate.engine.editor.ThumbnailCache
import com.pafoid.skate.engine.editor.UndoRedoManager
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.imgui.ImGuiLayer
import com.pafoid.skate.engine.prefabs.PrefabsGenerator
import com.pafoid.skate.engine.render.DebugDraw
import com.pafoid.skate.engine.render.PickingDraw
import com.pafoid.skate.engine.render.Renderer
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.scenes.ClipboardService
import com.pafoid.skate.engine.scenes.LevelManager
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.utils.SettingsManager
import com.pafoid.skate.engine.utils.StringManager
import com.pafoid.skate.engine.utils.TrickManager
import com.pafoid.skate.engine.utils.serialization.Serializer
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
}

val engineModule = module {
    single<IInputBuffer> { InputBuffer() }
    single { ShaderLoader(false) }
    single { VAOLoader() }
    single { AssimpLoader() }
    single { ResourceManager(get(), get(), get(), get()) }
    single { PoseSerializer() }

    single { DebugDraw() }
    single { PickingDraw() }
    single { ThumbnailCache() }
    single { PrefabsGenerator(get(), get()) }

    single { Renderer(get(), get(), get(), get(), get(), get()) }
    single { ImGuiLayer(get(), get(), get(), get(), get(), get(), get(), get()) }
}

val inputModule = module {
    single { JoystickListener(get()) }
    single { KeyListener() }
    single { MouseListener() }
    single<IInputProvider> { InputProvider(get(), get()) }
}
