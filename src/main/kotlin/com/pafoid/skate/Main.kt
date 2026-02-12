package com.pafoid.skate

import com.pafoid.skate.app.appModule
import com.pafoid.skate.app.engineModule
import com.pafoid.skate.app.inputModule
import com.pafoid.skate.engine.core.Window
import org.koin.core.context.startKoin

fun main(args:Array<String>){
    startKoin {
        modules(appModule, inputModule, engineModule)
    }

    val window = Window(
        width = 512,
        height = 512,
        title ="PAFSK8"
    )
    window.run()
}