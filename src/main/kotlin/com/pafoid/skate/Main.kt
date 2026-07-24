package com.pafoid.skate

import com.pafoid.skate.app.EditorScreen
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.Window

fun main(args:Array<String>){
    val engine = Engine()
    val window = Window(title = "PAFSK8", windowIcon = Assets.Textures.APP_ICON)

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