package com.pafoid.skate.engine.physics3d

import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.collision.PhysicsRayTestResult
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.collision.shapes.CylinderCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
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
        physicsSpace.setGravity(com.jme3.math.Vector3f(0f, -9.81f, 0f))
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
        physicsSpace.setGravity(com.jme3.math.Vector3f(gravity.x, gravity.y, gravity.z))
    }

    fun rayTest(from: Vector3f, to: Vector3f): List<PhysicsRayTestResult> {
        val start = com.jme3.math.Vector3f(from.x, from.y, from.z)
        val end = com.jme3.math.Vector3f(to.x, to.y, to.z)
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
                
                val compound = CompoundCollisionShape()
                
                boxColliders.forEach { c ->
                    val shape = BoxCollisionShape(com.jme3.math.Vector3f(c.halfExtents.x, c.halfExtents.y, c.halfExtents.z))
                    shape.setMargin(c.margin)
                    compound.addChildShape(shape, com.jme3.math.Vector3f(c.offset.x, c.offset.y, c.offset.z))
                }
                
                cylinderColliders.forEach { c ->
                    val shape = CylinderCollisionShape(c.radius, c.height, c.axis)
                    shape.setMargin(c.margin)
                    compound.addChildShape(shape, com.jme3.math.Vector3f(c.offset.x, c.offset.y, c.offset.z))
                }

                // If no colliders, provide a default box
                if (boxColliders.isEmpty() && cylinderColliders.isEmpty()) {
                    val shape = BoxCollisionShape(com.jme3.math.Vector3f(1f, 1f, 1f))
                    shape.setMargin(0.04f)
                    compound.addChildShape(shape, com.jme3.math.Vector3f(0f, 0f, 0f))
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
                body.setPhysicsLocation(com.jme3.math.Vector3f(trans.x, trans.y, trans.z))
                
                // Set rotation from euler (JOML -> JME)
                val q = org.joml.Quaternionf().rotationXYZ(
                    Math.toRadians(rot.x.toDouble()).toFloat(),
                    Math.toRadians(rot.y.toDouble()).toFloat(),
                    Math.toRadians(rot.z.toDouble()).toFloat()
                )
                body.setPhysicsRotation(com.jme3.math.Quaternion(q.x, q.y, q.z, q.w))
                
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
                val shape = body.collisionShape
                val location = body.getPhysicsLocation(null)
                val rotation = body.getPhysicsRotation(null)
                
                // Convert JME types to JOML for DebugDraw
                val pos = Vector3f(location.x, location.y, location.z)
                val rot = org.joml.Quaternionf(rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW())

                if (shape is BoxCollisionShape) {
                    val halfExtents = shape.getHalfExtents(null)
                    val h = Vector3f(halfExtents.x, halfExtents.y, halfExtents.z)
                    drawDebugBox(pos, rot, h, debugColor)
                } else if (shape is CompoundCollisionShape) {
                    shape.listChildren().forEach { child ->
                        val childShape = child.shape
                        val worldOffset = Vector3f()
                        val jmeOffset = child.copyOffset(null)
                        worldOffset.set(jmeOffset.x, jmeOffset.y, jmeOffset.z)
                        
                        // Calculate world position of child shape
                        rot.transform(worldOffset)
                        val childPos = Vector3f(pos).add(worldOffset)
                        
                        if (childShape is BoxCollisionShape) {
                            val halfExtents = childShape.getHalfExtents(null)
                            val h = Vector3f(halfExtents.x, halfExtents.y, halfExtents.z)
                            drawDebugBox(childPos, rot, h, debugColor)
                        } else if (childShape is CylinderCollisionShape) {
                            val halfExtents = childShape.getHalfExtents(null)
                            val h = Vector3f(halfExtents.x, halfExtents.y, halfExtents.z)
                            drawDebugBox(childPos, rot, h, debugColor)
                        }
                    }
                } else {
                    // For other shapes, just draw a small cross at the origin
                    com.pafoid.skate.engine.render.DebugDraw.addLine3D(
                        Vector3f(pos).add(-0.5f, 0f, 0f),
                        Vector3f(pos).add(0.5f, 0f, 0f),
                        debugColor
                    )
                    com.pafoid.skate.engine.render.DebugDraw.addLine3D(
                        Vector3f(pos).add(0f, -0.5f, 0f),
                        Vector3f(pos).add(0f, 0.5f, 0f),
                        debugColor
                    )
                }
            }
        }
    }

    private fun drawDebugBox(center: Vector3f, rotation: org.joml.Quaternionf, halfExtents: Vector3f, color: Vector3f) {
        val h = halfExtents
        val corners = arrayOf(
            Vector3f(-h.x, -h.y, -h.z), Vector3f(h.x, -h.y, -h.z),
            Vector3f(h.x, h.y, -h.z), Vector3f(-h.x, h.y, -h.z),
            Vector3f(-h.x, -h.y, h.z), Vector3f(h.x, -h.y, h.z),
            Vector3f(h.x, h.y, h.z), Vector3f(-h.x, h.y, h.z)
        )

        // Rotate and translate corners
        corners.forEach { c ->
            rotation.transform(c)
            c.add(center)
        }

        // Draw edges
        val dd = com.pafoid.skate.engine.render.DebugDraw
        // Bottom square
        dd.addLine3D(corners[0], corners[1], color)
        dd.addLine3D(corners[1], corners[2], color)
        dd.addLine3D(corners[2], corners[3], color)
        dd.addLine3D(corners[3], corners[0], color)
        // Top square
        dd.addLine3D(corners[4], corners[5], color)
        dd.addLine3D(corners[5], corners[6], color)
        dd.addLine3D(corners[6], corners[7], color)
        dd.addLine3D(corners[7], corners[4], color)
        // Vertical lines
        dd.addLine3D(corners[0], corners[4], color)
        dd.addLine3D(corners[1], corners[5], color)
        dd.addLine3D(corners[2], corners[6], color)
        dd.addLine3D(corners[3], corners[7], color)
    }

    fun destroy() {
        physicsSpace.destroy()
    }
}
