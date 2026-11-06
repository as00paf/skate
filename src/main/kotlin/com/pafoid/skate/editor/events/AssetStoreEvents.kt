package com.pafoid.skate.editor.events

import com.pafoid.skate.editor.assetstore.StoreImportRequest
import com.pafoid.skate.engine.events.Event

/** Asset store (AST-16): the window publishes requests; `AssetStoreActionHandler` runs them as commands. */
sealed class AssetStoreEvent(eventName: String) : Event(eventName) {
    /** A staged download is ready to be copied into the project (main thread). */
    data class ImportRequested(val request: StoreImportRequest) : AssetStoreEvent("assetstore.import_requested")

    data class ImportFinished(val title: String, val success: Boolean, val reason: String?) : AssetStoreEvent("assetstore.import_finished")
}
