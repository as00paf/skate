package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.renderer.DebugRenderer

fun interface Physics3DFactory {
    fun create(): IPhysics3D
}

class DefaultPhysics3DFactory : Physics3DFactory {
    override fun create(): IPhysics3D = BulletPhysics3D()
}

class BulletPhysics3DFactory(
    private val nativeLibraryLoader: NativeLibraryLoader,
    private val debugRendererProvider: () -> DebugRenderer,
) : Physics3DFactory {
    override fun create(): IPhysics3D = BulletPhysics3D(
        nativeLibraryLoader = nativeLibraryLoader,
        debugRendererProvider = debugRendererProvider
    )
}
