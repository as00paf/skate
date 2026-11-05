package com.pafoid.skate.engine.render.rhi.opengl

import com.pafoid.skate.engine.render.graph.GpuQueryApi
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL15.GL_QUERY_RESULT
import org.lwjgl.opengl.GL15.GL_QUERY_RESULT_AVAILABLE
import org.lwjgl.opengl.GL15.glBeginQuery
import org.lwjgl.opengl.GL15.glDeleteQueries
import org.lwjgl.opengl.GL15.glEndQuery
import org.lwjgl.opengl.GL15.glGenQueries
import org.lwjgl.opengl.GL15.glGetQueryObjecti
import org.lwjgl.opengl.GL33.GL_TIME_ELAPSED
import org.lwjgl.opengl.GL33.glGetQueryObjectui64

/** GL 3.3 `GL_TIME_ELAPSED` queries behind [GpuQueryApi] (REN-22); becomes the backend of `RhiTimer` at step 7. */
object GlTimerQueries {
    /** The query API, or null when there is no current context or it has no timer queries. */
    fun create(): GpuQueryApi? {
        val caps = runCatching { GL.getCapabilities() }.getOrNull() ?: return null
        if (!caps.OpenGL33 && !caps.GL_ARB_timer_query) return null
        return Api
    }

    private object Api : GpuQueryApi {
        override fun create() = glGenQueries()
        override fun begin(query: Int) = glBeginQuery(GL_TIME_ELAPSED, query)
        override fun end() = glEndQuery(GL_TIME_ELAPSED)
        override fun isAvailable(query: Int) = glGetQueryObjecti(query, GL_QUERY_RESULT_AVAILABLE) != 0
        override fun resultNs(query: Int) = glGetQueryObjectui64(query, GL_QUERY_RESULT)
        override fun delete(query: Int) = glDeleteQueries(query)
    }
}
