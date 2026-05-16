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
        mockService = mockk<INativeLibraryLoaderService>()
        mockLoader = mockk<NativeBinaryLoader>()
        
        every { mockService.registerNativeLibraries(any(), any()) } returns mockLoader
        every { mockService.initPlatformLibrary(any()) } returns mockLoader
        every { mockService.loadLibrary(any(), any()) } returns mockLoader

        loader = NativeLibraryLoader(mockService)
    }

    @AfterEach
    fun teardown() {
        // Reset state if needed
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
}