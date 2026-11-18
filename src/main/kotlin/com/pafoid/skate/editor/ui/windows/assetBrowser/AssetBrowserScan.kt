package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.engine.assets.AssetFolder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One scan for every Asset Browser tab (EDT-69). Tabs, Refresh and file-system events only [request] a scan; the
 * window calls [update] once per frame, which starts at most one [build] on an IO worker and hands the result to
 * [publish]. A request during a scan runs one more scan after it, so the last change is never missed.
 */
class AssetBrowserScan(
    private val runIO: (() -> Unit) -> Unit,
    private val build: () -> Map<AssetFolder, List<BrowserEntry>>,
    private val publish: (Map<AssetFolder, List<BrowserEntry>>) -> Unit,
) {
    private val pending = AtomicBoolean(false)
    private val running = AtomicBoolean(false)

    fun request() = pending.set(true)

    fun update() {
        if (!pending.get() || !running.compareAndSet(false, true)) return
        pending.set(false)
        runIO {
            try {
                publish(build())
            } finally {
                running.set(false)
            }
        }
    }
}
