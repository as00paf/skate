package com.pafoid.skate.engine.utils

import com.pafoid.skate.engine.assets.pack.PackBuilder
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.fileExtension
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

class AssetsResolver(
    val serializer: Serializer,
    val logger: LoggerService
) {
    lateinit var assetsAtlas: Atlas
    lateinit var binData: ByteArray
    var headerOffset: Int = 0
    var initialized = false

    fun read(info: AssetInfo): ByteArray =
        PackBuilder.extract(binData, info.position + headerOffset, info.size, info.compressed)

    fun initialize(atlas: Atlas, binData: ByteArray, headerOffset: Int) {
        this.assetsAtlas = atlas
        this.binData = binData
        this.headerOffset = headerOffset
        initialized = true
    }

    inline fun <reified T> resolve(path: String): T? {
        if (!initialized) {
            logger.logEngine("AssetsResolver must be initialized first", LogLevel.ERROR)
            return null
        }
        val info = assetsAtlas[path.fileExtension()]?.firstOrNull {
            it.path == path
        } ?: run {
            logger.logEngine("Could not resolve ${T::class.java.simpleName} for path: $path", LogLevel.ERROR)
            return null
        }
        val data = read(info).toString(Charsets.UTF_8)
        val serializable = serializer.decode<T>(data)
        return serializable
    }

    fun resolveData(path: String): ByteBuffer? {
        if (!initialized) {
            logger.logEngine("AssetsResolver must be initialized first", LogLevel.ERROR)
            return null
        }
        val info = assetsAtlas[path.fileExtension()]?.firstOrNull {
            it.path == path
        } ?: run {
            logger.logEngine("Could not resolve data for path: $path", LogLevel.ERROR)
            return null
        }

        val bytes = read(info)
        val data = MemoryUtil.memAlloc(bytes.size)
        data.put(bytes)
        data.flip()

        return data
    }

    fun resolveModel(path: String): ByteBuffer? {
        if (!initialized) {
            logger.logEngine("AssetsResolver must be initialized first", LogLevel.ERROR)
            return null
        }
        val info = assetsAtlas[path.fileExtension()]?.firstOrNull {
            it.path == path
        } ?: run {
            logger.logEngine("Could not resolve scene for path: $path", LogLevel.ERROR)
            return null
        }

        val bytes = read(info)
        val data = ByteBuffer.allocateDirect(bytes.size)
        data.put(bytes)
        data.flip()

        return data
    }

}