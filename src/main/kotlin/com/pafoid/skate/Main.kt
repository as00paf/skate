package com.pafoid.skate

import com.pafoid.skate.engine.Window
import com.pafoid.skate.engine.di.appModule
import com.pafoid.skate.engine.di.engineModule
import com.pafoid.skate.engine.scenes.SceneManager
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

fun main(args:Array<String>){
    startKoin {
        modules(appModule, engineModule)
    }

    val sceneManager = GlobalContext.get().get<SceneManager>()
    val window = Window(
        width = 512,
        height = 512,
        title ="PAFSK8",
        initCallback = sceneManager::initializeScene,
        drawCallback = sceneManager::draw,
        destroyCallback = sceneManager::destroy
    )
    window.run()
}