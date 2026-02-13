package com.pafoid.skate.engine.physics3d.debug

import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.collision.shapes.*
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Quaternion
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.utils.JomlVector3f
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Responsible for rendering debug wireframes for physics colliders.
 * This class handles the visualization of physics objects for debugging purposes.
 */
class PhysicsDebugger(private val debugRenderer: DebugRenderer) {

    /**
     * Draws debug wireframes for all rigid bodies in the physics space.
     */
    fun drawDebugWireframes(physicsSpace: PhysicsSpace) {
        val debugColor = JomlVector3f(0f, 1f, 0f)
        physicsSpace.rigidBodyList.forEach { body ->
            val location = body.getPhysicsLocation(com.pafoid.skate.engine.utils.JmeVector3f())
            val rotation = body.getPhysicsRotation(Quaternion())

            val pos = JomlVector3f(location.x, location.y, location.z)
            val rot = Quaternionf(rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW())

            debugDrawShape(body.collisionShape, pos, rot, debugColor)
        }
    }

    /**
     * Dispatches the correct debug drawing method based on the [CollisionShape]'s type.
     */
    private fun debugDrawShape(shape: CollisionShape, pos: JomlVector3f, rot: Quaternionf, color: JomlVector3f) {
        when (shape) {
            is BoxCollisionShape -> drawBoxCollisionShape(shape, pos, rot, color)
            is CylinderCollisionShape -> drawCylinderCollisionShape(shape, pos, rot, color)
            is CompoundCollisionShape -> drawCompoundCollisionShape(shape, pos, rot, color)
            is HullCollisionShape, is MeshCollisionShape -> drawComplexShapes(shape, pos, rot, color)
        }
    }

    /**
     * Draws a wireframe representation of a [BoxCollisionShape].
     */
    fun drawBoxCollisionShape(shape: BoxCollisionShape, pos: JomlVector3f, rot: Quaternionf, color: JomlVector3f) {
        val halfExtents = shape.getHalfExtents(null)
        debugRenderer.addBox3D(pos, rot, JomlVector3f(halfExtents.x, halfExtents.y, halfExtents.z), color)
    }

    /**
     * Draws a wireframe representation of a [CylinderCollisionShape].
     */
    fun drawCylinderCollisionShape(shape: CylinderCollisionShape, pos: JomlVector3f, rot: Quaternionf, color: JomlVector3f) {
        val axis = shape.axis
        val radius: Float
        val height: Float
        val halfExtents = shape.getHalfExtents(null)

        when (axis) {
            0 -> { // X-axis is height
                radius = halfExtents.y
                height = halfExtents.x * 2f
            }
            1 -> { // Y-axis is height
                radius = halfExtents.x
                height = halfExtents.y * 2f
            }
            2 -> { // Z-axis is height
                radius = halfExtents.x
                height = halfExtents.z * 2f
            }
            else -> {
                radius = halfExtents.x
                height = halfExtents.y * 2f
            }
        }
        debugRenderer.addCylinder3D(pos, rot, radius, height, axis, color)
    }

    /**
     * Recursively draws the child shapes of a [CompoundCollisionShape].
     */
    fun drawCompoundCollisionShape(shape: CompoundCollisionShape, pos: JomlVector3f, rot: Quaternionf, color: JomlVector3f) {
        shape.listChildren().forEach { child ->
            val childShape = child.shape
            val childOffset = child.copyOffset(null)
            val childRot = child.copyRotation(null)

            // Child's local to world: WorldPos + WorldRot * (ChildOffset + ChildRot * LocalPoint)
            // We can combine them into a single transform
            val combinedRot =
                Quaternionf(rot).mul(Quaternionf(childRot.getX(), childRot.getY(), childRot.getZ(), childRot.getW()))
            val combinedPos = JomlVector3f(childOffset.x, childOffset.y, childOffset.z)
            rot.transform(combinedPos)
            combinedPos.add(pos)

            debugDrawShape(childShape, combinedPos, combinedRot, color)
        }
    }

    /**
     * Draws a placeholder for complex collision shapes (like meshes or hulls) since rendering them
     * vertex-by-vertex would be too slow for a simple debug view.
     */
    fun drawComplexShapes(
        shape: CollisionShape,
        pos: JomlVector3f,
        rot: Quaternionf,
        color: JomlVector3f
    ) {
        // Complex shapes - just draw a small cross for now to indicate position
        debugRenderer.addLine3D(JomlVector3f(pos).add(-0.5f, 0f, 0f), JomlVector3f(pos).add(0.5f, 0f, 0f), color)
        debugRenderer.addLine3D(JomlVector3f(pos).add(0f, -0.5f, 0f), JomlVector3f(pos).add(0f, 0.5f, 0f), color)
        debugRenderer.addLine3D(JomlVector3f(pos).add(0f, 0f, -0.5f), JomlVector3f(pos).add(0f, 0f, 0.5f), color)
    }
}