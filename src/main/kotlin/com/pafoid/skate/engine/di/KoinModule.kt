package com.pafoid.skate.engine.di

import com.pafoid.skate.engine.assets.AssimpLoader
import com.pafoid.skate.engine.assets.ObjLoader
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.ShaderLoader
import com.pafoid.skate.engine.scenes.SceneManager
import org.koin.dsl.module

val engineModule = module {
    single { ShaderLoader(false) }
    single { AssimpLoader() }
    single { ObjLoader() }
    single { ResourceManager(get(), get(), get()) }
    single { SceneManager() }
}