package com.pafoid.skate.editor.help

import java.io.File
import java.net.JarURLConnection
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Where help articles live: `<root>/<locale>/<topic>.md` (EDT-68). The editor ships them in its jar under
 * `editor/help`; a dev run reads `src/main/resources/editor/help` directly so an edited article shows on the next
 * open without a rebuild.
 */
interface HelpArticleSource {
    /** Topic ids available in [locale]. */
    fun topics(locale: String): Set<String>

    /** The article's Markdown, or null when [topic] has no file in [locale] (or it is not UTF-8). */
    fun read(locale: String, topic: String): String?

    /** `src/main/resources/editor/help/...` on disk. */
    class Directory(private val root: File) : HelpArticleSource {
        override fun topics(locale: String): Set<String> =
            File(root, locale).listFiles { f -> f.isFile && f.name.endsWith(MD) }
                ?.map { it.name.removeSuffix(MD) }?.filter(::isTopicId)?.toSet().orEmpty()

        override fun read(locale: String, topic: String): String? =
            File(File(root, locale), topic + MD).takeIf { it.isFile && isTopicId(topic) }?.readBytes()?.let(::decode)
    }

    /** Resources under [root] on the classpath, from a directory (tests, IDE runs) or a jar (shipped editor). */
    class Classpath(
        private val root: String = RESOURCE_ROOT,
        private val classLoader: ClassLoader = HelpArticleSource::class.java.classLoader,
    ) : HelpArticleSource {
        override fun topics(locale: String): Set<String> {
            val dir = "$root/$locale"
            val url = classLoader.getResource(dir) ?: return emptySet()
            val names: List<String> = when (url.protocol) {
                "file" -> File(url.toURI()).list()?.toList().orEmpty()
                "jar" -> (url.openConnection() as JarURLConnection).let { conn ->
                    conn.useCaches = false
                    conn.jarFile.use { jar ->
                        jar.entries().toList().map { it.name }.filter { it.startsWith("$dir/") }
                            .map { it.removePrefix("$dir/") }.filter { '/' !in it }
                    }
                }
                else -> emptyList()
            }
            return names.filter { it.endsWith(MD) }.map { it.removeSuffix(MD) }.filter(::isTopicId).toSet()
        }

        override fun read(locale: String, topic: String): String? {
            if (!isTopicId(topic)) return null
            return classLoader.getResourceAsStream("$root/$locale/$topic$MD")?.use { it.readBytes() }?.let(::decode)
        }
    }

    companion object {
        const val RESOURCE_ROOT = "editor/help"
        const val DEV_ROOT = "src/main/resources/$RESOURCE_ROOT"
        private const val MD = ".md"
        private val TOPIC_ID = Regex("""[a-z0-9_]+""")

        /** Topic ids are the windows' help ids: lower case, digits and underscores (`asset_browser`). */
        fun isTopicId(id: String): Boolean = TOPIC_ID.matches(id)

        /** The dev folder when the editor runs from the source tree, else the classpath. */
        fun default(workingDir: File = File(".")): HelpArticleSource {
            val dev = File(workingDir, DEV_ROOT)
            return if (dev.isDirectory) Directory(dev) else Classpath()
        }

        /** Strict UTF-8; null for bytes that are not. */
        fun decode(bytes: ByteArray): String? = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }
}
