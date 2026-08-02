package com.pafoid.skate

import com.pafoid.skate.app.EditorScreen
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.Window

fun main(args:Array<String>){
    val engine = Engine()
    val icon = Engine::class.java.getResourceAsStream("/app_icon.png")?.readAllBytes()
    val window = Window(title = "Skate Editor", icon = icon)

    engine.start(window.glfwWindow)

    val editorScreen = EditorScreen(window, engine)
    editorScreen.init()

    window.show { dt ->
        engine.update(dt)
        editorScreen.update(dt)
    }

    editorScreen.destroy()
    engine.destroy()
}