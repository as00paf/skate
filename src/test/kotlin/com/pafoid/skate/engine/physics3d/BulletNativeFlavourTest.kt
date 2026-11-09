package com.pafoid.skate.engine.physics3d

import com.jme3.bullet.util.NativeLibrary
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** PHY-20: the engine runs libbulletjme's single-precision release native, not the (several times slower) debug one. */
class BulletNativeFlavourTest {

    @Test
    fun BulletNative_IsTheReleaseBuild() {
        assertTrue(NativeLibraryLoader().loadNativeLibrary())
        assertFalse(NativeLibrary.isDebug(), "libbulletjme loaded a debug native")
        assertFalse(NativeLibrary.isDoublePrecision(), "the engine uses single precision")
    }

    @Test
    fun BulletNative_ClasspathCarriesOnlyTheSpReleaseClassifier() {
        val classpath = System.getProperty("java.class.path")
        assertTrue(classpath.contains("Libbulletjme-Windows64-") && classpath.contains("-SpRelease.jar"), "SpRelease missing")
        assertFalse(classpath.contains("-SpDebug.jar"), "the SpDebug native is still on the classpath")
    }
}
