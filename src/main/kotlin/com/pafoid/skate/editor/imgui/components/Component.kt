package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.AmbientLightComponent
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.Component.Companion.getCachedFields
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.PointLightComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SpotLightComponent
import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import com.pafoid.skate.engine.ecs.components.Transform
import imgui.ImGui
import imgui.type.ImInt
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.lang.reflect.Modifier

fun Component.imgui(engine: Engine) {
    when (this) {
        is AmbientLightComponent -> this.imgui(engine)
        is DirectionalLightComponent -> this.imgui(engine)
        is PointLightComponent -> this.imgui(engine)
        is SpotLightComponent -> this.imgui(engine)

        is Transform -> this.imgui(engine)
        is RenderComponent -> this.imgui(engine)
        is SpriteRenderer -> this.imgui(engine)
        is CameraComponent -> this.imgui(engine)

        else -> try {
            ImGui.pushID(this.javaClass.simpleName + this.name)

            val fields = getCachedFields(this.javaClass)
            fields.forEach { field ->
                val modifiers = field.modifiers
                if (Modifier.isTransient(modifiers) || Modifier.isStatic(modifiers)) return@forEach

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
                            if (ImGui.checkbox(localizedName, typedValue)) {
                                field.set(this, !typedValue)
                            }
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
                        if (ImGui.dragFloat4(localizedName, imVec)) {
                            typedValue.set(imVec[0], imVec[1], imVec[2], imVec[3])
                        }
                    }

                    else -> {
                        if (type.isEnum && !isFinal) {
                            val enumValues = getEnumValues(type as Class<out Enum<*>>)
                            val enumType = (value as Enum<*>).name
                            val index = ImInt(indexOf(enumType, enumValues))
                            if (ImGui.combo(localizedName, index, enumValues, enumValues.size)) {
                                field.set(this, type.enumConstants[index.get()])
                            }
                        }
                    }
                }

                if (Modifier.isPrivate(modifiers)) field.isAccessible = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            ImGui.popID()
        }
    }
}

fun <T : Enum<T>> getEnumValues(type: Class<T>): Array<String> {
    val enumValues = arrayOfNulls<String>(type.enumConstants.size)
    var i = 0
    for (enumIntegerValue in type.enumConstants) {
        enumValues[i] = enumIntegerValue.name
        i++
    }
    return enumValues.filterNotNull().toTypedArray()
}

fun indexOf(str: String, arr: Array<String>): Int {
    for (i in arr.indices) {
        if (str == arr[i]) {
            return i
        }
    }
    return -1
}

