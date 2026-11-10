package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.editor.systems.FbxClassifier.Classification
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class FbxClassifierTest {

    private val fixturesDir = File(javaClass.classLoader.getResource("fbx")!!.file)

    @Test
    fun ModelFbx_ClassifiedAsModel() {
        val file = File(fixturesDir, "model.fbx")
        assertTrue(file.exists(), "fixture model.fbx should exist")
        assertEquals(Classification.MODEL, FbxClassifier.classifyContent(file))
        assertEquals(FileType.MODEL_3D, FileTypeResolver.resolve(file))
    }

    @Test
    fun AnimationOnlyFbx_ClassifiedAsAnimation() {
        val file = File(fixturesDir, "animation-only.fbx")
        assertTrue(file.exists(), "fixture animation-only.fbx should exist")
        assertEquals(Classification.ANIMATION, FbxClassifier.classifyContent(file))
        assertEquals(FileType.ANIMATION, FileTypeResolver.resolve(file))
    }

    @Test
    fun ModelWithClips_ClassifiedAsModel() {
        val file = File(fixturesDir, "model-with-clips.fbx")
        assertTrue(file.exists(), "fixture model-with-clips.fbx should exist")
        assertEquals(Classification.MODEL, FbxClassifier.classifyContent(file))
        assertEquals(FileType.MODEL_3D, FileTypeResolver.resolve(file))
    }

    @Test
    fun FbxResolve_DoesNotReturnUnknown() {
        // Ensure FBX files don't fall through to UNKNOWN even when Assimp fails
        val temp = File.createTempFile("test", ".FBX")
        temp.deleteOnExit()
        assertNotEquals(FileType.UNKNOWN, FileTypeResolver.resolve(temp))
    }
}
