package com.pafoid.skate.game.player

import com.pafoid.skate.game.skateboard.PreferredStance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** GPL-23 §4: the combo chain (no points): link window, manual linking, lost on a bail. Pure logic. */
class ComboTrackerTest {
    private val kickflip = ComboMove.Air(TrickType.KICKFLIP)
    private val fiftyFifty = ComboMove.Grind(GrindType.FIFTY_FIFTY)
    private val manual = ComboMove.Manual(ManualType.MANUAL)
    private val combo = ComboTracker { ComboTuning(linkWindowSec = 0.3f) }

    @Test
    fun aComboLands_WhenTheLinkWindowRunsOut() {
        combo.add(kickflip)
        combo.add(fiftyFifty)
        combo.touchedFlat()
        combo.update(0.2f)
        assertEquals(listOf(kickflip, fiftyFifty), combo.moves)
        assertNull(combo.lastResult)

        combo.update(0.2f)
        assertEquals(emptyList<ComboMove>(), combo.moves)
        assertEquals(ComboResult(listOf(kickflip, fiftyFifty), ComboOutcome.LANDED), combo.lastResult)
        assertEquals(1, combo.resultCount)
    }

    @Test
    fun aManualStartedInTheWindow_ContinuesTheCombo() {
        combo.add(kickflip)
        combo.touchedFlat()
        combo.update(0.25f)
        combo.add(manual)
        assertNull(combo.linkLeft)
        combo.update(5f) // a long manual: the window is closed while it lasts
        assertEquals(listOf(kickflip, manual), combo.moves)

        // Off the manual onto flat ground: a new window; a pop from the manual would have kept it going instead.
        combo.touchedFlat()
        combo.update(0.31f)
        assertEquals(ComboResult(listOf(kickflip, manual), ComboOutcome.LANDED), combo.lastResult)
    }

    @Test
    fun aMoveAfterTheWindow_StartsANewCombo() {
        combo.add(kickflip)
        combo.touchedFlat()
        combo.update(0.3f)
        combo.add(manual)
        assertEquals(listOf(manual), combo.moves)
        assertEquals(listOf(kickflip), combo.lastResult!!.moves)
    }

    @Test
    fun aBail_LosesTheCombo() {
        combo.add(kickflip)
        combo.add(manual)
        combo.bail()
        assertEquals(ComboResult(listOf(kickflip, manual), ComboOutcome.BAILED), combo.lastResult)
        assertEquals(emptyList<ComboMove>(), combo.moves)

        // Nothing to lose or land without a combo.
        combo.bail()
        combo.touchedFlat()
        combo.update(1f)
        assertEquals(1, combo.resultCount)
    }

    @Test
    fun reset_DropsTheComboWithoutAResult() {
        combo.add(kickflip)
        combo.touchedFlat()
        combo.reset()
        combo.update(1f)
        assertEquals(0, combo.resultCount)
        assertEquals(emptyList<ComboMove>(), combo.moves)
    }

    @Test
    fun labels_NameSpinsTricksGrindsAndManuals() {
        val string = { key: String, args: Array<out Any> ->
            when (key) {
                "trick.fs" -> "FS %d".format(*args)
                "trick.kickflip" -> "Kickflip"
                "trick.ollie" -> "Ollie"
                else -> key
            }
        }
        assertEquals("FS 180 Kickflip", ComboMove.Air(TrickType.KICKFLIP, BodySpin(SpinSide.FRONTSIDE, 180)).label(string))
        assertEquals("FS 360", ComboMove.Air(TrickType.OLLIE, BodySpin(SpinSide.FRONTSIDE, 360)).label(string))
        assertEquals("Ollie", ComboMove.Air(TrickType.OLLIE).label(string))
        assertEquals("trick.5_0", ComboMove.Grind(GrindType.FIVE_O).label(string))
        assertEquals("trick.nosemanual", ComboMove.Manual(ManualType.NOSE_MANUAL).label(string))
    }

    @Test
    fun bodySpins_RoundToTheNearest180_AndFollowTheStance() {
        val rad = { deg: Double -> Math.toRadians(deg).toFloat() }
        assertNull(BodySpin.of(rad(60.0), PreferredStance.REGULAR))
        // Left (positive yaw) is frontside for a regular rider, backside for a goofy one.
        assertEquals(BodySpin(SpinSide.FRONTSIDE, 180), BodySpin.of(rad(150.0), PreferredStance.REGULAR))
        assertEquals(BodySpin(SpinSide.BACKSIDE, 180), BodySpin.of(rad(150.0), PreferredStance.GOOFY))
        assertEquals(BodySpin(SpinSide.BACKSIDE, 360), BodySpin.of(rad(-340.0), PreferredStance.REGULAR))
    }

    @Test
    fun grinds_AreNamedByLeanOrByTheBoardAcrossTheRail() {
        val t = TrickTuning()
        assertEquals(GrindType.FIFTY_FIFTY, GrindType.of(0f, 0f, t))
        assertEquals(GrindType.FIVE_O, GrindType.of(-0.8f, 0f, t))
        assertEquals(GrindType.NOSEGRIND, GrindType.of(0.8f, 175f, t))
        assertEquals(GrindType.BOARDSLIDE, GrindType.of(0.8f, 90f, t))
    }
}
