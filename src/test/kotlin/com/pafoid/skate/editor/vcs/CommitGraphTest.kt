package com.pafoid.skate.editor.vcs

import com.pafoid.skate.editor.vcs.CommitGraph.Row
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CommitGraphTest {
    private fun c(id: String, vararg parents: String, msg: String = id, author: String = "T") =
        VcsCommit(id, msg, author, 0, emptyList(), parents.toList())

    @Test
    fun Layout_ALineOfHistoryStaysInOneLane() {
        val rows = CommitGraph.layout(listOf(c("c", "b"), c("b", "a"), c("a")))
        assertEquals(listOf(Row(0, emptyList(), listOf(0), emptyList()), Row(0, listOf(0), listOf(0), emptyList()), Row(0, listOf(0), emptyList(), emptyList())), rows)
    }

    @Test
    fun Layout_ABranchSplitsAndAMergeJoins() {
        // m merges f into the main line; f and b both come from a.
        //  m        lane 0, parents b (lane 0) and f (lane 1)
        //  | f      f in lane 1, b passes in lane 0
        //  b |      b in lane 0, f's line passes in lane 1
        //  a-+      a takes lane 0; lane 1 also waited for a and ends there
        val rows = CommitGraph.layout(listOf(c("m", "b", "f"), c("f", "a"), c("b", "a"), c("a")))
        assertEquals(Row(0, emptyList(), listOf(0, 1), emptyList()), rows[0])
        assertEquals(Row(1, listOf(1), listOf(1), listOf(0)), rows[1])
        assertEquals(Row(0, listOf(0), listOf(0), listOf(1)), rows[2])
        assertEquals(Row(0, listOf(0, 1), emptyList(), emptyList()), rows[3])
        assertEquals(2, rows[0].width)
    }

    @Test
    fun Layout_TwoBranchTipsGetTwoLanesAndFreedLanesAreReused() {
        val rows = CommitGraph.layout(listOf(c("x", "a"), c("y", "a"), c("a"), c("z")))
        assertEquals(0, rows[0].lane)
        assertEquals(1, rows[1].lane)
        assertEquals(listOf(0), rows[1].through)
        // x's lane and y's lane both wait for a: they join in a's dot.
        assertEquals(Row(0, listOf(0, 1), emptyList(), emptyList()), rows[2])
        // An unrelated root after that reuses lane 0.
        assertEquals(0, rows[3].lane)
    }

    @Test
    fun Layout_IgnoresParentsOutsideTheLoadedList() {
        val rows = CommitGraph.layout(listOf(c("b", "a")))
        assertEquals(Row(0, emptyList(), emptyList(), emptyList()), rows.single())
    }

    @Test
    fun Filter_ByBranchFollowsParents() {
        val commits = listOf(c("m", "b", "f"), c("f", "a"), c("b", "a"), c("a"), c("other"))
        val branches = VcsBranches("main", listOf("main", "feature", "lone"), mapOf("main" to "m", "feature" to "f", "lone" to "other"))
        assertEquals(listOf("f", "a"), GitLogFilter.apply(commits, branches, "feature", "").map { it.id })
        assertEquals(listOf("m", "f", "b", "a"), GitLogFilter.apply(commits, branches, "main", "").map { it.id })
        assertEquals(commits, GitLogFilter.apply(commits, branches, null, ""))
        assertEquals(emptyList<VcsCommit>(), GitLogFilter.apply(commits, branches, "gone", ""))
    }

    @Test
    fun Filter_ByTextMatchesMessageAuthorOrIdStart() {
        val commits = listOf(c("abc123", msg = "Add rail texture", author = "Alex"), c("def456", msg = "Grind tweaks", author = "Sam"))
        assertEquals(listOf("abc123"), GitLogFilter.apply(commits, null, null, "RAIL").map { it.id })
        assertEquals(listOf("def456"), GitLogFilter.apply(commits, null, null, "sam").map { it.id })
        assertEquals(listOf("def456"), GitLogFilter.apply(commits, null, null, "def").map { it.id })
        assertEquals(emptyList<VcsCommit>(), GitLogFilter.apply(commits, null, null, "123"), "ids match from the start only")
    }

    @Test
    fun Labels_GroupBranchesByCommit() {
        val branches = VcsBranches("main", listOf("main", "b", "a"), mapOf("main" to "x", "b" to "x", "a" to "y"))
        assertEquals(mapOf("x" to listOf("b", "main"), "y" to listOf("a")), GitLogFilter.labels(branches))
    }
}
