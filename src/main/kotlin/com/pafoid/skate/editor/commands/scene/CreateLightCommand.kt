package com.pafoid.skate.editor.commands.scene

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.render.data.LightType

class CreateLightCommand(
    private val name: String,
    private val type: LightType,
    private val scene: Scene,
    private val gameObjectManager: GameObjectManager,
) : ExecuteOnlyCommand {
    override fun execute() {
        val lightObj = GameObject(name)
        val transform = Transform()
        when (type) {
            LightType.DIRECTIONAL -> {
                transform.translation.set(0f, 10f, 0f)
                transform.rotation.set(
                    Math.toRadians(-45.0).toFloat(),
                    Math.toRadians(45.0).toFloat(),
                    0f
                )
            }
            LightType.POINT -> transform.translation.set(0f, 5f, 0f)
            LightType.SPOT -> {
                transform.translation.set(0f, 5f, 0f)
                transform.rotation.set(Math.toRadians(-90.0).toFloat(), 0f, 0f)
            }
        }
        lightObj.addComponent(transform)

        gameObjectManager.addGameObject(lightObj)
        scene.selectedGameObject = lightObj
    }

    override fun undo() {
    }

    override fun getDisplayName(): String = "Create $name"
    override fun getTargetName(): String? = name
}
