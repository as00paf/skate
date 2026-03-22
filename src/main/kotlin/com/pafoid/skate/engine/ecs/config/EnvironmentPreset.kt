package com.pafoid.skate.engine.ecs.config

/**
 * Preset environment configurations for quick setup.
 */
enum class EnvironmentPreset {
    /** Clear daytime sky with minimal fog */
    CLEAR_DAY,

    /** Overcast cloudy sky */
    CLOUDY,

    /** Dense fog for atmospheric effects */
    FOGGY,

    /** Warm sunset colors */
    SUNSET,

    /** No fog, clear visibility */
    NO_FOG
}