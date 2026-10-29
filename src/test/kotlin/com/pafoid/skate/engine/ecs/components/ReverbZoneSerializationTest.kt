package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.audio.ReverbPreset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReverbZoneSerializationTest {
    @Test
    fun ReverbZone_RoundTripsThroughPolymorphicComponentJson() {
        val serializer = Serializer()
        val zone: Component = ReverbZone(ReverbPreset.values().last(), 4f, 3, 0.5f, 0.25f)

        val decoded = serializer.decode<Component>(serializer.encode(zone))

        assertEquals(zone, decoded)
    }
}
