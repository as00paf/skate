package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.engine.ecs.components.Component
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

/**
 * How the generic inspector draws a saved field (EDT-107). Free of ImGui so a guard test can ask, for every component,
 * which saved fields have no widget instead of letting the inspector skip them silently.
 */
sealed interface FieldKind {
    object IntField : FieldKind
    object LongField : FieldKind
    object FloatField : FieldKind
    object DoubleField : FieldKind
    object BoolField : FieldKind
    object TextField : FieldKind
    object Vec2Field : FieldKind
    object Vec3Field : FieldKind
    object Vec4Field : FieldKind
    data class EnumValue(val type: Class<*>) : FieldKind
    data class ListOf(val element: Type) : FieldKind
    data class MapOf(val value: Type) : FieldKind

    /** A `@Serializable` class drawn field by field. */
    data class Nested(val type: Class<*>) : FieldKind

    /** A raw JSON value (prefab overrides): shown as text, removable with its map entry. */
    object JsonValue : FieldKind

    /** A primitive or object array: shown read-only as its length (arrays are baked or replicated data). */
    data class ArrayOf(val element: Class<*>) : FieldKind

    /** No widget. */
    data class Unsupported(val reason: String) : FieldKind

    companion object {
        /** Saved fields hidden on purpose (`Class.field` to the reason); the guard test allows exactly these. */
        val NOT_EDITABLE: Map<String, String> = mapOf(
            "NetState.values" to "replicated network state, written by the network layer",
            "SkeletonComponent.pose" to "bone matrices driven by the Animator; edited in the Animation Editor",
        )

        fun isHidden(owner: Class<*>, field: Field): Boolean = "${owner.simpleName}.${field.name}" in NOT_EDITABLE

        /** Fields of a component class that carry saved values, as the scene file saves them (base [Component] state excluded). */
        fun savedFields(type: Class<*>): List<Field> = com.pafoid.skate.editor.commands.objects.ComponentValues.fields(type)
            .filter { it.declaringClass != Component::class.java }

        fun of(type: Type): FieldKind {
            val raw = rawClass(type) ?: return Unsupported("unresolved type $type")
            return when {
                raw == Int::class.javaPrimitiveType || raw == java.lang.Integer::class.java -> IntField
                raw == Long::class.javaPrimitiveType || raw == java.lang.Long::class.java -> LongField
                raw == Float::class.javaPrimitiveType || raw == java.lang.Float::class.java -> FloatField
                raw == Double::class.javaPrimitiveType || raw == java.lang.Double::class.java -> DoubleField
                raw == Boolean::class.javaPrimitiveType || raw == java.lang.Boolean::class.java -> BoolField
                raw == String::class.java -> TextField
                raw == Vector2f::class.java -> Vec2Field
                raw == Vector3f::class.java -> Vec3Field
                raw == Vector4f::class.java -> Vec4Field
                JsonElement::class.java.isAssignableFrom(raw) -> JsonValue
                raw.isArray -> ArrayOf(raw.componentType)
                raw.isEnum -> EnumValue(raw)
                List::class.java.isAssignableFrom(raw) -> ListOf(typeArgument(type, 0))
                Map::class.java.isAssignableFrom(raw) -> {
                    if (rawClass(typeArgument(type, 0)) != String::class.java) Unsupported("map keys must be String")
                    else MapOf(typeArgument(type, 1))
                }
                raw.isAnnotationPresent(Serializable::class.java) && !raw.isInterface && !Modifier.isAbstract(raw.modifiers) -> Nested(raw)
                else -> Unsupported("no widget for ${raw.simpleName}")
            }
        }

        fun rawClass(type: Type): Class<*>? = when (type) {
            is Class<*> -> type
            is ParameterizedType -> type.rawType as? Class<*>
            is WildcardType -> type.upperBounds.firstOrNull()?.let(::rawClass)
            else -> null
        }

        /** Type argument [index] of a generic [type] (`List<T>` → T), unwrapping Kotlin's `? extends` wildcards. */
        fun typeArgument(type: Type, index: Int): Type {
            val argument = (type as? ParameterizedType)?.actualTypeArguments?.getOrNull(index) ?: return Any::class.java
            return if (argument is WildcardType) argument.upperBounds.firstOrNull() ?: Any::class.java else argument
        }

        /** Saved fields of the class a [Nested] value is drawn from. */
        fun nestedFields(type: Class<*>): List<Field> = com.pafoid.skate.editor.commands.objects.ComponentValues.fields(type)
    }
}
