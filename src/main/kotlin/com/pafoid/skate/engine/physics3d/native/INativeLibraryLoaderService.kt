package com.pafoid.skate.engine.physics3d.native

import electrostatic4j.snaploader.LoadingCriterion
import electrostatic4j.snaploader.NativeBinaryLoader
import electrostatic4j.snaploader.platform.NativeDynamicLibrary

/**
 * Interface for loading native libraries to enable mocking in tests
 */
interface INativeLibraryLoaderService {
    fun registerNativeLibraries(loader: NativeBinaryLoader, libraries: Array<NativeDynamicLibrary?>): NativeBinaryLoader
    fun initPlatformLibrary(loader: NativeBinaryLoader): NativeBinaryLoader
    fun loadLibrary(loader: NativeBinaryLoader, criterion: LoadingCriterion): NativeBinaryLoader
}