package com.pafoid.skate.engine.events

sealed class AssetEvent(eventName: String, val key: String) : Event(eventName) {
    class Loaded(key: String) : AssetEvent("asset.loaded", key)
    class Failed(key: String, val reason: String) : AssetEvent("asset.failed", key)
    class Unloaded(key: String) : AssetEvent("asset.unloaded", key)
}
