package com.pafoid.skate.engine.physics3d.native

import electrostatic4j.snaploader.LoadingCriterion
import electrostatic4j.snaploader.NativeBinaryLoader
import electrostatic4j.snaploader.platform.NativeDynamicLibrary

/**
 * Default implementation of INativeLibraryLoaderService that performs actual library loading
 */
class DefaultNativeLibraryLoaderService : INativeLibraryLoaderService {
    override fun registerNativeLibraries(loader: NativeBinaryLoader, libraries: Array<NativeDynamicLibrary?>): NativeBinaryLoader {
        return loader.registerNativeLibraries(libraries)
    }

    override fun initPlatformLibrary(loader: NativeBinaryLoader): NativeBinaryLoader {
        return loader.initPlatformLibrary()
    }

    override fun loadLibrary(loader: NativeBinaryLoader, criterion: LoadingCriterion): NativeBinaryLoader {
        return loader.loadLibrary(criterion)
    }
}