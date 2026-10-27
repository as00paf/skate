package com.pafoid.skate.engine.assets

/** Outcome of a load: either the asset or why it failed. Unlike the legacy `get*`/`load*` calls it never falls back silently. */
sealed class AssetResult<out T> {
    data class Success<T>(val value: T) : AssetResult<T>()
    data class Failure(val path: String, val reason: String, val cause: Throwable? = null) : AssetResult<Nothing>()

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.value

    companion object {
        inline fun <T> attempt(path: String, block: () -> T): AssetResult<T> = try {
            Success(block())
        } catch (e: Exception) {
            Failure(path, e.message ?: e::class.java.simpleName, e)
        }
    }
}
