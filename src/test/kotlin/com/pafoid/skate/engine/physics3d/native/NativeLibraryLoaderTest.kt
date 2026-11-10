package com.pafoid.skate.engine.physics3d.native

import electrostatic4j.snaploader.NativeBinaryLoader
import io.mockk.*
import org.junit.jupiter.api.*

/**
 * Test class for NativeLibraryLoader following TDD protocol.
 * Tests the native library loading functionality that was previously in Physics3D.
 */
class NativeLibraryLoaderTest {

    private lateinit var loader: NativeLibraryLoader
    private lateinit var mockService: INativeLibraryLoaderService
    private lateinit var mockLoader: NativeBinaryLoader

    @BeforeEach
    fun setup() {
        NativeLibraryLoader.resetForTests()
        mockService = mockk<INativeLibraryLoaderService>()
        mockLoader = mockk<NativeBinaryLoader>()
        
        every { mockService.registerNativeLibraries(any(), any()) } returns mockLoader
        every { mockService.initPlatformLibrary(any()) } returns mockLoader
        every { mockService.loadLibrary(any(), any()) } returns mockLoader

        loader = NativeLibraryLoader(mockService)
    }

    @AfterEach
    fun teardown() {
        NativeLibraryLoader.resetForTests()
    }

    @Test
    fun `loadNativeLibrary_firstCall_loadsSuccessfully`() {
        // Act
        loader.loadNativeLibrary()

        // Assert
        verify(exactly = 1) { mockService.registerNativeLibraries(any(), any()) }
        verify(exactly = 1) { mockService.initPlatformLibrary(any()) }
        verify(exactly = 1) { mockService.loadLibrary(any(), any()) }
    }

    @Test
    fun `loadNativeLibrary_calledTwice_secondCallDoesNothing`() {
        // Act - Call twice
        loader.loadNativeLibrary()
        loader.loadNativeLibrary()

        // Assert - Verify only called once due to internal flag
        verify(exactly = 1) { mockService.registerNativeLibraries(any(), any()) }
        verify(exactly = 1) { mockService.initPlatformLibrary(any()) }
        verify(exactly = 1) { mockService.loadLibrary(any(), any()) }
    }

    @Test
    fun `isLibraryLoaded_initially_false_then_true_afterLoad`() {
        // This test verifies the internal state changes from false to true after loading
        // Since we can't access the private property directly, we'll test the behavior
        // by checking that the second call doesn't trigger the service methods
        
        // First call should execute the service methods
        loader.loadNativeLibrary()
        verify(exactly = 1) { mockService.registerNativeLibraries(any(), any()) }
        verify(exactly = 1) { mockService.initPlatformLibrary(any()) }
        verify(exactly = 1) { mockService.loadLibrary(any(), any()) }
        
        // Reset the verification counts
        clearMocks(mockService)
        
        // Second call should not execute the service methods, indicating the flag worked
        loader.loadNativeLibrary()
        verify(exactly = 0) { mockService.registerNativeLibraries(any(), any()) }
        verify(exactly = 0) { mockService.initPlatformLibrary(any()) }
        verify(exactly = 0) { mockService.loadLibrary(any(), any()) }
    }

    @Test
    fun NativeLoader_SpecsLoadIndependently() {
        loader.loadNativeLibrary()
        NativeLibraryLoader(mockService, NativeLibrarySpec.JOLT).loadNativeLibrary()

        // One load per library: Bullet's flag does not stop Jolt from loading
        verify(exactly = 2) { mockService.loadLibrary(any(), any()) }
    }

    @Test
    fun NativeLoader_JoltSpec_RegistersTheJoltJniJarFolder() {
        val registered = slot<Array<electrostatic4j.snaploader.platform.NativeDynamicLibrary?>>()
        every { mockService.registerNativeLibraries(any(), capture(registered)) } returns mockLoader

        NativeLibraryLoader(mockService, NativeLibrarySpec.JOLT).loadNativeLibrary()

        Assertions.assertEquals(NativeLibrarySpec.JOLT.platforms.size, registered.captured.size)
        Assertions.assertEquals("joltjni", NativeLibrarySpec.JOLT.name)
        Assertions.assertTrue(NativeLibrarySpec.JOLT.platforms.any { it.first == "windows/x86-64/com/github/stephengold" })
    }

    @Test
    fun NativeLoader_Failure_ReturnsFalseAndRetriesNextTime() {
        every { mockService.loadLibrary(any(), any()) } throws IllegalStateException("missing")

        Assertions.assertFalse(loader.loadNativeLibrary())
        Assertions.assertFalse(loader.loadNativeLibrary())
        verify(exactly = 2) { mockService.loadLibrary(any(), any()) }
    }

    @Test
    fun NativeLoader_Success_ReturnsTrue() {
        Assertions.assertTrue(loader.loadNativeLibrary())
        Assertions.assertTrue(loader.loadNativeLibrary())
    }

    @Test
    fun NativeLoader_DirIsUserHomeBasedNotCwd() {
        // PHY-18: natives must extract to a per-user data folder, not the app's
        // working directory (which can be read-only, e.g. Program Files for
        // an exported game).
        val dirPath = NativeLibraryLoader.nativesDir().path
        val home = System.getProperty("user.home").orEmpty()
        val appData = System.getenv("APPDATA")

        val os = System.getProperty("os.name").orEmpty().lowercase()
        Assertions.assertTrue(
            dirPath.startsWith(home) ||
                dirPath.startsWith(appData ?: ""),
            "nativesDir should resolve under user home or APPDATA, got: $dirPath",
        )
        Assertions.assertFalse(
            dirPath.startsWith(System.getProperty("user.dir").orEmpty()),
            "nativesDir must NOT be the working directory (read-only risk): $dirPath",
        )
    }

    @Test
    fun NativeLoader_WindowsOsName_UsesAppDataEvenWhenCapitalised() {
        val dir = NativeLibraryLoader.nativesDirFor("Windows 11", "C:/Users/u", "C:/Users/u/AppData/Roaming", null)
        Assertions.assertEquals(java.io.File("C:/Users/u/AppData/Roaming", "Skate/natives"), dir)
    }

    @Test
    fun NativeLoader_MacAndLinux_UsePerUserConfigFolders() {
        Assertions.assertEquals(
            java.io.File("/Users/u", "Library/Application Support/Skate/natives"),
            NativeLibraryLoader.nativesDirFor("Mac OS X", "/Users/u", null, null),
        )
        Assertions.assertEquals(
            java.io.File("/home/u/.config", "skate/natives"),
            NativeLibraryLoader.nativesDirFor("Linux", "/home/u", null, null),
        )
    }

    @Test
    fun NativeLoader_NativesDir_ExistsSoTheExtractorCanWriteIntoIt() {
        Assertions.assertTrue(java.io.File(NativeLibraryLoader.nativesDir().path).isDirectory)
    }
}
