package com.pafoid.skate.engine.ecs.components

/**
 * Bounds and drag step of a numeric (`Int`, `Long`, `Float`, `Double`) component field. The generic and the runtime-UI
 * inspectors clamp edits to [min]..[max] and drag by [step]; a [step] of 0 keeps the inspector's default speed.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Range(val min: Double = -Double.MAX_VALUE, val max: Double = Double.MAX_VALUE, val step: Double = 0.0)
