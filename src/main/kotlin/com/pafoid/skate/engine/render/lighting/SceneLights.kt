package com.pafoid.skate.engine.render.lighting

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.PointLightComponent
import com.pafoid.skate.engine.ecs.components.SpotLightComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import org.joml.Vector3f
import kotlin.math.cos

/** Converts light components to GPU-ready [LightData]. */
object SceneLights {
    fun point(go: GameObject): LightData? {
        val t = go.getComponent<Transform>() ?: return null
        val l = go.getComponent<PointLightComponent>() ?: return null
        return LightData(
            LightType.POINT, Vector3f(t.worldMatrix.getTranslation(Vector3f())), Vector3f(),
            Vector3f(l.color).mul(l.intensity), l.constant, l.linear, l.quadratic,
        )
    }

    /** Spot cone angles are stored in degrees on the component; the shader wants their cosines. */
    fun spot(go: GameObject): LightData? {
        val t = go.getComponent<Transform>() ?: return null
        val l = go.getComponent<SpotLightComponent>() ?: return null
        return LightData(
            LightType.SPOT, Vector3f(t.worldMatrix.getTranslation(Vector3f())), Vector3f(l.direction),
            Vector3f(l.color).mul(l.intensity), l.constant, l.linear, l.quadratic,
            cos(Math.toRadians(l.cutOff.toDouble())).toFloat(), cos(Math.toRadians(l.outerCutOff.toDouble())).toFloat(),
        )
    }
}
