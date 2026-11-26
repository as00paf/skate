package com.pafoid.skate.architecture

import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.components.Component
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModuleCollector
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.reflect.KClass

/**
 * QA-20: every serialized component field must have an inspector entry, a label and a tooltip, or be listed (with a
 * reason, owner and task) in `component-inspector-allowlist.txt`. Three inspector kinds exist (see `Component.kt` in
 * `editor/imgui/components`): a dedicated one per component class, the runtime-UI reflective one (`ui.inspector.*`)
 * and the generic reflective one (`component.<Type>.<field>`). The rules below mirror what each can draw.
 *
 * For dedicated inspectors the check is textual (the field name appears in the inspector source, and a string key
 * ending in `.<field>` and `.<field>.tooltip` exists); the allowlist entry is `Type.field`, optionally `Type.*`.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class) // SerializersModuleCollector, descriptor element access
class ComponentInspectorCoverageGuardTest {

    private val root = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
    private val src = root.resolve("src/main/kotlin/com/pafoid/skate")
    private val inspectorDir = src.resolve("editor/imgui/components")
    private val headerFields = setOf("enabled", "name")

    private class Gap(val component: String, val field: String, val problem: String) {
        val key get() = "$component.$field"
        override fun toString() = "$key: $problem"
    }

    private fun registeredComponents(): List<KClass<out Component>> {
        val found = ArrayList<KClass<out Component>>()
        Serializer().engineModule.dumpTo(object : SerializersModuleCollector {
            override fun <T : Any> contextual(kClass: KClass<T>, serializer: KSerializer<T>) {}
            override fun <T : Any> contextual(kClass: KClass<T>, provider: (typeArgumentsSerializers: List<KSerializer<*>>) -> KSerializer<*>) {}
            override fun <Base : Any, Sub : Base> polymorphic(baseClass: KClass<Base>, actualClass: KClass<Sub>, actualSerializer: KSerializer<Sub>) {
                if (baseClass == Component::class) {
                    @Suppress("UNCHECKED_CAST")
                    found += actualClass as KClass<out Component>
                }
            }
            override fun <Base : Any> polymorphicDefaultSerializer(baseClass: KClass<Base>, defaultSerializerProvider: (value: Base) -> kotlinx.serialization.SerializationStrategy<Base>?) {}
            override fun <Base : Any> polymorphicDefaultDeserializer(baseClass: KClass<Base>, defaultDeserializerProvider: (className: String?) -> kotlinx.serialization.DeserializationStrategy<Base>?) {}
        })
        return found.sortedBy { it.java.simpleName }
    }

    private fun stringKeys(): Set<String> {
        val keys = HashSet<String>()
        val dir = root.resolve("src/main/resources/editor/strings")
        Files.list(dir).use { files ->
            files.filter { it.fileName.toString().endsWith(".properties") && !it.fileName.toString().endsWith("_fr.properties") }
                .forEach { f -> Files.newBufferedReader(f).use { r -> Properties().also { p -> p.load(r) }.keys.forEach { keys += it as String } } }
        }
        return keys
    }

    private fun serializedFields(c: Class<*>): List<Field> {
        val names = kotlinx.serialization.serializer(c).descriptor.let { d -> (0 until d.elementsCount).map(d::getElementName) }
        val chain = generateSequence(c) { it.superclass }.takeWhile { it != Component::class.java && it != Any::class.java }.toList()
        val fields = chain.flatMap { it.declaredFields.toList() }
        return names.filter { it !in headerFields }.mapNotNull { n -> fields.firstOrNull { it.name == n } }
    }

    private fun dedicatedInspectors(): Set<String> =
        Regex("""is (\w+) -> this\.imgui\(engine\)""").findAll(inspectorDir.resolve("Component.kt").readText())
            .map { it.groupValues[1] }.toSet()

    /** Source of the file declaring `fun <type>.imgui(engine)`; its name does not always match the component's. */
    private fun dedicatedSource(type: String): String? = Files.list(inspectorDir).use { files ->
        files.filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }.map { it.readText() }
            .filter { Regex("""fun $type\.imgui\(""").containsMatchIn(it) }.findFirst().orElse(null)
    }

    private fun isFinal(f: Field) = Modifier.isFinal(f.modifiers)

    /** What the generic reflective inspector (`Component.reflectedImgui`) draws. */
    private fun genericDraws(f: Field): Boolean = when (f.type) {
        Int::class.javaPrimitiveType, Float::class.javaPrimitiveType, Boolean::class.javaPrimitiveType -> !isFinal(f)
        String::class.java -> !isFinal(f)
        Vector2f::class.java, Vector3f::class.java, Vector4f::class.java -> true
        else -> f.type.isEnum && !isFinal(f)
    }

    /** What the runtime-UI inspector (`Component.uiImgui`) draws. */
    private fun uiDraws(f: Field): Boolean {
        val t = f.type
        if (t == List::class.java && f.genericType.toString().contains("UiListItem")) return true // `listItems` section
        if (isFinal(f)) return false
        return t == Int::class.javaPrimitiveType || t == Float::class.javaPrimitiveType || t == Boolean::class.javaPrimitiveType ||
            t == String::class.java || t == Integer::class.java || t == Vector4f::class.java || t.isEnum ||
            (t == List::class.java && f.genericType.toString().contains("String")) ||
            t.name.startsWith("com.pafoid.skate.engine.ui.")
    }

    private fun wordIn(text: String, word: String) = Regex("""\b${Regex.escape(word)}\b""").containsMatchIn(text)

    private fun findGaps(): List<Gap> {
        val keys = stringKeys()
        val dedicated = dedicatedInspectors()
        val gaps = ArrayList<Gap>()
        for (kc in registeredComponents()) {
            val c = kc.java
            val type = c.simpleName
            val isUi = c.name.startsWith("com.pafoid.skate.engine.ui.")
            val text = if (type in dedicated) dedicatedSource(type) else null
            val delegates = text?.contains("reflectedImgui(") == true
            for (f in serializedFields(c)) {
                val name = f.name
                when {
                    isUi -> {
                        if (!uiDraws(f)) gaps += Gap(type, name, "no widget for ${f.genericType.typeName} in the UI inspector")
                        if ("ui.inspector.$name" !in keys) gaps += Gap(type, name, "missing label ui.inspector.$name")
                        if ("ui.inspector.$name.tooltip" !in keys) gaps += Gap(type, name, "missing tooltip ui.inspector.$name.tooltip")
                    }
                    text != null && !(delegates && genericDraws(f)) -> {
                        if (!wordIn(text, name)) gaps += Gap(type, name, "not referenced by the dedicated inspector of $type")
                        if (keys.none { it.endsWith(".$name") }) gaps += Gap(type, name, "no label key ending in .$name")
                        if (keys.none { it.endsWith(".$name.tooltip") }) gaps += Gap(type, name, "no tooltip key ending in .$name.tooltip")
                    }
                    else -> {
                        if (!genericDraws(f)) gaps += Gap(type, name, "no widget for ${f.genericType.typeName} in the generic inspector")
                        if ("component.$type.$name" !in keys) gaps += Gap(type, name, "missing label component.$type.$name")
                        if ("component.$type.$name.tooltip" !in keys) gaps += Gap(type, name, "missing tooltip component.$type.$name.tooltip")
                    }
                }
            }
        }
        return gaps
    }

    private fun allowlist(): Set<String> {
        val file = root.resolve("src/test/resources/architecture/component-inspector-allowlist.txt")
        if (!file.isRegularFile()) return emptySet()
        return file.readText().lineSequence().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toSet()
    }

    private fun allowed(gap: Gap, allow: Set<String>) = gap.key in allow || "${gap.component}.*" in allow

    @Test
    fun ComponentInspectors_EverySerializedFieldHasEntryLabelAndTooltipOrIsAllowlisted() {
        val allow = allowlist()
        val gaps = findGaps()
        val report = root.resolve("build/reports/qa-20-inspector-gaps.txt")
        Files.createDirectories(report.parent)
        Files.writeString(report, gaps.joinToString("\n") { it.toString() })

        val open = gaps.filterNot { allowed(it, allow) }
        assertTrue(open.isEmpty(), "Serialized component fields without a complete inspector entry (fix, or allowlist with owner and task):\n" + open.joinToString("\n"))
    }

    @Test
    fun ComponentInspectors_AllowlistHasNoStaleEntries() {
        val gaps = findGaps()
        val stale = allowlist().filter { entry ->
            gaps.none { it.key == entry || "${it.component}.*" == entry }
        }
        assertTrue(stale.isEmpty(), "Allowlist entries whose gap no longer exists (remove them): $stale")
    }
}
