package com.pafoid.skate.engine.physics3d.native

import java.io.File
import java.io.InputStream

/** The side effects of loading a native library, replaceable in tests. */
interface INativeLibraryLoaderService {
    /** Opens the binary bundled at classpath [resourcePath], or returns null when no jar on the classpath carries it. */
    fun openResource(resourcePath: String): InputStream?

    /** Loads the native library at [file] into the process (`System.load`). */
    fun load(file: File)
}
