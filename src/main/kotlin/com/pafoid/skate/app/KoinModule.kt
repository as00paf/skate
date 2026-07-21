package com.pafoid.skate.app

import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.utils.DefaultJobSystem
import com.pafoid.skate.engine.utils.IJobSystem
import org.koin.dsl.module

val engineModule = module {
    // Core
    single<IJobSystem> { DefaultJobSystem() }
    single { StringManager() }
    single { EventSystem() }
    single { LoggerService() }
    single { Serializer() }

    //Engine
    single {
        Engine(
            serializer = get(),
            jobSystem = get(),
            logger = get(),
            eventSystem = get(),
        )
    }
}
