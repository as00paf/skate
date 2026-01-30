package com.pafoid.skate

import com.pafoid.skate.engine.Window
import com.pafoid.skate.engine.scenes.SceneManager

fun main(args:Array<String>){
    val sceneManager = SceneManager.get()
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