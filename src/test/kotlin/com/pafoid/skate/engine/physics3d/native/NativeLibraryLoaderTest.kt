package com.pafoid.skate.engine.physics3d.native

import electrostatic4j.snaploader.platform.util.PlatformPredicate
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File

class NativeLibraryLoaderTest {

    @TempDir
    lateinit var root: File

    private lateinit var service: INativeLibraryLoaderService
    private val loadedFiles = mutableListOf<File>()
    private val binary = "fake native".toByteArray()

    /** Pretends this machine is Windows x64, so the spec's Windows row is used on any test host. */
    private val onWindows: (PlatformPredicate) -> Boolean = { it === PlatformPredicate.WIN_X86_64 }

    private fun loader(spec: NativeLibrarySpec = NativeLibrarySpec.BULLET) =
        NativeLibraryLoader(service, spec, onWindows) { root }

    @BeforeEach
    fun setup() {
        NativeLibraryLoader.resetForTests()
        service = mockk()
        every { service.openResource(any()) } answers { ByteArrayInputStream(binary) }
        every { service.load(capture(loadedFiles)) } just Runs
    }

    @AfterEach
    fun teardown() {
        NativeLibraryLoader.resetForTests()
    }

    @Test
    fun NativeLoader_FirstCall_ExtractsTheWindowsBinaryAndLoadsIt() {
        Assertions.assertTrue(loader().loadNativeLibrary())

        verify(exactly = 1) { service.openResource("native/windows/x86_64/${System.mapLibraryName("bulletjme")}") }
        Assertions.assertEquals(1, loadedFiles.size)
        Assertions.assertArrayEquals(binary, loadedFiles.single().readBytes())
    }

    @Test
    fun NativeLoader_CalledTwice_LoadsOnce() {
        Assertions.assertTrue(loader().loadNativeLibrary())
        Assertions.assertTrue(loader().loadNativeLibrary())

        verify(exactly = 1) { service.load(any()) }
    }

    @Test
    fun NativeLoader_SpecsLoadIndependently() {
        loader().loadNativeLibrary()
        loader(NativeLibrarySpec.JOLT).loadNativeLibrary()

        // One load per library: Bullet's flag does not stop Jolt from loading
        verify(exactly = 2) { service.load(any()) }
        verify { service.openResource("windows/x86-64/com/github/stephengold/${System.mapLibraryName("joltjni")}") }
    }

    @Test
    fun NativeLoader_InstallsIntoAVersionedSubfolderOfTheRoot() {
        loader().loadNativeLibrary()

        val file = loadedFiles.single()
        Assertions.assertEquals(NativeExtractor.versionDir(root, "bulletjme", binary), file.parentFile)
        Assertions.assertEquals(root, file.parentFile.parentFile)
        Assertions.assertTrue(file.parentFile.name.matches(Regex("bulletjme-[0-9a-f]{16}")), file.parentFile.name)
    }

    @Test
    fun NativeLoader_SecondProcess_ReusesTheInstalledFile() {
        loader().loadNativeLibrary()
        val first = loadedFiles.single()
        val stamp = first.lastModified() - 10_000
        first.setLastModified(stamp)

        // A new process: the in-memory flag is gone, the folder is not
        NativeLibraryLoader.resetForTests()
        loader().loadNativeLibrary()

        Assertions.assertEquals(first, loadedFiles.last())
        Assertions.assertEquals(stamp, first.lastModified(), "an identical installed DLL must not be rewritten")
    }

    @Test
    fun NativeLoader_NoBinaryForThisPlatform_ReturnsFalse() {
        val noPlatform = NativeLibraryLoader(service, NativeLibrarySpec.BULLET, { false }) { root }

        Assertions.assertFalse(noPlatform.loadNativeLibrary())
        verify(exactly = 0) { service.load(any()) }
    }

    @Test
    fun NativeLoader_ResourceMissing_ReturnsFalse() {
        every { service.openResource(any()) } returns null

        Assertions.assertFalse(loader().loadNativeLibrary())
        verify(exactly = 0) { service.load(any()) }
    }

    @Test
    fun NativeLoader_Failure_ReturnsFalseAndRetriesNextTime() {
        every { service.load(any()) } throws UnsatisfiedLinkError("missing")

        Assertions.assertFalse(loader().loadNativeLibrary())
        Assertions.assertFalse(loader().loadNativeLibrary())
        verify(exactly = 2) { service.load(any()) }
    }

    @Test
    fun NativeLoader_JoltSpec_UsesTheJoltJniJarFolder() {
        Assertions.assertEquals("joltjni", NativeLibrarySpec.JOLT.name)
        Assertions.assertTrue(NativeLibrarySpec.JOLT.platforms.any { it.first == "windows/x86-64/com/github/stephengold" })
    }

    @Test
    fun NativeLoader_DirIsUserHomeBasedNotCwd() {
        // PHY-18: natives must extract to a per-user data folder, not the app's working directory (which can be
        // read-only, e.g. Program Files for an exported game).
        val dir = NativeLibraryLoader.nativesDirFor(
            System.getProperty("os.name").orEmpty(),
            System.getProperty("user.home").orEmpty(),
            System.getenv("APPDATA"),
            System.getenv("XDG_CONFIG_HOME"),
        ).path
        val home = System.getProperty("user.home").orEmpty()
        val appData = System.getenv("APPDATA")
        Assertions.assertTrue(
            dir.startsWith(home) || (appData != null && dir.startsWith(appData)),
            "nativesDir should resolve under user home or APPDATA, got: $dir",
        )
        Assertions.assertFalse(
            dir.startsWith(System.getProperty("user.dir").orEmpty()),
            "nativesDir must NOT be the working directory (read-only risk): $dir",
        )
    }

    @Test
    fun NativeLoader_WindowsOsName_UsesAppDataEvenWhenCapitalised() {
        val dir = NativeLibraryLoader.nativesDirFor("Windows 11", "C:/Users/u", "C:/Users/u/AppData/Roaming", null)
        Assertions.assertEquals(File("C:/Users/u/AppData/Roaming", "Skate/natives"), dir)
    }

    @Test
    fun NativeLoader_MacAndLinux_UsePerUserConfigFolders() {
        Assertions.assertEquals(
            File("/Users/u", "Library/Application Support/Skate/natives"),
            NativeLibraryLoader.nativesDirFor("Mac OS X", "/Users/u", null, null),
        )
        Assertions.assertEquals(
            File("/home/u/.config", "skate/natives"),
            NativeLibraryLoader.nativesDirFor("Linux", "/home/u", null, null),
        )
    }

    @Test
    fun NativeLoader_NativesDir_ExistsSoTheExtractorCanWriteIntoIt() {
        Assertions.assertTrue(NativeLibraryLoader.nativesDir().isDirectory)
    }

    @Test
    fun NativeLoader_NativesDirProperty_OverridesThePerUserFolder() {
        val previous = System.getProperty(NativeLibraryLoader.NATIVES_DIR_PROPERTY)
        try {
            System.setProperty(NativeLibraryLoader.NATIVES_DIR_PROPERTY, File(root, "ci").path)
            Assertions.assertEquals(File(root, "ci"), NativeLibraryLoader.nativesDir())
            Assertions.assertTrue(File(root, "ci").isDirectory)
        } finally {
            if (previous == null) System.clearProperty(NativeLibraryLoader.NATIVES_DIR_PROPERTY)
            else System.setProperty(NativeLibraryLoader.NATIVES_DIR_PROPERTY, previous)
        }
    }
}
