package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.imgui.components.FieldKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Component
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.serializer
import kotlinx.serialization.SerializationException
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
    @OptIn(ExperimentalSerializationApi::class) // serializer(Class) is the only lookup for a runtime Class
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

    /** JSON of a list, map or nested object (EDT-107), so a deep edit is one comparable before/after value. */
    data class Structure(val json: String)

    private val json by lazy { Serializer().json }

    private fun isStructured(field: Field) = FieldKind.of(field.genericType).let { it is FieldKind.ListOf || it is FieldKind.MapOf || it is FieldKind.Nested }

    private fun encode(field: Field, value: Any): Structure =
        Structure(json.encodeToString(json.serializersModule.serializer(field.genericType), value))

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

    /** The current value of [field] as [snapshot] keeps it: a copy for values, a [Structure] for lists, maps and nested objects. */
    fun capture(component: Component, field: Field): Any? {
        val v = field.get(component)
        return if (isValue(v) || v == null) copy(v) else runCatching { encode(field, v) }.getOrNull()
    }

    /** Copies of every value field of [component], by field. */
    fun snapshot(component: Component, ignored: Set<String> = emptySet(), structured: Boolean = false): Map<Field, Any?> =
        fields(component.javaClass).filter { it.name !in ignored }.mapNotNull { f ->
            val v = f.get(component)
            when {
                isValue(v) -> f to copy(v)
                structured && v != null && isStructured(f) -> runCatching { f to encode(f, v) }.getOrNull()
                else -> null
            }
        }.toMap()

    /** Restores a [Structure]: lists and maps are refilled in place, a final nested object is copied field by field. */
    @Suppress("UNCHECKED_CAST")
    private fun writeStructure(component: Component, field: Field, current: Any?, value: Structure) {
        val decoded: Any? = try {
            json.decodeFromString(json.serializersModule.serializer(field.genericType), value.json)
        } catch (e: SerializationException) {
            return
        }
        when {
            current is MutableList<*> && decoded is List<*> -> (current as MutableList<Any?>).apply { clear(); addAll(decoded) }
            current is MutableMap<*, *> && decoded is Map<*, *> -> (current as MutableMap<Any?, Any?>).apply { clear(); putAll(decoded) }
            current != null && decoded != null && Modifier.isFinal(field.modifiers) ->
                FieldKind.nestedFields(current.javaClass).filter { !Modifier.isFinal(it.modifiers) }.forEach { it.set(current, it.get(decoded)) }
            else -> field.set(component, decoded)
        }
    }

    /**
     * Writes [value] into [component]. JOML vectors are set in place, so code holding the vector sees the change.
     * A mutable list is refilled in place for the same reason.
     */
    @Suppress("UNCHECKED_CAST")
    fun write(component: Component, field: Field, value: Any?) {
        val current = field.get(component)
        if (value is Structure) return writeStructure(component, field, current, value)
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
