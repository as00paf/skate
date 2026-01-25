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
    }

    fun destroy() {
        physicsSpace.destroy()
    }
}
