package com.pafoid.skate.engine.audio

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StreamPolicyTest {
    private val big = StreamPolicy.AUTO_THRESHOLD_BYTES + 1

    @Test
    fun StreamPolicy_NonOgg_NeverStreams() {
        assertFalse(StreamPolicy.shouldStream("music.wav", BusId.MUSIC, StreamMode.ALWAYS, big))
    }

    @Test
    fun StreamPolicy_OggExtensionIsCaseInsensitive() {
        assertTrue(StreamPolicy.shouldStream("Music.OGG", BusId.SFX, StreamMode.ALWAYS, 10))
    }

    @Test
    fun StreamPolicy_NeverMode_DoesNotStreamLargeMusic() {
        assertFalse(StreamPolicy.shouldStream("a.ogg", BusId.MUSIC, StreamMode.NEVER, big))
    }

    @Test
    fun StreamPolicy_AutoMode_StreamsMusicBusRegardlessOfSize() {
        assertTrue(StreamPolicy.shouldStream("a.ogg", BusId.MUSIC, StreamMode.AUTO, 10))
    }

    @Test
    fun StreamPolicy_AutoMode_StreamsOnlySfxAboveThreshold() {
        assertFalse(StreamPolicy.shouldStream("a.ogg", BusId.SFX, StreamMode.AUTO, StreamPolicy.AUTO_THRESHOLD_BYTES))
        assertTrue(StreamPolicy.shouldStream("a.ogg", BusId.SFX, StreamMode.AUTO, big))
    }
}
