package com.pafoid.skate.editor.assetstore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class StorePathsAndLicenseTest {
    @TempDir
    lateinit var tmp: File

    @Test
    fun StorePaths_SafeRelative_RejectsZipSlipAndAbsolutePaths() {
        assertEquals("textures/a_1k.jpg", StorePaths.safeRelative("textures\\a_1k.jpg"))
        assertEquals("a.gltf", StorePaths.safeRelative("./a.gltf"))
        assertEquals("Models/GLB format/a (1).glb", StorePaths.safeRelative("Models\\GLB format\\a (1).glb"))
        listOf("../a.gltf", "textures/../../a", "/etc/passwd", "C:/x.png", "c:x.png", "a//b", "", "..", "a/b?.png", "a/b:c.png",
            "a/b|c", "a/<b>", "a/b.", "a/b ", "a/ b", "a/\u0001b").forEach { assertNull(StorePaths.safeRelative(it), it) }
        assertFalse(StorePaths.isSafeSegment("GLB format"), "our own folder names stay strict")
    }

    @Test
    fun StorePaths_ResolveInside_StaysUnderRoot() {
        assertEquals(File(tmp, "a/b.png").canonicalFile, StorePaths.resolveInside(tmp, "a/b.png"))
        assertNull(StorePaths.resolveInside(tmp, "../b.png"))
    }

    @Test
    fun StorePaths_ImportDir_IsAssetsStoreProviderSlug() {
        val dir = StorePaths.importDir(tmp, "polyhaven", StorePaths.slug("WoodenTable_01"))
        assertEquals(File(tmp, "Assets/Store/polyhaven/woodentable_01"), dir)
        assertEquals("asset", StorePaths.slug("../"))
        assertTrue(StorePaths.stagingDir(tmp, "polyhaven", "A b", "gltf-1k").invariantSeparatorsPath.endsWith(".skate/cache/store/polyhaven/a_b/gltf-1k"))
    }

    @Test
    fun StoreFormats_V1ImportsGltfObjAndTexturesOnly() {
        listOf("a.gltf", "a.glb", "a.bin", "a.obj", "a.mtl", "t.JPG", "t.png", "sky.hdr").forEach { assertTrue(StoreFormats.isImportable(it), it) }
        listOf("a.fbx", "a.blend", "a.usd", "a.usdz", "a.zip", "noext").forEach { assertFalse(StoreFormats.isImportable(it), it) }
    }

    @Test
    fun LicenseCatalog_MapsSlugsAndUnknown() {
        assertEquals(LicenseCatalog.CC0, LicenseCatalog.lookup("CC0"))
        assertEquals(LicenseCatalog.CC_BY_4, LicenseCatalog.lookup("by"))
        assertEquals(LicenseCatalog.CC_BY_NC_SA_4, LicenseCatalog.lookup("cc-by-nc-sa"))
        assertEquals(LicenseCatalog.APACHE_2, LicenseCatalog.lookup("Apache-2.0"))
        assertEquals(LicenseCatalog.UNKNOWN, LicenseCatalog.lookup("editorial"))
        assertEquals(LicenseCatalog.UNKNOWN, LicenseCatalog.lookup(null))
        assertFalse(LicenseCatalog.UNKNOWN.isKnown)
    }

    @Test
    fun License_BadgesAndFilters() {
        assertEquals(LicenseBadge.FREE, LicenseCatalog.CC0.badge)
        assertEquals(LicenseBadge.ATTRIBUTION, LicenseCatalog.CC_BY_4.badge)
        assertEquals(LicenseBadge.CONDITIONS, LicenseCatalog.CC_BY_ND_4.badge)
        assertEquals(LicenseBadge.RESTRICTED, LicenseCatalog.CC_BY_NC_4.badge)
        assertEquals(LicenseBadge.RESTRICTED, LicenseCatalog.UNKNOWN.badge)

        assertTrue(LicenseFilter.CC0_ONLY.accepts(LicenseCatalog.CC0))
        assertFalse(LicenseFilter.CC0_ONLY.accepts(LicenseCatalog.CC_BY_4))
        assertTrue(LicenseFilter.COMMERCIAL_OK.accepts(LicenseCatalog.CC_BY_SA_4))
        assertFalse(LicenseFilter.COMMERCIAL_OK.accepts(LicenseCatalog.UNKNOWN))
        assertFalse(LicenseFilter.ALL.accepts(LicenseCatalog.CC_BY_NC_4))
        assertTrue(LicenseFilter.ALL.accepts(LicenseCatalog.CC_BY_NC_4, allowNonCommercial = true))
    }

    @Test
    fun StoreHttp_RedactsCredentialsAndParsesRetryAfter() {
        val redacted = StoreHttp.redact(mapOf("Authorization" to "Bearer x", "X-Auth-Token" to "k", "Accept" to "json"))
        assertEquals(mapOf("Authorization" to "***", "X-Auth-Token" to "***", "Accept" to "json"), redacted)
        assertEquals(3L, StoreHttp.retryAfterSeconds(mapOf("Retry-After" to listOf("3"))))
        assertEquals(10L, StoreHttp.retryAfterSeconds(mapOf("retry-after" to listOf("120"))))
        assertNull(StoreHttp.retryAfterSeconds(mapOf("Retry-After" to listOf("Wed, 21 Oct 2026 07:28:00 GMT"))))
    }

    @Test
    fun AssetStoreRegistry_RejectsDuplicateOrUnsafeIds() {
        val http = FakeStoreHttp()
        val ph = com.pafoid.skate.editor.assetstore.providers.polyhaven.PolyHavenProvider(http)
        assertEquals(ph, AssetStoreRegistry(listOf(ph)).get("polyhaven"))
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> { AssetStoreRegistry(listOf(ph, ph)) }
    }
}
