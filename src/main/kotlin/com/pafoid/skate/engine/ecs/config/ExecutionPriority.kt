package com.pafoid.skate.engine.ecs.config

/**
 * Execution priority for ECS systems.
 * Systems are updated in priority order (EARLY first, LATE last).
 */
enum class ExecutionPriority {
    EARLY,      // Input, timing systems
    DEFAULT,    // Physics, animation systems
    LATE        // Rendering, UI systems
}