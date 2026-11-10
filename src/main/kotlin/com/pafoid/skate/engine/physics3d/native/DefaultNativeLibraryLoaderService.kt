package com.pafoid.skate.engine.physics3d.native

import java.io.File
import java.io.InputStream

class DefaultNativeLibraryLoaderService : INativeLibraryLoaderService {
    override fun openResource(resourcePath: String): InputStream? =
        NativeLibraryLoader::class.java.classLoader.getResourceAsStream(resourcePath)

    override fun load(file: File) {
        System.load(file.absolutePath)
    }
}
