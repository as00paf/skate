package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.TransformSystem
import com.pafoid.skate.engine.render.shadow.CascadeFit
import io.mockk.mockk
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * REN-20: child models (the skateboard's visual child under a mesh-less physics root) cast shadows, and moving only
 * the parent changes the cached shadow layer's signature (REN-22 caching) so the child's shadow follows.
 */
class ShadowCasterHierarchyTest {

    private val renderer = ShadowRenderer(mockk<Shader>(relaxed = true))
    private val transforms = TransformSystem()

    private val scene = Scene()
    private val root = GameObject("Skateboard").addComponent(Transform(Vector3f(0f, 0f, 0f)))
    private val visual = GameObject("Visual")
        .addComponent(Transform(Vector3f(0f, 0.1f, 0f)))
        .addComponent(RenderComponent(model = `3dModel`(mesh = listOf(MeshPart(vertices = floatArrayOf(-0.4f, 0f, -0.1f, 0.4f, 0.05f, 0.1f))))))

    private val fit = CascadeFit().apply {
        matrix.set(Matrix4f().ortho(-20f, 20f, -20f, 20f, -50f, 50f))
        radius = 20f
        depthRange = 100f
    }

    init {
        scene.addChild(root)
        root.addChild(visual)
        updateTransforms()
    }

    private fun updateTransforms() {
        transforms.updateTransform(root.components.filterIsInstance<Transform>().single())
        transforms.updateTransform(visual.components.filterIsInstance<Transform>().single())
    }

    @Test
    fun ShadowRenderer_CollectsChildModelsUnderAMeshlessRoot() {
        val casters = renderer.collect(scene)
        assertEquals(listOf(visual), casters.map { it.transform.gameObject })
    }

    @Test
    fun ShadowRenderer_HiddenRootRemovesItsChildrenFromShadows() {
        root.isVisible = false
        assertEquals(0, renderer.collect(scene).size)
    }

    @Test
    fun ShadowRenderer_MovingOnlyTheParentChangesTheLayerSignature() {
        renderer.collect(scene)
        val before = renderer.cascadeSignature(fit)
        assertEquals(before, renderer.also { it.collect(scene) }.cascadeSignature(fit), "unchanged scene keeps its signature")

        root.components.filterIsInstance<Transform>().single().translation.add(1f, 0f, 0f)
        updateTransforms()
        renderer.collect(scene)

        assertNotEquals(before, renderer.cascadeSignature(fit))
    }
}
