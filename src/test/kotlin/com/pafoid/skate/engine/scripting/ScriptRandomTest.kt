package com.pafoid.skate.engine.scripting

import com.pafoid.skate.engine.ecs.random.RandomService
import com.pafoid.skate.engine.scripting.runtime.ScriptRandomImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ScriptRandomTest {
    @Test
    fun DefaultStream_IsNamedAfterTheFile_SoEditorAndExportMatch() {
        assertEquals("script.Enemy.kts", ScriptRandomImpl.streamName("C:\\Game\\Assets\\Scripts\\Enemy.kts"))
        assertEquals("script.Enemy.kts", ScriptRandomImpl.streamName("Assets/Scripts/Enemy.kts"))
        val editor = ScriptRandomImpl(RandomService(5), "C:\\Game\\Assets\\Scripts\\Enemy.kts")
        val export = ScriptRandomImpl(RandomService(5), "Assets/Scripts/Enemy.kts")
        assertEquals(List(10) { editor.nextInt(100) }, List(10) { export.nextInt(100) })
    }

    @Test
    fun Scripts_DrawFromTheirOwnStreams() {
        val service = RandomService(9)
        val a = ScriptRandomImpl(service, "A.kts")
        val b = ScriptRandomImpl(service, "B.kts")
        val fresh = ScriptRandomImpl(RandomService(9), "A.kts")
        val expected = List(10) { fresh.nextFloat() }
        repeat(100) { b.nextFloat() }
        assertEquals(expected, List(10) { a.nextFloat() })
    }

    @Test
    fun NamedStreams_AreSharedAndReseedRewinds() {
        val service = RandomService(3)
        val a = ScriptRandomImpl(service, "A.kts")
        val b = ScriptRandomImpl(service, "B.kts")
        val first = a.stream("dice").nextInt(1, 7)
        val second = b.stream("dice").nextInt(1, 7)
        val fresh = RandomService(3).stream("dice")
        assertEquals(listOf(fresh.nextInt(1, 7), fresh.nextInt(1, 7)), listOf(first, second))

        a.reseed(4)
        assertEquals(4L, b.seed)
        val reseeded = RandomService(4).stream("dice")
        assertEquals(List(5) { reseeded.nextInt(1000) }, List(5) { b.stream("dice").nextInt(1000) })
        assertNotEquals(List(5) { RandomService(3).stream("dice").nextInt(1000) }, List(5) { RandomService(4).stream("dice").nextInt(1000) })
    }
}
