package com.pafoid.skate.editor.assetimport.osm

import com.pafoid.skate.editor.assetstore.CancelToken
import com.pafoid.skate.editor.assetstore.StoreHttpException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Fetches the OSM features of a box from the Overpass API (AST-17 §4.1): one query, `out geom` so node positions come
 * inline. The raw response is the snapshot; it is never refetched to regenerate. Run it on a worker.
 */
class OverpassClient(
    private val http: OsmHttp,
    private val endpoint: String = DEFAULT_ENDPOINT,
) {
    /** The response text of [box] (clamped to [GeoBox.MAX_AREA_KM2]); throws [StoreHttpException] on HTTP errors. */
    fun fetch(box: GeoBox, cancel: CancelToken = CancelToken()): String {
        val url = "$endpoint?data=" + URLEncoder.encode(query(box.clamped()), StandardCharsets.UTF_8)
        val result = http.get(url, OsmHttp.headers("Accept" to "application/json"), cancel)
        if (!result.isSuccess) {
            val reason = when (result.status) {
                429 -> "Overpass is busy (too many requests); try again in a minute"
                504 -> "Overpass timed out; try a smaller area"
                else -> "HTTP ${result.status}"
            }
            throw StoreHttpException(result.status, "Overpass: $reason")
        }
        val text = result.text
        // Overpass reports query errors with status 200 and a "remark" instead of elements.
        if (!text.contains("\"elements\"")) throw StoreHttpException(result.status, "Overpass returned no data: ${text.take(200)}")
        return text
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://overpass-api.de/api/interpreter"

        /** The Overpass QL query for [box]: buildings, roads and paths, parks, grass, water and plazas (AST-17 §1). */
        fun query(box: GeoBox): String = """
            [out:json][timeout:90][bbox:${box.overpass()}];
            (
              way["building"];
              relation["building"]["type"="multipolygon"];
              way["building:part"];
              way["highway"];
              way["area:highway"];
              way["place"="square"];
              way["leisure"~"^(park|garden)${'$'}"];
              relation["leisure"~"^(park|garden)${'$'}"]["type"="multipolygon"];
              way["landuse"~"^(grass|recreation_ground|village_green|meadow|reservoir)${'$'}"];
              way["natural"="water"];
              relation["natural"="water"]["type"="multipolygon"];
            );
            out geom;
        """.trimIndent()
    }
}
