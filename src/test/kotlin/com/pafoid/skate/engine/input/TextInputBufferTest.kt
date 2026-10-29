package com.pafoid.skate.engine.input

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TextInputBufferTest {
    @Test
    fun TextInput_CollectsTypedTextAndClearsOnConsume() {
        val b = TextInputBuffer()
        "héllo".forEach { b.appendCodePoint(it.code) }
        assertEquals("héllo", b.consumeText())
        assertEquals("", b.consumeText())
    }

    @Test
    fun TextInput_SupportsSupplementaryCodePointsAndIgnoresControlChars() {
        val b = TextInputBuffer()
        b.appendCodePoint(0x1F600)
        b.appendCodePoint('\n'.code)
        b.appendCodePoint(0x7F)
        b.appendCodePoint(-1)
        b.appendCodePoint(0x110000)
        assertEquals("\uD83D\uDE00", b.consumeText())
    }

    @Test
    fun TextInput_IsCappedAtMaxPending() {
        val b = TextInputBuffer(maxPending = 3)
        repeat(10) { b.appendCodePoint('a'.code) }
        assertEquals("aaa", b.consumeText())
    }

    @Test
    fun TextInput_RepeatLastsOneFrame() {
        val b = TextInputBuffer()
        b.keyRepeated(259)
        assertTrue(b.wasRepeated(259))
        b.endFrame()
        assertFalse(b.wasRepeated(259))
    }
}
