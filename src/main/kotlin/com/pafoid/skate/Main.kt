package com.pafoid.skate

import com.pafoid.skate.app.appModule
import com.pafoid.skate.app.engineModule
import com.pafoid.skate.app.inputModule
import com.pafoid.skate.engine.core.Engine
import org.koin.core.context.startKoin

fun main(args:Array<String>){
    val app = startKoin {
        modules(appModule, inputModule, engineModule)
    }

    val engine = app.koin.get<Engine>()
    engine.start()
}