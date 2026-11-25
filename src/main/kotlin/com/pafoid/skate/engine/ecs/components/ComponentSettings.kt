package com.pafoid.skate.engine.ecs.components

import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.lang.reflect.Modifier

/**
 * A comparable snapshot of a component's own settings (ECS-21): the values of its declared instance fields plus
 * `enabled`, with mutable vectors copied. Systems compare it frame to frame to apply an inspector edit at once instead
 * of easing toward it. [skip] names runtime fields (e.g. `state`) that are not settings.
 */
object ComponentSettings {
    fun of(component: Component, skip: Set<String> = emptySet()): List<Any?> {
        val out = ArrayList<Any?>()
        out += component.enabled
        for (field in Component.getCachedFields(component.javaClass)) {
            if (Modifier.isStatic(field.modifiers) || field.name in skip) continue
            field.isAccessible = true
            out += when (val v = field.get(component)) {
                is Vector2f -> Vector2f(v)
                is Vector3f -> Vector3f(v)
                is Vector4f -> Vector4f(v)
                is Quaternionf -> Quaternionf(v)
                else -> v
            }
        }
        return out
    }
}
