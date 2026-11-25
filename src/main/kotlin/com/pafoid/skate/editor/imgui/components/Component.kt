package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.editor.imgui.EnumLabels
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.Component.Companion.getCachedFields
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.JointComponent
import com.pafoid.skate.engine.ecs.components.ParticleEmitter
import com.pafoid.skate.engine.ecs.components.PointLightComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.ReverbZone
import com.pafoid.skate.engine.ecs.components.ScriptComponent
import com.pafoid.skate.engine.ecs.components.SpotLightComponent
import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.WaterComponent
import imgui.ImGui
import imgui.type.ImInt
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.lang.reflect.Modifier

/**
 * The inspector of a component. Every inspector runs inside [InspectorEditTracker.track], so values it writes
 * directly still become one undoable command per gesture (EDT-10). This is the only entry point;
 * UndoCoverageGuardTest checks that.
 */
fun Component.imgui(engine: Engine) {
    InspectorEditTracker.shared.track(this) { dispatchInspector(engine) }
}

private fun Component.dispatchInspector(engine: Engine) {
    when (this) {
        is DirectionalLightComponent -> this.imgui(engine)
        is PointLightComponent -> this.imgui(engine)
        is SpotLightComponent -> this.imgui(engine)

        is Transform -> this.imgui(engine)
        is RenderComponent -> this.imgui(engine)
        is SpriteRenderer -> this.imgui(engine)
        is CameraComponent -> this.imgui(engine)
        is ScriptComponent -> this.imgui(engine)
        is JointComponent -> this.imgui(engine)
        is ReverbZone -> this.imgui(engine)
        is AudioComponent -> this.imgui(engine)
        is Animator -> this.imgui(engine)
        is ParticleEmitter -> this.imgui(engine)
        is WaterComponent -> this.imgui(engine)
        is EnvironmentComponent -> this.imgui(engine)

        else -> if (this.isUiComponent()) this.uiImgui(engine) else reflectedImgui(engine)
    }
}

/** Inspector built from the component's declared fields (numbers, booleans, strings, vectors, enums); [skip] hides fields by name. */
fun Component.reflectedImgui(engine: Engine, skip: Set<String> = emptySet()) {
    try {
        ImGui.pushID(this.javaClass.simpleName + this.name)

        val fields = getCachedFields(this.javaClass)
        fields.forEach { field ->
            val modifiers = field.modifiers
            if (Modifier.isTransient(modifiers) || Modifier.isStatic(modifiers)) return@forEach
            if (field.name in skip) return@forEach

            if (Modifier.isPrivate(modifiers)) field.isAccessible = true

            val type = field.type
            val value = field.get(this)
            val name = field.name
            val localizedName = engine.stringManager.getString("component.${this.javaClass.simpleName}.$name")
            val isFinal = Modifier.isFinal(modifiers)

            when (type) {
                Int::class.java -> {
                    if (!isFinal) {
                        val typedValue = value as Int
                        field.set(this, MImGui.dragInt(localizedName, typedValue))
                    }
                }

                Float::class.java -> {
                    if (!isFinal) {
                        val typedValue = value as Float
                        field.set(this, MImGui.dragFloat(localizedName, typedValue))
                    }
                }

                Boolean::class.java -> {
                    if (!isFinal) {
                        val typedValue = value as Boolean
                        // A checkbox carries its label on its right; it wraps nothing, so a long label is clipped
                        // in a narrow window: keep it in the shared row like every other value (EDT-100).
                        var toggled = false
                        MImGui.row(localizedName, name) { toggled = ImGui.checkbox("##value", typedValue) }
                        if (toggled) field.set(this, !typedValue)
                    }
                }

                String::class.java -> {
                    if (!isFinal && StringFields.isEditable(this.javaClass, field)) {
                        StringFields.imgui(engine, this, field, localizedName)
                    }
                }

                Vector2f::class.java -> {
                    val typedValue = value as Vector2f
                    MImGui.drawVec2Control(localizedName, typedValue)
                }

                Vector3f::class.java -> {
                    val typedValue = value as Vector3f
                    MImGui.drawVec3Control(localizedName, typedValue)
                }

                Vector4f::class.java -> {
                    val typedValue = value as Vector4f
                    val imVec = floatArrayOf(typedValue.x, typedValue.y, typedValue.z, typedValue.w)
                    var changed = false
                    MImGui.row(localizedName, name) { changed = ImGui.dragFloat4("##value", imVec) }
                    if (changed) typedValue.set(imVec[0], imVec[1], imVec[2], imVec[3])
                }

                else -> {
                    if (type.isEnum && !isFinal) {
                        // EDT-100: the shared row, and translated value names (`enum.<Enum>.<VALUE>`).
                        val labels = EnumLabels.all(engine.stringManager, type.enumConstants.map { it as Enum<*> })
                        val index = ImInt((value as Enum<*>).ordinal)
                        var changed = false
                        MImGui.row(localizedName, "component.${this.javaClass.simpleName}.$name") {
                            changed = ImGui.combo("##value", index, labels, labels.size)
                        }
                        if (changed) field.set(this, type.enumConstants[index.get()])
                    }
                }
            }
            Tooltips.showIfPresent(engine.stringManager, "component.${this.javaClass.simpleName}.$name${Tooltips.SUFFIX}")

            if (Modifier.isPrivate(modifiers)) field.isAccessible = false
        }
    } catch (e: Exception) {
        engine.logger.logEditor("Inspector failed to draw ${this.javaClass.simpleName}: ${e.message}", LogLevel.ERROR)
    } finally {
        ImGui.popID()
    }
}

fun getEnumValues(type: Class<*>): Array<String> = type.enumConstants.map { (it as Enum<*>).name }.toTypedArray()

fun indexOf(str: String, arr: Array<String>): Int {
    for (i in arr.indices) {
        if (str == arr[i]) {
            return i
        }
    }
    return -1
}

