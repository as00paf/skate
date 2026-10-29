package com.pafoid.skate.engine.scripting.completion

import com.pafoid.skate.engine.scripting.host.KotlinScriptHost
import com.pafoid.skate.engine.scripting.host.LuaScriptHost
import com.pafoid.skate.engine.scripting.host.ScriptHostRegistry
import com.pafoid.skate.engine.scripting.host.ScriptLanguage
import com.pafoid.skate.engine.scripting.host.ScriptLoadResult
import com.pafoid.skate.engine.scripting.host.ScriptSource
import com.pafoid.skate.engine.scripting.host.TypeScriptHost
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScriptCompletionTest {
    private val kotlin = ApiCompletionProvider(LanguageProfile.KOTLIN)
    private val ts = ApiCompletionProvider(LanguageProfile.TYPESCRIPT)
    private val lua = ApiCompletionProvider(LanguageProfile.LUA)

    /** `|` marks the caret. */
    private fun at(provider: CompletionProvider, marked: String, explicit: Boolean = true): List<Completion> {
        val offset = marked.indexOf('|')
        return provider.complete(CompletionRequest("x", marked.removeRange(offset, offset + 1), offset, explicit))
    }

    private fun labels(items: List<Completion>) = items.map { it.label }

    @Test
    fun Completion_MembersOfContext() {
        val items = at(kotlin, "onUpdate { ctx, dt -> ctx.| }")

        assertTrue(labels(items).containsAll(listOf("entity", "scene", "input", "physics", "events", "time", "log", "state")))
        assertFalse("if" in labels(items))
        assertEquals("val entity: ScriptEntity", items.first { it.label == "entity" }.signature)
        assertNotNull(items.first { it.label == "entity" }.documentation)
    }

    @Test
    fun Completion_ChainedMembersAndMethodSignature() {
        val items = at(kotlin, "onUpdate { ctx, dt -> ctx.entity.transform.position.| }")
        assertEquals(listOf("x", "y", "z"), labels(items).take(3))

        val find = at(kotlin, "onCreate { ctx -> ctx.scene.| }").first { it.label == "find" }
        assertEquals("fun find(name: String): ScriptEntity?", find.signature)
        assertEquals("find()", find.insertText)
        assertEquals("find(".length, find.cursorOffset)
    }

    @Test
    fun Completion_PartialIdentifierFiltersAndReplacesPrefix() {
        val text = "onUpdate { ctx, dt -> ctx.entity.tra| }"
        val items = at(kotlin, text)

        assertEquals(listOf("transform"), labels(items))
        assertEquals(text.indexOf("tra"), items[0].replaceStart)
        assertEquals(text.indexOf('|'), items[0].replaceEnd)
    }

    @Test
    fun Completion_CamelHumpsAndAliases() {
        assertContains(labels(at(kotlin, "onCreate { ctx ->\n  val e = ctx.entity\n  e.hC| \n}")), "hasComponent")
        assertContains(labels(at(kotlin, "onUpdate { ctx, dt ->\n  val p = ctx.entity.transform.position\n  p.| }")), "normalize")
    }

    @Test
    fun Completion_TopLevelOffersContextKeywordsSnippetsAndDeclarations() {
        val items = at(kotlin, "val speed = 3\nonUpdate { ctx, dt -> | }")

        val byLabel = items.associateBy { it.label }
        assertEquals(CompletionKind.VARIABLE, byLabel.getValue("ctx").kind)
        assertEquals(CompletionKind.VARIABLE, byLabel.getValue("speed").kind)
        assertEquals(CompletionKind.KEYWORD, byLabel.getValue("while").kind)
        assertEquals(CompletionKind.SNIPPET, byLabel.getValue("onDestroy").kind)
        assertTrue(at(kotlin, "onUpdate { ctx, dt -> | }", explicit = false).isEmpty())
        assertContains(labels(at(kotlin, "onUpdate { ctx, dt -> c| }", explicit = false)), "ctx")
    }

    @Test
    fun Completion_SnippetPlacesCaret() {
        val s = at(kotlin, "onU|").first { it.kind == CompletionKind.SNIPPET }

        assertEquals("onUpdate", s.label)
        assertTrue(s.insertText.startsWith("onUpdate { ctx, dt ->"))
        assertEquals(s.insertText.indexOf("    ") + 4, s.cursorOffset)
    }

    @Test
    fun Completion_ErrorTolerantText() {
        assertContains(labels(at(kotlin, "onUpdate { ctx, dt ->\n  val = = 3 ((\n  ctx.entity.|\n")), "name")
        assertTrue(at(kotlin, "ctx.nope.|").isEmpty())
        assertTrue(at(kotlin, "onUpdate { ctx -> foo(1).|").isEmpty())
        assertTrue(at(kotlin, "onUpdate { ctx, dt -> ctx.entity.transform.position.x +| }").none { it.kind == CompletionKind.PROPERTY })
        assertTrue(kotlin.complete(CompletionRequest("x", "ct", 999)).isNotEmpty())
    }

    @Test
    fun Completion_NothingInsideStringsOrComments() {
        assertTrue(at(kotlin, "val s = \"ctx.|\"").isEmpty())
        assertTrue(at(kotlin, "// ctx.|").isEmpty())
        assertTrue(at(kotlin, "/* ctx.\n|").isEmpty())
        assertTrue(at(lua, "-- ctx.|").isEmpty())
        assertTrue(at(kotlin, "val name = |").isNotEmpty())
        assertTrue(at(kotlin, "val | = 1").isEmpty())
    }

    @Test
    fun Completion_TypeScriptAndLuaUseTheirSyntax() {
        val tsItems = at(ts, "export function onUpdate(c: any, dt: number) {\n  c.entity.|\n}")
        assertEquals("getComponent(name: string): any | null", tsItems.first { it.label == "getComponent" }.signature)
        assertFalse("component" in labels(tsItems))
        assertEquals("info(message: string): void", at(ts, "function onCreate(ctx) { ctx.log.| }").first { it.label == "info" }.signature)
        assertContains(labels(at(ts, "const t = ctx.time;\nt.|")), "deltaTime")
        assertContains(labels(at(ts, "function onCreate(ctx) { ctx.state.| }")), "set")

        val luaItems = at(lua, "function onUpdate(ctx, dt)\n  ctx.input.|\nend")
        assertEquals("action(name)", luaItems.first { it.label == "action" }.signature)
        assertContains(labels(at(lua, "local e = ctx.entity\ne.|")), "transform")
        assertContains(labels(at(lua, "fun|")), "function")
    }

    @Test
    fun Completion_HostsExposeProvidersAndAreCompletedByService() {
        val hosts = ScriptHostRegistry().apply { register(KotlinScriptHost()); register(LuaScriptHost()) }
        assertNotNull(hosts.completionProviderFor("a/b.kts"))
        assertNotNull(hosts.completionProviderFor("a.lua"))
        assertEquals(null, hosts.completionProviderFor("a.unknown"))

        CompletionService(hosts).use { service ->
            val text = "function onCreate(ctx) ctx.log. end"
            val items = service.request(CompletionRequest("a.lua", text, text.indexOf("log.") + 4)).result.get(10, TimeUnit.SECONDS)
            assertEquals(listOf("error", "info", "warn"), labels(items))
        }
    }

    @Test
    fun Completion_ServiceCancelsStaleRequestsAndCustomProvidersPlugIn() {
        val started = CountDownLatch(1)
        val slow = object : CompletionProvider {
            override fun complete(request: CompletionRequest): List<Completion> {
                started.countDown()
                while (true) { request.cancellation.throwIfCancelled(); Thread.sleep(5) }
            }
        }
        val fast = object : CompletionProvider {
            override fun complete(request: CompletionRequest) =
                listOf(Completion("custom", CompletionKind.KEYWORD, replaceStart = 0, replaceEnd = 0))
        }
        val hosts = ScriptHostRegistry().apply {
            register(customHost("slow", slow))
            register(customHost("fast", fast))
        }
        Executors.newSingleThreadExecutor().let { exec ->
            CompletionService(hosts, exec).use { service ->
                val first = service.request(CompletionRequest("a.slow", "", 0))
                assertTrue(started.await(5, TimeUnit.SECONDS))
                val second = service.request(CompletionRequest("a.fast", "", 0))

                assertEquals(listOf("custom"), labels(second.result.get(5, TimeUnit.SECONDS)))
                assertFailsWith<CancellationException> { first.result.get(5, TimeUnit.SECONDS) }
            }
        }
    }

    private fun customHost(ext: String, provider: CompletionProvider) = object : com.pafoid.skate.engine.scripting.host.ScriptHost {
        override val language = ScriptLanguage.KOTLIN
        override val fileExtensions = setOf(ext)
        override val completionProvider = provider
        override fun load(source: ScriptSource): ScriptLoadResult = ScriptLoadResult.Failure(emptyList())
    }
}
