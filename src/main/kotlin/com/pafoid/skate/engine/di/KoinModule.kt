package com.pafoid.skate.engine.di

import com.pafoid.skate.engine.assets.AssimpLoader
import com.pafoid.skate.engine.assets.ObjLoader
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.ShaderLoader
import com.pafoid.skate.engine.editor.ThumbnailCache
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.render.DebugDraw
import com.pafoid.skate.engine.render.PickingDraw
import com.pafoid.skate.engine.scenes.SceneManager
import org.koin.dsl.module

val engineModule = module {
    single { LoggerService() }
    single { ShaderLoader(false) }
    single { AssimpLoader() }
    single { ObjLoader() }
    single { ResourceManager(get(), get(), get()) }
    single { ThumbnailCache() }
    single { SceneManager() }
    single { DebugDraw() }
    single { DebugDraw() }
    single { PickingDraw() }
}