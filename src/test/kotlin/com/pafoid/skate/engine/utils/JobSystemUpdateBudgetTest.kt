package com.pafoid.skate.engine.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.coroutines.EmptyCoroutineContext

class JobSystemUpdateBudgetTest {

    @Test
    fun `update processes main-thread tasks using fixed per-frame budget`() {
        val jobSystem = JobSystem(maxMainThreadTasksPerUpdate = 3)
        var executedTasks = 0

        repeat(10) {
            jobSystem.mainDispatcher.dispatch(EmptyCoroutineContext, Runnable {
                executedTasks++
            })
        }

        jobSystem.update()
        assertEquals(3, executedTasks)

        jobSystem.update()
        assertEquals(6, executedTasks)

        jobSystem.update()
        assertEquals(9, executedTasks)

        jobSystem.update()
        assertEquals(10, executedTasks)

        jobSystem.destroy()
    }
}
