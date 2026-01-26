package com.pafoid.skate.engine.physics3d

import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.collision.PhysicsRayTestResult
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.collision.shapes.CylinderCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.bounding.BoundingBox
import com.jme3.math.Matrix3f
import com.jme3.math.Quaternion
import com.jme3.math.Transform
import com.jme3.math.Vector3f as JmeVector3f // Alias to avoid conflict with JOML Vector3f
import com.pafoid.skate.engine.scenes.GameObject
import electrostatic4j.snaploader.LibraryInfo
import electrostatic4j.snaploader.LoadingCriterion
import electrostatic4j.snaploader.NativeBinaryLoader
import electrostatic4j.snaploader.filesystem.DirectoryPath
import electrostatic4j.snaploader.platform.NativeDynamicLibrary
import electrostatic4j.snaploader.platform.util.PlatformPredicate
import org.joml.Vector3f


class Physics3D {
    private val physicsSpace: PhysicsSpace
    var debugEnabled = false

    init {
        loadNativeLibrary()
        physicsSpace = PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT)
        physicsSpace.setGravity(JmeVector3f(0f, -9.81f, 0f))
    }

    private fun loadNativeLibrary() {
        val info = LibraryInfo(null, "bulletjme", DirectoryPath.USER_DIR)
        val loader: NativeBinaryLoader = NativeBinaryLoader(info)

        val libraries: Array<NativeDynamicLibrary?> = arrayOf<NativeDynamicLibrary?>(
            NativeDynamicLibrary("native/linux/arm64", PlatformPredicate.LINUX_ARM_64),
            NativeDynamicLibrary("native/linux/arm32", PlatformPredicate.LINUX_ARM_32),
            NativeDynamicLibrary("native/linux/x86_64", PlatformPredicate.LINUX_X86_64),
            NativeDynamicLibrary("native/osx/arm64", PlatformPredicate.MACOS_ARM_64),
            NativeDynamicLibrary("native/osx/x86_64", PlatformPredicate.MACOS_X86_64),
            NativeDynamicLibrary("native/windows/x86_64", PlatformPredicate.WIN_X86_64)
        )
        loader.registerNativeLibraries(libraries).initPlatformLibrary()
        loader.loadLibrary(LoadingCriterion.CLEAN_EXTRACTION)
    }

    fun getGravity(): Vector3f {
        val g = physicsSpace.getGravity(null)
        return Vector3f(g.x, g.y, g.z)
    }

    fun setGravity(gravity: Vector3f) {
        physicsSpace.setGravity(JmeVector3f(gravity.x, gravity.y, gravity.z))
    }

    fun rayTest(from: Vector3f, to: Vector3f): List<PhysicsRayTestResult> {
        val start = JmeVector3f(from.x, from.y, from.z)
        val end = JmeVector3f(to.x, to.y, to.z)
        return physicsSpace.rayTest(start, end)
    }

    fun add(go: GameObject) {
        val rb = go.getComponent<com.pafoid.skate.engine.physics3d.components.RigidBody3D>()
        if (rb != null) {
            val desiredMass = if (rb.bodyType == com.pafoid.skate.engine.physics3d.enums.BodyType.Static) 0f else rb.mass
            
            // Check if we need to rebuild
            if (rb.rawBody != null) {
                val currentMass = rb.rawBody!!.mass
                if (currentMass != desiredMass) {
                    physicsSpace.remove(rb.rawBody)
                    rb.rawBody = null
                }
            }

            if (rb.rawBody == null) {
                val boxColliders = go.components.filterIsInstance<com.pafoid.skate.engine.physics3d.components.BoxCollider3D>()
                val cylinderColliders = go.components.filterIsInstance<com.pafoid.skate.engine.physics3d.components.CylinderCollider3D>()
                val customColliders = go.components.filterIsInstance<com.pafoid.skate.engine.physics3d.components.CustomCollider3D>()
                
                val compound = CompoundCollisionShape()
                
                boxColliders.forEach { c ->
                    val shape = BoxCollisionShape(JmeVector3f(c.halfExtents.x, c.halfExtents.y, c.halfExtents.z))
                    shape.setMargin(c.margin)
                    compound.addChildShape(shape, JmeVector3f(c.offset.x, c.offset.y, c.offset.z))
                }
                
                cylinderColliders.forEach { c ->
                    val shape = CylinderCollisionShape(c.radius, c.height, c.axis)
                    shape.setMargin(c.margin)
                    compound.addChildShape(shape, JmeVector3f(c.offset.x, c.offset.y, c.offset.z))
                }

                customColliders.forEach { c ->
                    compound.addChildShape(c.collisionShape, JmeVector3f(0f, 0f, 0f))
                }

                // If no colliders, provide a default box
                if (boxColliders.isEmpty() && cylinderColliders.isEmpty() && customColliders.isEmpty()) {
                    val shape = BoxCollisionShape(JmeVector3f(1f, 1f, 1f))
                    shape.setMargin(0.04f)
                    compound.addChildShape(shape, JmeVector3f(0f, 0f, 0f))
                }

                val body = PhysicsRigidBody(compound, desiredMass)
                body.setFriction(rb.friction)
                
                if (rb.bodyType == com.pafoid.skate.engine.physics3d.enums.BodyType.Kinematic) {
                    body.setKinematic(true)
                }

                if (rb.useCCD) {
                    body.setCcdMotionThreshold(0.1f)
                    body.setCcdSweptSphereRadius(0.1f)
                }
                
                val trans = go.transform.translation
                val rot = go.transform.rotation
                body.setPhysicsLocation(JmeVector3f(trans.x, trans.y, trans.z))
                
                // Set rotation from euler (JOML -> JME)
                val q = org.joml.Quaternionf().rotationXYZ(
                    Math.toRadians(rot.x.toDouble()).toFloat(),
                    Math.toRadians(rot.y.toDouble()).toFloat(),
                    Math.toRadians(rot.z.toDouble()).toFloat()
                )
                body.setPhysicsRotation(Quaternion(q.x, q.y, q.z, q.w))
                
                physicsSpace.add(body)
                rb.rawBody = body
            }
        }
    }

    fun remove(go: GameObject) {
        val rb = go.getComponent<com.pafoid.skate.engine.physics3d.components.RigidBody3D>()
        rb?.rawBody?.let {
            physicsSpace.remove(it)
            rb.rawBody = null
        }
    }

    fun update(dt: Float) {
        // Bullet's update(dt, maxSteps) is more stable for variable frame rates
        physicsSpace.update(dt, 10)

        if (debugEnabled) {
            val debugColor = Vector3f(0f, 1f, 0f)
            physicsSpace.rigidBodyList.forEach { body ->
                val location = body.getPhysicsLocation(null)
                val rotation = body.getPhysicsRotation(null)
                
                val pos = Vector3f(location.x, location.y, location.z)
                val rot = org.joml.Quaternionf(rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW())

                debugDrawShape(body.collisionShape, pos, rot, debugColor)
            }
        }
    }

    private fun debugDrawShape(shape: com.jme3.bullet.collision.shapes.CollisionShape, pos: Vector3f, rot: org.joml.Quaternionf, color: Vector3f) {
        val dd = com.pafoid.skate.engine.render.DebugDraw
        
        if (shape is BoxCollisionShape) {
            val halfExtents = shape.getHalfExtents(null)
            dd.addBox3D(pos, rot, Vector3f(halfExtents.x, halfExtents.y, halfExtents.z), color)
        } else if (shape is CylinderCollisionShape) {
            val halfExtents = shape.getHalfExtents(null)
            val axis = shape.axis
            
            val radius: Float
            val height: Float
            
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
            dd.addCylinder3D(pos, rot, radius, height, axis, color)
        } else if (shape is CompoundCollisionShape) {
            shape.listChildren().forEach { child ->
                val childShape = child.shape
                val childOffset = child.copyOffset(null)
                val childRot = child.copyRotation(null)
                
                // Child's local to world: WorldPos + WorldRot * (ChildOffset + ChildRot * LocalPoint)
                // We can combine them into a single transform
                val combinedRot = org.joml.Quaternionf(rot).mul(org.joml.Quaternionf(childRot.getX(), childRot.getY(), childRot.getZ(), childRot.getW()))
                val combinedPos = Vector3f(childOffset.x, childOffset.y, childOffset.z)
                rot.transform(combinedPos)
                combinedPos.add(pos)
                
                debugDrawShape(childShape, combinedPos, combinedRot, color)
            }
        } else if (shape is com.jme3.bullet.collision.shapes.MeshCollisionShape || shape is com.jme3.bullet.collision.shapes.HullCollisionShape) {
            // Complex shapes - just draw a small cross for now to indicate position
            dd.addLine3D(Vector3f(pos).add(-0.5f, 0f, 0f), Vector3f(pos).add(0.5f, 0f, 0f), color)
            dd.addLine3D(Vector3f(pos).add(0f, -0.5f, 0f), Vector3f(pos).add(0f, 0.5f, 0f), color)
            dd.addLine3D(Vector3f(pos).add(0f, 0f, -0.5f), Vector3f(pos).add(0f, 0f, 0.5f), color)
        }
    }

    fun destroy() {
        physicsSpace.destroy()
    }
}
