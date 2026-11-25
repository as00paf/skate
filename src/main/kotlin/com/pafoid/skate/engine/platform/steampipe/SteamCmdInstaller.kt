package com.pafoid.skate.engine.platform.steampipe

import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.ZipInputStream

/** Downloads [url] into [target]. [onProgress] gets bytes so far and the total when known. Throws on failure. */
fun interface SteamCmdDownloader {
    fun download(url: String, target: File, onProgress: (Long, Long?) -> Unit)
}

/** [HttpClient] downloader, HTTPS redirects followed. */
class JdkSteamCmdDownloader(private val timeout: Duration = Duration.ofMinutes(5)) : SteamCmdDownloader {
    override fun download(url: String, target: File, onProgress: (Long, Long?) -> Unit) {
        val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(30)).build()
        val request = HttpRequest.newBuilder(URI(url)).timeout(timeout).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) {
            response.body().close()
            throw IOException("HTTP ${response.statusCode()} for $url")
        }
        val total = response.headers().firstValueAsLong("Content-Length").let { if (it.isPresent) it.asLong else null }
        response.body().use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    done += n
                    onProgress(done, total)
                }
            }
        }
    }
}

enum class SteamCmdInstallStep { DOWNLOADING, EXTRACTING, UPDATING, DONE }

/** How [SteamCmdInstaller.install] ended. [failure] is null on success. */
data class SteamCmdInstallResult(val install: SteamCmdInstall?, val failure: SteamCmdInstallFailure? = null, val detail: String = "")

enum class SteamCmdInstallFailure { DOWNLOAD_FAILED, ARCHIVE_INVALID, EXECUTABLE_MISSING, SELF_UPDATE_FAILED, CANCELLED }

/**
 * The managed steamcmd install (docs/design/NET-07.md §4, Q2): downloads Valve's `steamcmd.zip` into [managedDir],
 * unzips it (refusing entries that escape the folder), then runs `steamcmd +quit` once so steamcmd updates itself.
 *
 * Call [install] only after the user confirmed the download from Valve in the UI (Q2). Skate never bundles
 * steamcmd (§6).
 */
class SteamCmdInstaller(
    private val managedDir: File,
    private val downloader: SteamCmdDownloader,
    private val runner: SteamCmdRunner,
    private val url: String = DOWNLOAD_URL,
) {
    fun install(
        cancel: SteamCmdCancel = SteamCmdCancel(),
        onStep: (SteamCmdInstallStep, Float) -> Unit = { _, _ -> },
        onLine: (String) -> Unit = {},
    ): SteamCmdInstallResult {
        managedDir.mkdirs()
        val zip = File(managedDir, ARCHIVE + ".part")
        try {
            onStep(SteamCmdInstallStep.DOWNLOADING, 0f)
            try {
                downloader.download(url, zip) { done, total ->
                    if (cancel.isCancelled) throw CancelledException()
                    onStep(SteamCmdInstallStep.DOWNLOADING, if (total != null && total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f)
                }
            } catch (e: CancelledException) {
                return SteamCmdInstallResult(null, SteamCmdInstallFailure.CANCELLED)
            } catch (e: Exception) {
                return SteamCmdInstallResult(null, SteamCmdInstallFailure.DOWNLOAD_FAILED, e.message.orEmpty())
            }
            if (cancel.isCancelled) return SteamCmdInstallResult(null, SteamCmdInstallFailure.CANCELLED)

            onStep(SteamCmdInstallStep.EXTRACTING, 0f)
            try {
                extract(zip, managedDir)
            } catch (e: IOException) {
                return SteamCmdInstallResult(null, SteamCmdInstallFailure.ARCHIVE_INVALID, e.message.orEmpty())
            }
            val exe = SteamCmdLocator.managedExecutable(managedDir)
            if (!exe.isFile) return SteamCmdInstallResult(null, SteamCmdInstallFailure.EXECUTABLE_MISSING)

            onStep(SteamCmdInstallStep.UPDATING, 0f)
            val run = runner.run(listOf(exe.absolutePath, "+quit"), managedDir, cancel, onLine)
            if (run.cancelled) return SteamCmdInstallResult(null, SteamCmdInstallFailure.CANCELLED)
            // steamcmd exits with 7 after a self-update on first run (⚠ UNVERIFIED, NET-07a); a rerun must give 0.
            if (run.exitCode != 0) {
                val again = runner.run(listOf(exe.absolutePath, "+quit"), managedDir, cancel, onLine)
                if (again.cancelled) return SteamCmdInstallResult(null, SteamCmdInstallFailure.CANCELLED)
                if (again.exitCode != 0) return SteamCmdInstallResult(null, SteamCmdInstallFailure.SELF_UPDATE_FAILED, "exit ${again.exitCode}")
            }
            onStep(SteamCmdInstallStep.DONE, 1f)
            return SteamCmdInstallResult(SteamCmdInstall(exe, SteamCmdSource.MANAGED))
        } finally {
            zip.delete()
        }
    }

    /** Removes the managed install, steamcmd's cached login included. */
    fun uninstall(): Boolean = !managedDir.exists() || managedDir.deleteRecursively()

    private class CancelledException : RuntimeException()

    companion object {
        /** Valve's public steamcmd download for Windows (⚠ UNVERIFIED still current, NET-07a). */
        const val DOWNLOAD_URL = "https://steamcdn-a.akamaihd.net/client/installer/steamcmd.zip"
        private const val ARCHIVE = "steamcmd.zip"

        /** Unzips [zip] into [dir]. Throws [IOException] for an unreadable archive or an entry outside [dir]. */
        fun extract(zip: File, dir: File) {
            val root = dir.canonicalFile
            var entries = 0
            ZipInputStream(zip.inputStream().buffered()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    entries++
                    val target = File(root, entry.name).canonicalFile
                    if (target != root && !target.path.startsWith(root.path + File.separator)) {
                        throw IOException("Archive entry escapes the install folder: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile.mkdirs()
                        target.outputStream().use { input.copyTo(it) }
                    }
                }
            }
            if (entries == 0) throw IOException("Empty or invalid archive")
        }
    }
}
