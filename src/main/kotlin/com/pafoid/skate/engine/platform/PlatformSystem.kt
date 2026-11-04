package com.pafoid.skate.engine.platform

import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemPhase

/** Pumps [PlatformServices] callbacks once per frame, before gameplay systems run. */
class PlatformSystem(val services: PlatformServices) : System() {
    override val phase: SystemPhase get() = SystemPhase.PRE_UPDATE

    override fun update(dt: Float) = services.update()

    override fun destroy() {
        services.shutdown()
        super.destroy()
    }
}
