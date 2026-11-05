package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Component
import kotlinx.serialization.serializer
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * The saved value fields of a component (EDT-10): numbers, booleans, strings, enums, JOML vectors and lists of those.
 * Used to snapshot a component around its inspector and to restore values on undo. Object fields (models,
 * materials, textures) are not values; their own commands cover them.
 */
object ComponentValues {
    private val fieldsByClass = ConcurrentHashMap<Class<*>, List<Field>>()

    /** Serialized fields of [type] (by its kotlinx descriptor), else its non-transient, non-static fields. */
    fun fields(type: Class<*>): List<Field> = fieldsByClass.getOrPut(type) {
        val names: Set<String>? = runCatching {
            val descriptor = serializer(type).descriptor
            (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }.toSet()
        }.getOrNull()
        generateSequence<Class<*>>(type) { it.superclass }.takeWhile { it != Any::class.java }
            .flatMap { it.declaredFields.asSequence() }
            .filter { !Modifier.isStatic(it.modifiers) && !Modifier.isTransient(it.modifiers) && !it.isSynthetic }
            .filter { names == null || it.name in names }
            .distinctBy { it.name }
            .onEach { it.isAccessible = true }
            .toList()
    }

    fun isValue(value: Any?): Boolean = when (value) {
        null, is Boolean, is Number, is String, is Char, is Enum<*> -> true
        is Vector2f, is Vector3f, is Vector4f, is Quaternionf -> true
        is List<*> -> value.all { isValue(it) && it !is List<*> }
        else -> false
    }

    /** A copy of [value] that later edits of the original cannot change. */
    fun copy(value: Any?): Any? = when (value) {
        is Vector2f -> Vector2f(value)
        is Vector3f -> Vector3f(value)
        is Vector4f -> Vector4f(value)
        is Quaternionf -> Quaternionf(value)
        is List<*> -> value.map { copy(it) }
        else -> value
    }

    /** Copies of every value field of [component], by field. */
    fun snapshot(component: Component, ignored: Set<String> = emptySet()): Map<Field, Any?> =
        fields(component.javaClass).filter { it.name !in ignored }.mapNotNull { f ->
            val v = f.get(component)
            if (isValue(v)) f to copy(v) else null
        }.toMap()

    /**
     * Writes [value] into [component]. JOML vectors are set in place, so code holding the vector sees the change.
     * A mutable list is refilled in place for the same reason.
     */
    @Suppress("UNCHECKED_CAST")
    fun write(component: Component, field: Field, value: Any?) {
        val current = field.get(component)
        when {
            current is Vector2f && value is Vector2f -> current.set(value)
            current is Vector3f && value is Vector3f -> current.set(value)
            current is Vector4f && value is Vector4f -> current.set(value)
            current is Quaternionf && value is Quaternionf -> current.set(value)
            current is MutableList<*> && value is List<*> && Modifier.isFinal(field.modifiers) ->
                (current as MutableList<Any?>).apply { clear(); addAll(value.map { copy(it) }) }
            else -> field.set(component, copy(value))
        }
    }

    /** Scene that owns [component] (top of its parent chain). */
    fun sceneOf(component: Component): Scene? =
        generateSequence<GameObject>(component.gameObject) { it.parent }.filterIsInstance<Scene>().firstOrNull()
}
