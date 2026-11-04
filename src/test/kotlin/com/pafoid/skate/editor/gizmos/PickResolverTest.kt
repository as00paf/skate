package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.prefab.PrefabInstance
import com.pafoid.skate.engine.utils.EntityIdEncoder
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class PickResolverTest {

    private val scene = Scene()
    private val floor = GameObject("Floor")
    private val board = GameObject("Skateboard")      // physics root
    private val boardVisual = GameObject("Visual")    // scaled model child
    private val wheel = GameObject("Wheel")

    init {
        scene.addChild(floor)
        scene.addChild(board)
        board.addChild(boardVisual)
        boardVisual.addChild(wheel)
    }

    private fun markPrefab(go: GameObject) {
        go.components.add(PrefabInstance("prefab.skateboard"))
    }

    private fun markBody(go: GameObject) {
        go.components.add(RigidBody3D())
    }

    @Test
    fun findByUid_findsNestedChildNotJustTopLevel() {
        assertSame(boardVisual, PickResolver.findByUid(scene, boardVisual.uId))
        assertSame(wheel, PickResolver.findByUid(scene, wheel.uId))
        assertSame(floor, PickResolver.findByUid(scene, floor.uId))
    }

    @Test
    fun findByUid_noEntityAndUnknownIdsResolveToNull() {
        assertNull(PickResolver.findByUid(scene, EntityIdEncoder.decode(EntityIdEncoder.NO_ENTITY)))
        assertNull(PickResolver.findByUid(scene, Int.MAX_VALUE))
    }

    @Test
    fun encodedIdRoundTripsToNestedChild() {
        val encoded = EntityIdEncoder.encode(boardVisual.uId)
        assertSame(boardVisual, PickResolver.resolve(scene, EntityIdEncoder.decode(encoded), null))
    }

    @Test
    fun selectionTarget_withoutPrefabSelectsClickedChild() {
        assertSame(boardVisual, PickResolver.selectionTarget(boardVisual, null))
    }

    @Test
    fun selectionTarget_otherSelectionDoesNotBlockNewPick() {
        assertSame(boardVisual, PickResolver.selectionTarget(boardVisual, floor))
    }

    @Test
    fun selectionTarget_prefabChildSelectsPrefabRootFirst() {
        markPrefab(board)
        assertSame(board, PickResolver.selectionTarget(wheel, null))
        assertSame(board, PickResolver.selectionTarget(wheel, floor))
    }

    @Test
    fun selectionTarget_repeatedClicksDrillDownToHit() {
        markPrefab(board)
        assertSame(boardVisual, PickResolver.selectionTarget(wheel, board))
        assertSame(wheel, PickResolver.selectionTarget(wheel, boardVisual))
        assertSame(wheel, PickResolver.selectionTarget(wheel, wheel))
    }

    @Test
    fun selectionTarget_outermostPrefabRootWinsForNestedPrefabs() {
        markPrefab(board)
        markPrefab(boardVisual)
        assertSame(board, PickResolver.selectionTarget(wheel, null))
    }

    @Test
    fun resolve_returnsNullWhenNothingHit() {
        assertNull(PickResolver.resolve(scene, -1, board))
    }

    @Test
    fun selectionTarget_visualChildOfPhysicsRootSelectsBodyFirst() {
        markBody(board)
        assertSame(board, PickResolver.selectionTarget(boardVisual, null))
        assertSame(board, PickResolver.selectionTarget(wheel, floor))
    }

    @Test
    fun selectionTarget_physicsRootDrillDownReachesChild() {
        markBody(board)
        assertSame(boardVisual, PickResolver.selectionTarget(wheel, board))
        assertSame(wheel, PickResolver.selectionTarget(wheel, boardVisual))
    }

    @Test
    fun selectionTarget_nearestPhysicsAncestorWins() {
        markBody(board)
        markBody(boardVisual)
        // boardVisual is nearest body above the wheel
        assertSame(boardVisual, PickResolver.selectionTarget(wheel, null))
    }

    @Test
    fun selectionTarget_hitWithOwnBodySelectsItself() {
        markBody(board)
        markBody(boardVisual)
        assertSame(boardVisual, PickResolver.selectionTarget(boardVisual, null))
    }

    @Test
    fun selectionTarget_prefabRootBeatsPhysicsRoot() {
        markPrefab(board)
        markBody(boardVisual)
        assertSame(board, PickResolver.selectionTarget(wheel, null))
    }
}
