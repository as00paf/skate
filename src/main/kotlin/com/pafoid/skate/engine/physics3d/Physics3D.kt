package com.pafoid.skate.engine.physics3d

import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.collision.PhysicsRayTestResult
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.collision.shapes.CylinderCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.collision.shapes.HullCollisionShape
import com.jme3.bullet.collision.shapes.MeshCollisionShape
import com.jme3.math.Quaternion
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.CustomCollider3D
import com.pafoid.skate.engine.physics3d.components.CylinderCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.render.DebugDraw
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.utils.JomlVector3f
import com.pafoid.skate.engine.utils.JmeVector3f
import electrostatic4j.snaploader.LibraryInfo
import electrostatic4j.snaploader.LoadingCriterion
import electrostatic4j.snaploader.NativeBinaryLoader
import electrostatic4j.snaploader.filesystem.DirectoryPath
import electrostatic4j.snaploader.platform.NativeDynamicLibrary
import electrostatic4j.snaploader.platform.util.PlatformPredicate
import org.joml.Quaternionf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

class Physics3D : IPhysics3D, KoinComponent {
    private val debugDraw: DebugDraw by inject()
    private val physicsSpace: PhysicsSpace
    override var debugEnabled = false

    init {
        loadNativeLibrary()
        physicsSpace = PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT)
        physicsSpace.setGravity(JmeVector3f(0f, -9.81f, 0f))
    }

    private fun loadNativeLibrary() {
        val info = LibraryInfo(null, "bulletjme", DirectoryPath.USER_DIR)
        val loader = NativeBinaryLoader(info)

        val libraries: Array<NativeDynamicLibrary?> = arrayOf(
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

    override fun getGravity(): JomlVector3f {
        val g = physicsSpace.getGravity(null)
        return JomlVector3f(g.x, g.y, g.z)
    }

    override fun setGravity(gravity: JomlVector3f) {
        physicsSpace.setGravity(JmeVector3f(gravity.x, gravity.y, gravity.z))
    }

    override fun rayTest(from: JomlVector3f, to: JomlVector3f): List<PhysicsRayTestResult> {
        val start = JmeVector3f(from.x, from.y, from.z)
        val end = JmeVector3f(to.x, to.y, to.z)
        return physicsSpace.rayTest(start, end)
    }

    override fun add(go: GameObject) {
        val rb = go.getComponent<RigidBody3D>()
        if (rb != null) {
            val desiredMass = if (rb.bodyType == BodyType.Static) 0f else rb.mass
            
            // Check if we need to rebuild
            if (rb.rawBody != null) {
                val currentMass = rb.rawBody!!.mass
                if (currentMass != desiredMass) {
                    physicsSpace.remove(rb.rawBody)
                    rb.rawBody = null
                } else {
                    // Sync transform and scale for existing body (important for Editor)
                    val trans = go.transform.translation
                    val rot = go.transform.rotation
                    val scale = go.transform.scale
                    
                    rb.rawBody!!.setPhysicsLocation(JmeVector3f(trans.x, trans.y, trans.z))
                    rb.rawBody!!.collisionShape.setScale(JmeVector3f(scale.x, scale.y, scale.z))
                    
                    val q = Quaternionf().rotationXYZ(
                        Math.toRadians(rot.x.toDouble()).toFloat(),
                        Math.toRadians(rot.y.toDouble()).toFloat(),
                        Math.toRadians(rot.z.toDouble()).toFloat()
                    )
                    rb.rawBody!!.setPhysicsRotation(Quaternion(q.x, q.y, q.z, q.w))
                }
            }

            if (rb.rawBody == null) {
                val boxColliders = go.components.filterIsInstance<BoxCollider3D>()
                val cylinderColliders = go.components.filterIsInstance<CylinderCollider3D>()
                val customColliders = go.components.filterIsInstance<CustomCollider3D>()
                
                val compound = CompoundCollisionShape()
                
                boxColliders.forEach { c ->
                    val shape = BoxCollisionShape(JmeVector3f(c.halfExtents.x, c.halfExtents.y, c.halfExtents.z))
                    shape.setMargin(c.margin)
                    compound.addChildShape(shape, JmeVector3f(c.offset.x, c.offset.y, c.offset.z))
                }
                
                cylinderColliders.forEach { c ->
                    val shape = CylinderCollisionShape(c.radius, c.height, c.axis)
                    shape.margin = c.margin
                    compound.addChildShape(shape, JmeVector3f(c.offset.x, c.offset.y, c.offset.z))
                }

                customColliders.forEach { c ->
                    compound.addChildShape(c.collisionShape, JmeVector3f(0f, 0f, 0f))
                }

                // If no colliders, provide a default box
                if (boxColliders.isEmpty() && cylinderColliders.isEmpty() && customColliders.isEmpty()) {
                    val shape = BoxCollisionShape(JmeVector3f(1f, 1f, 1f))
                    shape.margin = 0.04f
                    compound.addChildShape(shape, JmeVector3f(0f, 0f, 0f))
                }

                val body = PhysicsRigidBody(compound, desiredMass)
                body.friction = rb.friction
                
                if (rb.bodyType == BodyType.Kinematic) {
                    body.isKinematic = true
                }

                if (rb.useCCD) {
                    body.setCcdMotionThreshold(0.1f)
                    body.setCcdSweptSphereRadius(0.1f)
                }
                
                val trans = go.transform.translation
                val rot = go.transform.rotation
                val scale = go.transform.scale
                body.setPhysicsLocation(JmeVector3f(trans.x, trans.y, trans.z))
                body.collisionShape.setScale(JmeVector3f(scale.x, scale.y, scale.z))
                
                // Set rotation from euler (JOML -> JME)
                val q = Quaternionf().rotationXYZ(
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

    override fun remove(go: GameObject) {
        val rb = go.getComponent<RigidBody3D>()
        rb?.rawBody?.let {
            physicsSpace.remove(it)
            rb.rawBody = null
        }
    }

    override fun update(dt: Float) {
        // Bullet's update(dt, maxSteps) is more stable for variable frame rates
        val startTime = System.nanoTime()
        physicsSpace.update(dt, 10)
        val endTime = System.nanoTime()
        com.pafoid.skate.engine.utils.EngineStats.physicsStepTime.set(endTime - startTime)

        if (debugEnabled) {
            val debugColor = JomlVector3f(0f, 1f, 0f)
            physicsSpace.rigidBodyList.forEach { body ->
                val location = body.getPhysicsLocation(null)
                val rotation = body.getPhysicsRotation(null)
                
                val pos = JomlVector3f(location.x, location.y, location.z)
                val rot = Quaternionf(rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW())

                debugDrawShape(body.collisionShape, pos, rot, debugColor)
            }
        }
    }
    
    private fun debugDrawShape(shape: CollisionShape, pos: JomlVector3f, rot: Quaternionf, color: JomlVector3f) {
        when (shape) {
            is BoxCollisionShape -> drawBoxCollisionShape(shape, pos, rot, color)
            is CylinderCollisionShape -> drawCylinderCollisionShape(shape, pos, rot, color)
            is CompoundCollisionShape -> drawCompoundCollisionShape(shape, pos, rot, color)
            is HullCollisionShape, is MeshCollisionShape -> drawComplexShapes(shape, pos, rot, color)
        }
    }

    private fun drawComplexShapes(
        shape: CollisionShape,
        pos: JomlVector3f,
        rot: Quaternionf,
        color: JomlVector3f
    ) {
        // Complex shapes - just draw a small cross for now to indicate position
        debugDraw.addLine3D(JomlVector3f(pos).add(-0.5f, 0f, 0f), JomlVector3f(pos).add(0.5f, 0f, 0f), color)
        debugDraw.addLine3D(JomlVector3f(pos).add(0f, -0.5f, 0f), JomlVector3f(pos).add(0f, 0.5f, 0f), color)
        debugDraw.addLine3D(JomlVector3f(pos).add(0f, 0f, -0.5f), JomlVector3f(pos).add(0f, 0f, 0.5f), color)
    }

    private fun drawCompoundCollisionShape(
        shape: CompoundCollisionShape,
        pos: JomlVector3f,
        rot: Quaternionf,
        color: JomlVector3f
    ) {
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

    private fun drawCylinderCollisionShape(shape: CylinderCollisionShape, pos: JomlVector3f, rot: Quaternionf, color: JomlVector3f) {
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
        debugDraw.addCylinder3D(pos, rot, radius, height, axis, color)
    }

    private fun drawBoxCollisionShape(shape: BoxCollisionShape, pos: JomlVector3f, rot: Quaternionf, color: JomlVector3f) {
        val halfExtents = shape.getHalfExtents(null)
        debugDraw.addBox3D(pos, rot, JomlVector3f(halfExtents.x, halfExtents.y, halfExtents.z), color)
    }

    override fun destroy() {
        physicsSpace.destroy()
    }
}
