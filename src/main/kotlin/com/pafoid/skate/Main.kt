package com.pafoid.skate

import com.pafoid.skate.engine.Window
import com.pafoid.skate.engine.di.appModule
import com.pafoid.skate.engine.di.engineModule
import com.pafoid.skate.engine.di.inputModule
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