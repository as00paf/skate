package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.components.FieldKind
import com.pafoid.skate.editor.imgui.components.usesReflectedInspector
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.StringTables
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModuleCollector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.lang.reflect.Field
import kotlin.reflect.KClass

/**
 * EDT-107: the generic inspector skips no saved component field. Every saved field of a component drawn by
 * `reflectedImgui` alone, and of the objects nested in its lists, maps and fields, has a widget ([FieldKind]) or is
 * listed below with the reason it is not editable.
 */
class ReflectedInspectorGuardTest {

    @OptIn(ExperimentalSerializationApi::class) // SerializersModule.dumpTo is the only way to list the registered components
    private fun reflectedComponents(): List<Class<*>> {
        val classes = ArrayList<Class<*>>()
        Serializer().engineModule.dumpTo(object : SerializersModuleCollector {
            override fun <T : Any> contextual(kClass: KClass<T>, serializer: KSerializer<T>) {}
            override fun <T : Any> contextual(kClass: KClass<T>, provider: (typeArgumentsSerializers: List<KSerializer<*>>) -> KSerializer<*>) {}
            override fun <Base : Any, Sub : Base> polymorphic(baseClass: KClass<Base>, actualClass: KClass<Sub>, actualSerializer: KSerializer<Sub>) {
                classes.add(actualClass.java)
            }
            override fun <Base : Any> polymorphicDefaultSerializer(baseClass: KClass<Base>, defaultSerializerProvider: (value: Base) -> SerializationStrategy<Base>?) {}
            override fun <Base : Any> polymorphicDefaultDeserializer(baseClass: KClass<Base>, defaultDeserializerProvider: (className: String?) -> DeserializationStrategy<Base>?) {}
        })
        return classes.filter(::usesReflectedInspector)
    }

    /** `Owner.field` of every saved field reachable from [type] that [FieldKind] cannot draw, and each nested class used. */
    private fun walk(type: Class<*>, owner: String, seen: MutableSet<Class<*>>, problems: MutableList<String>, nested: MutableSet<Class<*>>) {
        for (f in FieldKind.savedFields(type)) {
            val name = "$owner.${f.name}"
            if (FieldKind.isHidden(type, f)) continue
            when (val kind = FieldKind.of(f.genericType)) {
                is FieldKind.Unsupported -> problems.add("$name: ${kind.reason}")
                else -> descend(kind, name, seen, problems, nested)
            }
        }
    }

    private fun descend(kind: FieldKind, name: String, seen: MutableSet<Class<*>>, problems: MutableList<String>, nested: MutableSet<Class<*>>) {
        val inner = when (kind) {
            is FieldKind.ListOf -> FieldKind.of(kind.element)
            is FieldKind.MapOf -> FieldKind.of(kind.value)
            else -> kind
        }
        when (inner) {
            is FieldKind.Unsupported -> problems.add("$name: ${inner.reason}")
            is FieldKind.Nested -> if (seen.add(inner.type)) {
                nested.add(inner.type)
                walk(inner.type, inner.type.simpleName, seen, problems, nested)
            }
            is FieldKind.ListOf, is FieldKind.MapOf -> descend(inner, name, seen, problems, nested)
            else -> {}
        }
    }

    @Test
    fun ReflectedInspector_SkipsNoSavedField() {
        val components = reflectedComponents()
        assertTrue(components.size > 20, "found ${components.size} components")
        val problems = ArrayList<String>()
        val seen = HashSet<Class<*>>()
        for (c in components) {
            val declared = c.declaredFields.toSet()
            // reflectedImgui reads the class's own declared fields only.
            FieldKind.savedFields(c).filter { it !in declared && !FieldKind.isHidden(c, it) }.forEach { problems.add("${c.simpleName}.${it.name}: inherited, not drawn") }
            walk(c, c.simpleName, seen, problems, HashSet())
        }
        assertEquals(emptyList<String>(), problems.sorted())
    }

    @Test
    fun ReflectedInspector_ListMapAndNestedFieldsHaveLabelsAndTooltips() {
        val keys = StringTables.merged(File(Assets.Strings.EDITOR_STRINGS)).stringPropertyNames()
        val missing = ArrayList<String>()
        fun need(typeName: String, f: Field) {
            val key = "component.$typeName.${f.name}"
            if (key !in keys) missing.add(key)
            if ("$key.tooltip" !in keys) missing.add("$key.tooltip")
        }
        val nested = LinkedHashSet<Class<*>>()
        val seen = HashSet<Class<*>>()
        for (c in reflectedComponents()) {
            for (f in FieldKind.savedFields(c)) {
                if (!FieldKind.isHidden(c, f) && FieldKind.of(f.genericType) !is FieldKind.Unsupported && isStructured(f)) need(c.simpleName, f)
            }
            walk(c, c.simpleName, seen, ArrayList(), nested)
        }
        for (type in nested) FieldKind.nestedFields(type).forEach { need(type.simpleName, it) }
        assertEquals(emptyList<String>(), missing.sorted())
    }

    private fun isStructured(f: Field) = FieldKind.of(f.genericType).let { it is FieldKind.ListOf || it is FieldKind.MapOf || it is FieldKind.Nested }
}
