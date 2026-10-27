package com.pafoid.skate.engine.core

import com.pafoid.skate.engine.core.PluralCategory.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PluralRulesTest {
    @Test
    fun PluralRules_EnglishAndFrenchDifferOnZero() {
        assertEquals(OTHER, PluralRules.category("en", 0))
        assertEquals(ONE, PluralRules.category("en", 1))
        assertEquals(ONE, PluralRules.category("fr", 0))
        assertEquals(OTHER, PluralRules.category("fr_CA", 2))
        assertEquals(OTHER, PluralRules.category("ja", 1))
    }

    @Test
    fun PluralRules_SlavicCategories() {
        assertEquals(ONE, PluralRules.category("ru", 21))
        assertEquals(FEW, PluralRules.category("ru", 3))
        assertEquals(MANY, PluralRules.category("ru", 11))
        assertEquals(MANY, PluralRules.category("ru", 25))
        assertEquals(ONE, PluralRules.category("pl", 1))
        assertEquals(MANY, PluralRules.category("pl", 21))
        assertEquals(FEW, PluralRules.category("pl", 22))
    }
}
