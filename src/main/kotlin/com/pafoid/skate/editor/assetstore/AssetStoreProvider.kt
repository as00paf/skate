package com.pafoid.skate.editor.assetstore

import java.io.File

/**
 * One asset source (AST-16 §3). Providers are stateless apart from response caches and never touch the project: they
 * write only inside a staging folder ([postProcess]); download and import belong to [StagingDownloader] and [StoreImporter]. Methods block and run on an IO
 * worker; every network call goes through the [StoreHttp] they were built with.
 */
interface AssetStoreProvider {
    /** Stable, lowercase; used in paths (`Assets/Store/<id>/…`) and the ledger. */
    val id: String
    val displayNameKey: String
    val kinds: Set<StoreAssetKind>
    /** Provider-level credit shown in the window, as a string key (Poly Haven API condition), or null. */
    val poweredByKey: String?

    fun search(query: StoreQuery): StorePage

    fun details(ref: StoreAssetRef): StoreAssetDetails

    /** Fresh URLs for [variant]; called right before each download and never cached. */
    fun resolveDownload(details: StoreAssetDetails, variant: StoreVariant): DownloadPlan

    /**
     * Runs on the IO worker after the download, inside [stagingDir] only (unzip, texture packing). Returns the staged
     * relative paths [importPlan] works on. Default: the downloaded files as they are.
     */
    fun postProcess(details: StoreAssetDetails, variant: StoreVariant, stagingDir: File, staged: List<String>): List<String> = staged

    /** Pure: maps the staged files of [variant] to the import folder. */
    fun importPlan(details: StoreAssetDetails, variant: StoreVariant, staged: List<String>): ImportPlan
}

/** Built-in providers in display order. A `ServiceLoader` hook for third-party providers comes later (AST-16 §3). */
class AssetStoreRegistry(val providers: List<AssetStoreProvider>) {
    init {
        require(providers.map { it.id }.toSet().size == providers.size) { "Duplicate provider id" }
        providers.forEach { require(StorePaths.isSafeSegment(it.id)) { "Provider id '${it.id}' is not a safe folder name" } }
    }

    fun get(id: String): AssetStoreProvider? = providers.firstOrNull { it.id == id }
}
