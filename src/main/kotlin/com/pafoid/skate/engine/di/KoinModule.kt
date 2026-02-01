package com.pafoid.skate.engine.di

import com.pafoid.skate.engine.assets.AssimpLoader
import com.pafoid.skate.engine.assets.ObjLoader
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.ShaderLoader
import com.pafoid.skate.engine.controls.input.IInputProvider
import com.pafoid.skate.engine.controls.input.InputProvider
import com.pafoid.skate.engine.controls.listeners.JoystickListener
import com.pafoid.skate.engine.editor.ThumbnailCache
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.render.DebugDraw
import com.pafoid.skate.engine.render.PickingDraw
import com.pafoid.skate.engine.scenes.SceneManager
import org.koin.dsl.module

val engineModule = module {
    single { ShaderLoader(false) }
    single { AssimpLoader() }
    single { ObjLoader() }
    single { ResourceManager(get(), get(), get(), get()) }
    single { ThumbnailCache() }
    single { DebugDraw() }
    single { PickingDraw() }

    single { JoystickListener() }
    single<IInputProvider> { InputProvider(get()) }
}

val appModule = module {
    single { SceneManager() }
    single { LoggerService() }
}