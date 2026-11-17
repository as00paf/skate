package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.engine.assets.AssetFolder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/** EDT-69: one scan serves every tab, however many tabs ask for it. */
class AssetBrowserScanTest {
    private val jobs = ArrayDeque<() -> Unit>()
    private var builds = 0
    private var published: Map<AssetFolder, List<BrowserEntry>>? = null

    private val scan = AssetBrowserScan(
        runIO = { jobs.addLast(it) },
        build = { builds++; mapOf(AssetFolder.MODELS to listOf(BrowserEntry(File("cube.obj"), AssetFolder.MODELS))) },
        publish = { published = it },
    )

    @Test
    fun ManyRequestsInAFrame_MakeOneScan() {
        repeat(11) { scan.request() }
        scan.update()
        scan.update()
        assertEquals(1, jobs.size)
        jobs.removeFirst()()
        assertEquals(1, builds)
        assertEquals(listOf("cube.obj"), published!![AssetFolder.MODELS]!!.map { it.name })
    }

    @Test
    fun NoRequest_NoScan() {
        scan.update()
        assertEquals(0, jobs.size)
    }

    @Test
    fun ARequestDuringAScan_RunsOneMoreAfterIt() {
        scan.request(); scan.update()
        scan.request(); scan.update()
        assertEquals(1, jobs.size, "waits for the running scan")
        jobs.removeFirst()()
        scan.update()
        assertEquals(1, jobs.size, "then scans again")
        jobs.removeFirst()()
        assertEquals(2, builds)
    }
}
