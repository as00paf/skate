package com.pafoid.skate.engine.core

/** CLDR plural categories for the locales the engine ships; unknown languages use the English rule. */
enum class PluralCategory(val suffix: String) { ZERO("zero"), ONE("one"), TWO("two"), FEW("few"), MANY("many"), OTHER("other") }

object PluralRules {
    fun category(locale: String, n: Int): PluralCategory {
        val lang = locale.lowercase().substringBefore('_').substringBefore('-')
        val abs = kotlin.math.abs(n)
        return when (lang) {
            "ja", "zh", "ko", "vi", "th", "id" -> PluralCategory.OTHER
            "fr", "pt" -> if (abs <= 1) PluralCategory.ONE else PluralCategory.OTHER
            "ru", "uk" -> slavic(abs, manyForFractionless = true)
            "pl" -> if (abs == 1) PluralCategory.ONE else slavic(abs, manyForFractionless = true, oneRule = false)
            else -> if (abs == 1) PluralCategory.ONE else PluralCategory.OTHER
        }
    }

    private fun slavic(n: Int, manyForFractionless: Boolean, oneRule: Boolean = true): PluralCategory {
        val m10 = n % 10
        val m100 = n % 100
        return when {
            oneRule && m10 == 1 && m100 != 11 -> PluralCategory.ONE
            m10 in 2..4 && m100 !in 12..14 -> PluralCategory.FEW
            manyForFractionless -> PluralCategory.MANY
            else -> PluralCategory.OTHER
        }
    }
}
