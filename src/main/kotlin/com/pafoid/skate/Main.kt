package com.pafoid.skate

import com.pafoid.skate.app.appModule
import com.pafoid.skate.app.engineModule
import com.pafoid.skate.app.inputModule
import com.pafoid.skate.editor.EditorWorkspace
import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EngineState
import com.pafoid.skate.engine.core.Window
import org.koin.core.context.startKoin

fun main(args:Array<String>){
    val app = startKoin {
        modules(appModule, inputModule, engineModule)
    }
    val engine = app.koin.get<Engine>()
    val workspace = app.koin.get<EditorWorkspace>()
    val imguiLayer = app.koin.get<ImGuiLayer>()

    val window = Window(title = "PAFSK8")

    engine.start()
    workspace.init(window.glfwWindow)
    imguiLayer.init(window.windowController)

    // TODO : clean up
    window.show { dt ->
        val isRunning = engine.engineState.get() == EngineState.RUNNING
        if (isRunning) workspace.update(dt)
        engine.update(dt)
        if (isRunning) imguiLayer.update(dt)
    }

    imguiLayer.destroy()
    workspace.destroy()
    engine.destroy()

}