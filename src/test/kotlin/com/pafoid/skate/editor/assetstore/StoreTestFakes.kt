package com.pafoid.skate.editor.assetstore

import com.pafoid.skate.editor.assetstore.providers.polyhaven.PolyHavenProvider
import java.io.File

/** In-memory [StoreHttp]: no sockets in unit tests (AST-16 §10). */
class FakeStoreHttp(private val routes: MutableMap<String, ByteArray> = mutableMapOf()) : StoreHttp {
    val gets = mutableListOf<String>()
    val downloads = mutableListOf<String>()
    var failDownloads = false
    var contentType: String? = null

    fun route(url: String, body: String) = apply { routes[url] = body.toByteArray() }
    fun route(url: String, body: ByteArray) = apply { routes[url] = body }

    override fun get(url: String, headers: Map<String, String>): HttpResult {
        gets += url
        return routes[url]?.let { HttpResult(200, it) } ?: HttpResult(404, "not found".toByteArray())
    }

    override fun download(url: String, headers: Map<String, String>, target: File, cancel: CancelToken, progress: (Long, Long?) -> Unit): String? {
        downloads += url
        if (cancel.isCancelled) throw StoreCancelledException()
        if (failDownloads) throw StoreHttpException(500, "boom")
        val body = routes[url] ?: throw StoreHttpException(404, "not found: $url")
        target.parentFile?.mkdirs()
        target.writeBytes(body)
        progress(body.size.toLong(), body.size.toLong())
        return contentType
    }
}

/** Runs everything inline, so service state is final when the call returns. */
object InlineJobs : StoreJobs {
    override fun io(block: () -> Unit) = block()
    override fun main(block: () -> Unit) = block()
}

object PolyHavenFixtures {
    private fun text(name: String): String =
        PolyHavenFixtures::class.java.getResource("/assetstore/polyhaven/$name")!!.readText()

    /** Poly Haven API responses recorded on 2026-11-06 (trimmed catalogs). */
    fun http(): FakeStoreHttp = FakeStoreHttp()
        .route("${PolyHavenProvider.API_BASE}/assets?type=models", text("assets_models.json"))
        .route("${PolyHavenProvider.API_BASE}/assets?type=textures", text("assets_textures.json"))
        .route("${PolyHavenProvider.API_BASE}/search?q=chair&t=models&limit=200", text("search_chair_models.json"))
        .route("${PolyHavenProvider.API_BASE}/info/WoodenTable_01", text("info_WoodenTable_01.json"))
        .route("${PolyHavenProvider.API_BASE}/files/WoodenTable_01", text("files_WoodenTable_01.json"))
        .route("${PolyHavenProvider.API_BASE}/info/rock_wall_08", text("info_rock_wall_08.json"))
        .route("${PolyHavenProvider.API_BASE}/files/rock_wall_08", text("files_rock_wall_08.json"))
}

fun cc0Details(sourceId: String = "WoodenTable_01", kind: StoreAssetKind = StoreAssetKind.MODEL, license: LicenseInfo = LicenseCatalog.CC0) =
    StoreAssetDetails(
        summary = StoreAssetSummary(StoreAssetRef("polyhaven", sourceId), "Wooden Table 01", kind, null, license, listOf("Ethan Place")),
        description = null,
        sourceUrl = "https://polyhaven.com/a/$sourceId",
        authors = listOf(Author("Ethan Place")),
        license = license,
        variants = listOf(StoreVariant("gltf-1k", SourceFormat.GLTF, "1k", 10)),
    )
