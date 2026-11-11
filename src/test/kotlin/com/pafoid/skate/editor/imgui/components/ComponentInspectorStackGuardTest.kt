package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.ImGuiStyleManager
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.loadFonts
import com.pafoid.skate.editor.imgui.theme.ThemeLibrary
import com.pafoid.skate.editor.settings.AppearanceSettings
import com.pafoid.skate.editor.settings.Density
import com.pafoid.skate.editor.settings.EditorSettings
import com.pafoid.skate.editor.settings.Spacing
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.SceneComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.data.models.animations.SkeletonPose
import imgui.ImGui
import imgui.assertion.ImAssertCallback
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModuleCollector
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

/**
 * EDT-87 guard: every registered component's inspector, expanded in a Properties-like window, with default and with
 * populated data, at every density preset (plus the largest custom padding) and the smallest and largest font sizes.
 * Unbalanced Push/Pop, Begin/End, ID stack or table/column use trips an ImGui assert when the window ends; with no
 * assert callback (the editor) that aborts the whole JVM, which is how expanding Input State crashed the editor.
 */
class ComponentInspectorStackGuardTest {

    private val asserts = mutableListOf<String>()
    private var currentCase = ""

    private val strings = mockk<StringManager>(relaxed = true) {
        every { getString(any()) } answers { "!!${firstArg<String>()}!!" }
        every { getString(any(), *anyVararg()) } answers { "!!${firstArg<String>()}!!" }
    }
    private val engine = mockk<Engine>(relaxed = true) {
        every { stringManager } returns strings
        every { logger } returns LoggerService().apply { consoleSink = { _, _ -> } }
    }

    @AfterEach
    fun teardown() {
        ImGuiStyleManager.uiScale = { 1f }
        ImGuiStyleManager.appearance = { AppearanceSettings.THEME_ONLY }
        ImGuiStyleManager.library = ThemeLibrary(emptyList())
        MImGui.strings = null
    }

    /** Every component class registered for scenes (the polymorphic `Component` subclasses of the serializer). */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private fun registered(): List<KClass<out Component>> {
        val out = mutableListOf<KClass<out Component>>()
        Serializer().engineModule.dumpTo(object : SerializersModuleCollector {
            override fun <T : Any> contextual(kClass: KClass<T>, provider: (List<KSerializer<*>>) -> KSerializer<*>) = Unit
            override fun <Base : Any, Sub : Base> polymorphic(baseClass: KClass<Base>, actualClass: KClass<Sub>, actualSerializer: KSerializer<Sub>) {
                @Suppress("UNCHECKED_CAST")
                if (baseClass == Component::class) out += actualClass as KClass<out Component>
            }
            override fun <Base : Any> polymorphicDefaultSerializer(baseClass: KClass<Base>, defaultSerializerProvider: (Base) -> kotlinx.serialization.SerializationStrategy<Base>?) = Unit
            override fun <Base : Any> polymorphicDefaultDeserializer(baseClass: KClass<Base>, defaultDeserializerProvider: (String?) -> kotlinx.serialization.DeserializationStrategy<Base>?) = Unit
        })
        return out.distinct()
    }

    /** Non-default values in every simple field, so the populated branches of the inspectors draw too. */
    private fun populate(c: Component) {
        var cls: Class<*>? = c.javaClass
        while (cls != null && cls != Any::class.java) {
            for (f in cls.declaredFields) {
                if (Modifier.isStatic(f.modifiers)) continue
                runCatching {
                    f.isAccessible = true
                    when (f.type) {
                        Int::class.java -> if (!Modifier.isFinal(f.modifiers)) f.setInt(c, 3)
                        Float::class.java -> if (!Modifier.isFinal(f.modifiers)) f.setFloat(c, 2.5f)
                        Boolean::class.java -> if (!Modifier.isFinal(f.modifiers)) f.setBoolean(c, !f.getBoolean(c))
                        String::class.java -> if (!Modifier.isFinal(f.modifiers)) f.set(c, "Assets/probe_value.txt")
                        Vector2f::class.java -> (f.get(c) as? Vector2f)?.set(0.5f, -0.25f)
                        Vector3f::class.java -> (f.get(c) as? Vector3f)?.set(1f, 2f, 3f)
                        Vector4f::class.java -> (f.get(c) as? Vector4f)?.set(0.1f, 0.2f, 0.3f, 1f)
                        else -> if (f.type.isEnum && !Modifier.isFinal(f.modifiers)) f.set(c, f.type.enumConstants.last())
                    }
                }
            }
            cls = cls.superclass
        }
    }

    /** A fresh [cls]; components whose constructor needs data get a small valid one. */
    private fun build(cls: KClass<out Component>): Component = when (cls) {
        SkeletonComponent::class -> {
            val root = Bone(0, "root").apply { addChild(Bone(1, "child")) }
            SkeletonComponent(SkeletonPose(Skeleton(root, 2)))
        }
        else -> cls.createInstance()
    }

    private fun holder(c: Component): GameObject =
        @Suppress("UNCHECKED_CAST")
        (if (c is SceneComponent) Scene("probe_scene") else GameObject("probe")).addComponent(c::class as KClass<Component>, c)

    private fun newContext() {
        ImGui.createContext()
        ImGui.setAssertCallback(object : ImAssertCallback() {
            override fun imAssertCallback(assertion: String, line: Int, file: String) {
                asserts += "$assertion ($file:$line)"
                // ImGui may not survive its own assert (the editor aborts): name the case before the JVM goes.
                System.err.println("EDT-87 guard: ImGui assert in $currentCase: $assertion ($file:$line)")
            }
        })
        val io = ImGui.getIO()
        io.iniFilename = null
        io.loadFonts(javaClass.getResourceAsStream(Assets.Fonts.FONTS_FILE)!!.readAllBytes())
        io.setDisplaySize(1600f, 1000f)
        io.deltaTime = 1f / 60f
        ImGuiStyleManager.invalidate()
        ImGuiStyleManager.sync("dark")
    }

    /** Draws [c] expanded for a few frames; returns the asserts and the exception it raised, if any. */
    private fun draw(c: Component): List<String> {
        asserts.clear()
        var error: Throwable? = null
        newContext()
        try {
            repeat(3) {
                ImGui.newFrame()
                ImGui.setNextWindowPos(20f, 20f)
                ImGui.setNextWindowSize(420f, 900f)
                ImGui.begin("Properties##probe")
                ImGui.setNextItemOpen(true)
                if (ImGui.collapsingHeader("${c.name}###probe_header")) {
                    try {
                        c.imgui(engine)
                    } catch (t: Throwable) {
                        if (error == null) error = t
                    }
                }
                ImGui.end()
                ImGui.render()
                if (asserts.isNotEmpty()) return@repeat
            }
        } finally {
            ImGui.destroyContext()
        }
        return asserts.toList() + listOfNotNull(error?.let { "threw ${it::class.simpleName}: ${it.message} at ${it.stackTrace.take(4).joinToString(" < ")}" })
    }

    @Test
    fun EveryComponentInspector_DrawsWithBalancedImGuiStacks() {
        ImGuiStyleManager.library = ThemeLibrary.load(File(Assets.Themes.DIR))
        MImGui.strings = strings
        val classes = registered()
        assertTrue(classes.size >= 40, "registered components: ${classes.size}")
        val densest = AppearanceSettings(Density.CUSTOM, Spacing(Spacing.MAX, Spacing.MAX, Spacing.MAX, Spacing.MAX, Spacing.MAX, Spacing.MAX))
        val styles = listOf(AppearanceSettings.THEME_ONLY, AppearanceSettings(), AppearanceSettings(density = Density.COMPACT), densest)
        val failures = sortedSetOf<String>()
        val unbuildable = mutableListOf<String>()
        for (cls in classes) {
            for (populated in listOf(false, true)) for (style in styles) for (scale in listOf(1f, EditorSettings.MAX_UI_SCALE)) {
                val component = runCatching { build(cls) }.getOrElse { unbuildable += cls.simpleName.orEmpty(); null } ?: break
                runCatching { holder(component) }
                if (populated) populate(component)
                ImGuiStyleManager.appearance = { style }
                ImGuiStyleManager.uiScale = { scale }
                currentCase = "${cls.simpleName} ${if (populated) "populated" else "default"} ${style.density}${if (style === densest) "(max)" else ""} @${(scale * 100).toInt()}%"
                val problems = draw(component)
                problems.forEach { failures += "$currentCase: $it" }
            }
        }
        println("EDT-87 guard: ${classes.size} components, not constructible: ${unbuildable.distinct()}")
        assertTrue(unbuildable.isEmpty(), "components the guard cannot build (add them to build()): ${unbuildable.distinct()}")
        assertTrue(failures.isEmpty(), "Inspector problems (${failures.size}):\n" + failures.joinToString("\n") + "\nnot constructible: ${unbuildable.distinct()}")
    }
}
