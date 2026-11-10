package com.pafoid.skate.engine.physics3d.native

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

/**
 * Extracts and loads one [spec]'s native library once per process. Defaults to Bullet. The binary is installed by
 * [NativeExtractor] into a content-hashed subfolder of [nativesDir], so concurrent processes share it safely.
 * [isCurrentPlatform] picks the [NativeLibrarySpec.platforms] row for this machine (replaced in tests).
 */
class NativeLibraryLoader(
    private val nativeLibraryLoaderService: INativeLibraryLoaderService = DefaultNativeLibraryLoaderService(),
    private val spec: NativeLibrarySpec = NativeLibrarySpec.BULLET,
    private val isCurrentPlatform: (PlatformPredicate) -> Boolean = { it.evaluatePredicate() },
    private val root: () -> File = ::nativesDir,
) {
    companion object {
        private val loaded = HashSet<String>()

        internal fun resetForTests() = synchronized(loaded) { loaded.clear() }

        /** Overrides [nativesDir] (CI, tests); the per-user folder is used when unset. */
        const val NATIVES_DIR_PROPERTY = "skate.natives.dir"

        /** Per-user data directory for physics natives (EDT-18 safety), created if missing. */
        fun nativesDir(): File {
            val dir = System.getProperty(NATIVES_DIR_PROPERTY)?.takeIf { it.isNotBlank() }?.let(::File) ?: nativesDirFor(
                System.getProperty("os.name").orEmpty(),
                System.getProperty("user.home").orEmpty(),
                System.getenv("APPDATA"),
                System.getenv("XDG_CONFIG_HOME"),
            )
            dir.mkdirs()
            return dir
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
            nativeLibraryLoaderService.load(install())
            loaded += spec.name
            true
        } catch (e: Throwable) {
            // Missing natives (unsupported platform, stripped export) must not crash the app: callers fall back
            System.err.println("Warning: Could not load native physics library ${spec.name}: ${e.message}")
            false
        }
    }

    /** Extracts this platform's bundled binary (or reuses an identical copy) and returns the file to load. */
    internal fun install(): File {
        val folder = spec.platforms.firstOrNull { isCurrentPlatform(it.second) }?.first
            ?: error("no ${spec.name} native for this platform")
        val fileName = System.mapLibraryName(spec.name)
        val bytes = nativeLibraryLoaderService.openResource("$folder/$fileName")?.use { it.readBytes() }
            ?: error("$folder/$fileName is not on the classpath")
        return NativeExtractor.install(root(), spec.name, fileName, bytes)
    }
}
