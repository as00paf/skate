package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.assets.data.models.animations.BoneMirrorUtil
import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test
    fun `getMirroredBoneName handles letter prefixes and suffixes but not letters inside words`() {
        assertEquals("R_Foot", BoneMirrorUtil.getMirroredBoneName("L_Foot"))
        assertEquals("rig:l.hand", BoneMirrorUtil.getMirroredBoneName("rig:r.hand"))
        assertEquals("Thigh_R", BoneMirrorUtil.getMirroredBoneName("Thigh_L"))
        assertEquals("upperarm_right", BoneMirrorUtil.getMirroredBoneName("upperarm_left"))
        assertEquals("Small_Toe", BoneMirrorUtil.getMirroredBoneName("Small_Toe"))
        assertEquals("Upper_Arm", BoneMirrorUtil.getMirroredBoneName("Upper_Arm"))
        assertEquals("Root", BoneMirrorUtil.getMirroredBoneName("Root"))
    }
}
