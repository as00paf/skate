package com.pafoid.skate.engine.di

import com.pafoid.skate.engine.assets.AssimpLoader
import com.pafoid.skate.engine.assets.ObjLoader
import com.pafoid.skate.engine.assets.PoseSerializer
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.ShaderLoader
import com.pafoid.skate.engine.controls.input.IInputProvider
import com.pafoid.skate.engine.controls.input.InputProvider
import com.pafoid.skate.engine.controls.listeners.JoystickListener
import com.pafoid.skate.engine.controls.listeners.KeyListener
import com.pafoid.skate.engine.controls.listeners.MouseListener
import com.pafoid.skate.engine.editor.ThumbnailCache
import com.pafoid.skate.engine.editor.UndoRedoManager
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.render.DebugDraw
import com.pafoid.skate.engine.render.PickingDraw
import com.pafoid.skate.engine.scenes.ClipboardService
import com.pafoid.skate.engine.prefabs.PrefabsGenerator
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.utils.SettingsManager
import com.pafoid.skate.engine.utils.serialization.Serializer
import org.koin.dsl.module

val appModule = module {
    single { SceneManager() }
    single { Serializer() }
    single { LoggerService() }
    single { ClipboardService(get()) }
    single { UndoRedoManager() }
    single { SettingsManager(get(), get()) }
}

val engineModule = module {
    single { PoseSerializer() }
    single { ShaderLoader(false) }
    single { AssimpLoader() }
    single { ObjLoader() }
    single { ResourceManager(get(), get(), get(), get()) }

    single { DebugDraw() }
    single { PickingDraw() }
    single { ThumbnailCache() }
    single { PrefabsGenerator(get(), get()) }
}

val inputModule = module {
    single { JoystickListener() }
    single { KeyListener() }
    single { MouseListener() }
    single<IInputProvider> { InputProvider(get(), get()) }
}
