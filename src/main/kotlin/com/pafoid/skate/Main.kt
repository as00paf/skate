package com.pafoid.skate

import com.pafoid.skate.app.EditorScreen
import com.pafoid.skate.app.appModule
import com.pafoid.skate.app.engineModule
import com.pafoid.skate.app.runtimeAdapterModule
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.Window
import org.koin.core.context.startKoin

fun main(args: Array<String>) {
    val editorMode = "--editor" in args

    val modules = if (editorMode) listOf(engineModule, runtimeAdapterModule, appModule)
                  else listOf(engineModule, runtimeAdapterModule)
    val app = startKoin { modules(modules) }

    val engine = app.koin.get<Engine>()
    val window = Window(title = "PAFSK8", windowIcon = Assets.Textures.APP_ICON)

    engine.start()

    if (editorMode) {
        val editorScreen = EditorScreen(window)
        editorScreen.init()
        window.show { dt ->
            engine.update(dt)
            editorScreen.update(dt)
        }
        editorScreen.destroy()
    } else {
        window.show { dt -> engine.update(dt) }
    }

    engine.destroy()
}