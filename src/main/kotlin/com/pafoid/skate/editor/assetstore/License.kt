package com.pafoid.skate.editor.assetstore

/** License of a store item (AST-16 §6.1). [spdx] uses SPDX ids, or `LicenseRef-…` for anything else. */
data class LicenseInfo(
    val spdx: String,
    val name: String,
    val url: String,
    val attributionRequired: Boolean,
    val commercialUse: Boolean,
    val derivatives: Boolean,
    val shareAlike: Boolean,
) {
    val isKnown: Boolean get() = spdx != LicenseCatalog.UNKNOWN.spdx

    val badge: LicenseBadge
        get() = when {
            !isKnown || !commercialUse -> LicenseBadge.RESTRICTED
            !derivatives || shareAlike -> LicenseBadge.CONDITIONS
            attributionRequired -> LicenseBadge.ATTRIBUTION
            else -> LicenseBadge.FREE
        }
}

/** Colour class shown in the window: green, blue, amber, red. */
enum class LicenseBadge(val key: String) {
    FREE("assetstore.license.badge.free"),
    ATTRIBUTION("assetstore.license.badge.attribution"),
    CONDITIONS("assetstore.license.badge.conditions"),
    RESTRICTED("assetstore.license.badge.restricted"),
}

enum class LicenseFilter(val key: String) {
    CC0_ONLY("assetstore.filter.license.cc0"),
    COMMERCIAL_OK("assetstore.filter.license.commercial"),
    ALL("assetstore.filter.license.all");

    /** Non-commercial items stay hidden unless [allowNonCommercial] (project setting, default false) is on. */
    fun accepts(license: LicenseInfo?, allowNonCommercial: Boolean = false): Boolean {
        if (license == null) return this == ALL
        if (!license.commercialUse && !allowNonCommercial) return false
        return when (this) {
            CC0_ONLY -> license.spdx == LicenseCatalog.CC0.spdx
            COMMERCIAL_OK -> license.isKnown && license.commercialUse
            ALL -> true
        }
    }
}

/** Known licenses, looked up by SPDX id or a provider slug. Unknown slugs map to [UNKNOWN], which blocks import. */
object LicenseCatalog {
    val CC0 = LicenseInfo("CC0-1.0", "CC0 1.0 Universal", "https://creativecommons.org/publicdomain/zero/1.0/",
        attributionRequired = false, commercialUse = true, derivatives = true, shareAlike = false)
    val CC_BY_4 = cc("CC-BY-4.0", "by", nc = false, nd = false, sa = false)
    val CC_BY_SA_4 = cc("CC-BY-SA-4.0", "by-sa", nc = false, nd = false, sa = true)
    val CC_BY_ND_4 = cc("CC-BY-ND-4.0", "by-nd", nc = false, nd = true, sa = false)
    val CC_BY_NC_4 = cc("CC-BY-NC-4.0", "by-nc", nc = true, nd = false, sa = false)
    val CC_BY_NC_SA_4 = cc("CC-BY-NC-SA-4.0", "by-nc-sa", nc = true, nd = false, sa = true)
    val CC_BY_NC_ND_4 = cc("CC-BY-NC-ND-4.0", "by-nc-nd", nc = true, nd = true, sa = false)
    val APACHE_2 = LicenseInfo("Apache-2.0", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0",
        attributionRequired = true, commercialUse = true, derivatives = true, shareAlike = false)
    val UNKNOWN = LicenseInfo("LicenseRef-unknown", "Unknown license", "",
        attributionRequired = true, commercialUse = false, derivatives = false, shareAlike = false)

    private val all = listOf(CC0, CC_BY_4, CC_BY_SA_4, CC_BY_ND_4, CC_BY_NC_4, CC_BY_NC_SA_4, CC_BY_NC_ND_4, APACHE_2)

    private val aliases: Map<String, LicenseInfo> = buildMap {
        all.forEach { put(it.spdx.lowercase(), it) }
        put("cc0", CC0)
        put("cc-0", CC0)
        put("public domain", CC0)
        listOf(CC_BY_4 to "by", CC_BY_SA_4 to "by-sa", CC_BY_ND_4 to "by-nd", CC_BY_NC_4 to "by-nc",
            CC_BY_NC_SA_4 to "by-nc-sa", CC_BY_NC_ND_4 to "by-nc-nd").forEach { (license, slug) ->
            put(slug, license)
            put("cc-$slug", license)
            put("cc-$slug-4.0", license)
        }
        put("apache-2", APACHE_2)
    }

    fun lookup(slug: String?): LicenseInfo = slug?.trim()?.lowercase()?.let { aliases[it] } ?: UNKNOWN

    private fun cc(spdx: String, slug: String, nc: Boolean, nd: Boolean, sa: Boolean) = LicenseInfo(
        spdx, "Creative Commons ${slug.uppercase()} 4.0", "https://creativecommons.org/licenses/$slug/4.0/",
        attributionRequired = true, commercialUse = !nc, derivatives = !nd, shareAlike = sa,
    )
}
