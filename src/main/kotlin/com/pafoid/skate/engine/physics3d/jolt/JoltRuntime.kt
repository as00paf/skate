package com.pafoid.skate.engine.physics3d.jolt

import com.github.stephengold.joltjni.Jolt
import com.github.stephengold.joltjni.JoltPhysicsObject
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.physics3d.native.NativeLibrarySpec

/**
 * One-time, process-wide Jolt setup: loads the jolt-jni native, then registers Jolt's allocator, callbacks, factory and
 * types. Worlds ([JoltPhysics3D]) are created only after [ensureLoaded] returned true.
 */
internal object JoltRuntime {
    @Volatile
    private var ready = false

    /** Why the last [ensureLoaded] failed, or null. */
    @Volatile
    var failure: String? = null
        private set

    /** Loads and initialises Jolt once; returns false (see [failure]) when the native is missing or broken. */
    @Synchronized
    fun ensureLoaded(loader: NativeLibraryLoader = NativeLibraryLoader(spec = NativeLibrarySpec.JOLT)): Boolean {
        if (ready) return true
        failure = null
        try {
            if (!loader.loadNativeLibrary()) {
                failure = "jolt-jni native library could not be loaded"
                return false
            }
            Jolt.registerDefaultAllocator()
            Jolt.installDefaultAssertCallback()
            Jolt.installDefaultTraceCallback()
            if (!Jolt.newFactory()) {
                failure = "Jolt factory could not be created"
                return false
            }
            Jolt.registerTypes()
            // Safety net: frees native objects the backend forgot to close once they are garbage collected
            JoltPhysicsObject.startCleaner()
            ready = true
        } catch (e: Throwable) {
            failure = "Jolt initialisation failed: ${e.message ?: e::class.simpleName}"
        }
        return ready
    }
}
