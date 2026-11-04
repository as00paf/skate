package com.pafoid.skate.engine.ecs.config

/** How fog thickens with distance (REN-09); see `shaders/fog.glsl`. */
enum class FogMode {
    /** Ramps from none at `fogStart` to full at `fogEnd` (metres). */
    LINEAR,

    /** 1 - e^(-density * d). */
    EXP,

    /** 1 - e^(-(density * d)^2): clear near the camera, then thickens quickly. */
    EXP2,
}
