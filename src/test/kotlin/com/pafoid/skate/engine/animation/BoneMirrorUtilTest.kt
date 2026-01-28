package com.pafoid.skate.engine.animation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BoneMirrorUtilTest {

    @Test
    fun `getMirroredBoneName should return correct mirrored names`() {
        assertEquals("mixamorig9_RightArm", BoneMirrorUtil.getMirroredBoneName("mixamorig9_LeftArm"))
        assertEquals("mixamorig9_LeftArm", BoneMirrorUtil.getMirroredBoneName("mixamorig9_RightArm"))
        assertEquals("mixamorig9_RightUpLeg", BoneMirrorUtil.getMirroredBoneName("mixamorig9_LeftUpLeg"))
        assertEquals("mixamorig9_LeftUpLeg", BoneMirrorUtil.getMirroredBoneName("mixamorig9_RightUpLeg"))
    }

    @Test
    fun `getMirroredBoneName should return original name if no mirror exists`() {
        assertEquals("mixamorig9_Spine", BoneMirrorUtil.getMirroredBoneName("mixamorig9_Spine"))
        assertEquals("mixamorig9_Head", BoneMirrorUtil.getMirroredBoneName("mixamorig9_Head"))
        assertEquals("unrelatedBone", BoneMirrorUtil.getMirroredBoneName("unrelatedBone"))
    }
}
