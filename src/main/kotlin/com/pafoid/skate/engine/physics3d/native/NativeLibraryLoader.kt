package com.pafoid.skate.engine.physics3d.native

import electrostatic4j.snaploader.LibraryInfo
import electrostatic4j.snaploader.LoadingCriterion
import electrostatic4j.snaploader.NativeBinaryLoader
import electrostatic4j.snaploader.filesystem.DirectoryPath
import electrostatic4j.snaploader.platform.NativeDynamicLibrary
import electrostatic4j.snaploader.platform.util.PlatformPredicate

/**
 * Responsible for loading the native Bullet Physics library (`bulletjme`) using a helper library.
 * This class ensures the native binaries are available and prevents multiple loading attempts.
 * 
 * @param nativeLibraryLoaderService Service for performing the actual library loading operations
 */
class NativeLibraryLoader(
    private val nativeLibraryLoaderService: INativeLibraryLoaderService = DefaultNativeLibraryLoaderService()
) {
    companion object {
        @Volatile
        private var isNativeLibraryLoaded = false

        internal fun resetForTests() {
            isNativeLibraryLoaded = false
        }
    }

    /**
     * Loads the native Bullet Physics library (`bulletjme`) using a helper library.
     * This method is called once during initialization to ensure the native binaries are available.
     * It prevents multiple loading attempts with a static flag.
     */
    @Synchronized
    fun loadNativeLibrary() {
        if (isNativeLibraryLoaded) return
        try {
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
            
            nativeLibraryLoaderService.registerNativeLibraries(loader, libraries)
            nativeLibraryLoaderService.initPlatformLibrary(loader)
            nativeLibraryLoaderService.loadLibrary(loader, LoadingCriterion.CLEAN_EXTRACTION)
            isNativeLibraryLoaded = true
        } catch (e: Exception) {
            // In test environments or when native files are not available, we log the issue
            // but don't crash the application. The physics engine may not work properly
            // without the native libraries, but the application can continue running.
            System.err.println("Warning: Could not load native physics library: ${e.message}")
        }
    }
}
