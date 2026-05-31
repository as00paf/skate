package com.pafoid.skate

import com.pafoid.skate.app.EditorScreen
import com.pafoid.skate.app.appModule
import com.pafoid.skate.app.engineModule
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.Window
import org.koin.core.context.startKoin

fun main(args:Array<String>){
    val app = startKoin {
        modules(engineModule, appModule)
    }
    val engine = app.koin.get<Engine>()
    val window = Window(title = "PAFSK8", windowIcon = Assets.Textures.APP_ICON)

    engine.start()

    val editorScreen = EditorScreen(window)
    editorScreen.init()

    window.show { dt ->
        engine.update(dt)
        editorScreen.update(dt)
    }

    editorScreen.destroy()
    engine.destroy()
}