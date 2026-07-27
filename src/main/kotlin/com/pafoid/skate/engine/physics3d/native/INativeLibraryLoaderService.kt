package com.pafoid.skate.engine.physics3d.native

import electrostatic4j.snaploader.LoadingCriterion
import electrostatic4j.snaploader.NativeBinaryLoader
import electrostatic4j.snaploader.platform.NativeDynamicLibrary

interface INativeLibraryLoaderService {
    fun registerNativeLibraries(loader: NativeBinaryLoader, libraries: Array<NativeDynamicLibrary?>): NativeBinaryLoader
    fun initPlatformLibrary(loader: NativeBinaryLoader): NativeBinaryLoader
    fun loadLibrary(loader: NativeBinaryLoader, criterion: LoadingCriterion): NativeBinaryLoader
}