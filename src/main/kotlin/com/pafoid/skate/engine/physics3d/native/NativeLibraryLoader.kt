package com.pafoid.skate.engine.physics3d.native

import electrostatic4j.snaploader.LibraryInfo
import electrostatic4j.snaploader.LoadingCriterion
import electrostatic4j.snaploader.NativeBinaryLoader
import electrostatic4j.snaploader.filesystem.DirectoryPath
import electrostatic4j.snaploader.platform.NativeDynamicLibrary
import electrostatic4j.snaploader.platform.util.PlatformPredicate
import java.io.File

/**
 * A native physics library packed in a dependency jar: its base [name] (no `lib` prefix or extension) and the jar
 * folders holding the binary for each platform.
 */
class NativeLibrarySpec(val name: String, val platforms: List<Pair<String, PlatformPredicate>>) {
    companion object {
        /** libbulletjme (Bullet backend). */
        val BULLET = NativeLibrarySpec(
            "bulletjme",
            listOf(
                "native/linux/arm64" to PlatformPredicate.LINUX_ARM_64,
                "native/linux/arm32" to PlatformPredicate.LINUX_ARM_32,
                "native/linux/x86_64" to PlatformPredicate.LINUX_X86_64,
                "native/osx/arm64" to PlatformPredicate.MACOS_ARM_64,
                "native/osx/x86_64" to PlatformPredicate.MACOS_X86_64,
                "native/windows/x86_64" to PlatformPredicate.WIN_X86_64,
            ),
        )

        /** jolt-jni (Jolt backend). Only the Windows natives are on the classpath today; add a row per new OS classifier. */
        val JOLT = NativeLibrarySpec(
            "joltjni",
            listOf("windows/x86-64/com/github/stephengold" to PlatformPredicate.WIN_X86_64),
        )
    }
}

/** Extracts and loads one [spec]'s native library once per process. Defaults to Bullet. */
class NativeLibraryLoader(
    private val nativeLibraryLoaderService: INativeLibraryLoaderService = DefaultNativeLibraryLoaderService(),
    private val spec: NativeLibrarySpec = NativeLibrarySpec.BULLET,
) {
    companion object {
        private val loaded = HashSet<String>()

        internal fun resetForTests() = synchronized(loaded) { loaded.clear() }

        /** Per-user data directory for physics natives (EDT-18 safety), created if missing. */
        fun nativesDir(): DirectoryPath {
            val dir = nativesDirFor(
                System.getProperty("os.name").orEmpty(),
                System.getProperty("user.home").orEmpty(),
                System.getenv("APPDATA"),
                System.getenv("XDG_CONFIG_HOME"),
            )
            dir.mkdirs()
            return DirectoryPath(dir.path)
        }

        /** `os.name` is e.g. "Windows 11" or "Mac OS X", so it is compared case-insensitively. */
        internal fun nativesDirFor(osName: String, home: String, appData: String?, xdgConfigHome: String?): File {
            val os = osName.lowercase()
            return when {
                os.startsWith("windows") -> File(appData ?: File(home, "AppData/Roaming").path, "Skate/natives")
                os.startsWith("mac") -> File(home, "Library/Application Support/Skate/natives")
                else -> File(xdgConfigHome ?: File(home, ".config").path, "skate/natives")
            }
        }
    }

    /** Loads the library unless it already is; returns true when it is loaded. Failures are reported, not thrown. */
    fun loadNativeLibrary(): Boolean = synchronized(loaded) {
        if (spec.name in loaded) return true
        try {
            val info = LibraryInfo(null, spec.name, nativesDir())
            val loader = NativeBinaryLoader(info)
            val libraries: Array<NativeDynamicLibrary?> =
                spec.platforms.map { (path, platform) -> NativeDynamicLibrary(path, platform) }.toTypedArray()

            nativeLibraryLoaderService.registerNativeLibraries(loader, libraries)
            nativeLibraryLoaderService.initPlatformLibrary(loader)
            nativeLibraryLoaderService.loadLibrary(loader, LoadingCriterion.CLEAN_EXTRACTION)
            loaded += spec.name
            true
        } catch (e: Exception) {
            // In test environments or when native files are not available, we log the issue
            // but don't crash the application. The physics engine may not work properly
            // without the native libraries, but the application can continue running.
            System.err.println("Warning: Could not load native physics library ${spec.name}: ${e.message}")
            false
        }
    }
}
