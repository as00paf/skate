package com.pafoid.skate.engine.physics3d.water

/** How water treats a rigid body (PHY-16, `docs/design/REN-29.md` §8.3). */
enum class BuoyancyMode {
    /** Buoyancy and drag, plus water events. A body with a `PlayerController` is treated as [CONTROLLER]. */
    AUTO,

    /** Water ignores the body: no force, no events. */
    OFF,

    /** Water events only; the body's controller handles water itself (bail now, swimming later). */
    CONTROLLER,
}
