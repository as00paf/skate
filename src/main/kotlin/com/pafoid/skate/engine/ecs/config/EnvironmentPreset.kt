package com.pafoid.skate.engine.ecs.config

enum class EnvironmentPreset {
    CLEAR_DAY,

    /** Clear daytime sky with minimal fog */
    CLOUDY,

    /** Overcast cloudy sky */
    FOGGY,

    /** Dense fog for atmospheric effects */
    SUNSET,

    /** Warm sunset colors */
    NO_FOG
    /** No fog, clear visibility */
}