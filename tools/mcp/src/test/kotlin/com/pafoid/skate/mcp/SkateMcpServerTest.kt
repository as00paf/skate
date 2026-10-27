package com.pafoid.skate.mcp

import com.pafoid.skate.cli.ShowcaseProjectBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkateMcpServerTest {
    private val server = SkateMcpServer()

    private fun rpc(line: String): JsonObject = Json.parseToJsonElement(server.handle(line)!!).jsonObject

    private fun call(id: Int, tool: String, args: String): Pair<Boolean, String> {
        val r = rpc("""{"jsonrpc":"2.0","id":$id,"method":"tools/call","params":{"name":"$tool","arguments":$args}}""")
        val res = r["result"]!!.jsonObject
        return (res["isError"]!!.jsonPrimitive.content == "true") to
            res["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
    }

    @Test
    fun Mcp_InitializeAndListTools() {
        val init = rpc("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""")
        assertEquals(SkateMcpServer.PROTOCOL_VERSION, init["result"]!!.jsonObject["protocolVersion"]!!.jsonPrimitive.content)
        val names = rpc("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")["result"]!!
            .jsonObject["tools"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertTrue(names.containsAll(listOf("list_scenes", "list_entities", "export_project", "get_logs")))
    }

    @Test
    fun Mcp_NotificationsGetNoResponseAndBadInputIsRejected() {
        assertNull(server.handle("""{"jsonrpc":"2.0","method":"notifications/initialized"}"""))
        assertEquals(-32700, rpc("not json")["error"]!!.jsonObject["code"]!!.jsonPrimitive.content.toInt())
        assertEquals(-32601, rpc("""{"jsonrpc":"2.0","id":3,"method":"nope"}""")["error"]!!.jsonObject["code"]!!.jsonPrimitive.content.toInt())
        assertTrue(call(4, "list_scenes", "{}").first)
        assertEquals(-32602, rpc("""{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"x"}}""")["error"]!!.jsonObject["code"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun Mcp_ListsScenesEntitiesAndExports() {
        val dir = Files.createTempDirectory("skate-mcp").toFile()
        try {
            val project = ShowcaseProjectBuilder().build(dir).path.replace("\\", "\\\\")
            val (e1, scenes) = call(10, "list_scenes", """{"project":"$project"}""")
            assertTrue(!e1 && scenes.contains("Physics"), scenes)

            val (e2, entities) = call(11, "list_entities", """{"project":"$project","scene":"Physics"}""")
            assertTrue(!e2 && entities.contains("Box_0") && entities.contains("RigidBody3D"), entities)

            val (e3, entity) = call(12, "get_entity", """{"project":"$project","scene":"Physics","path":"Box_0"}""")
            assertTrue(!e3 && entity.contains("Box_0"), entity)
            assertTrue(call(13, "get_entity", """{"project":"$project","scene":"Physics","path":"Nope"}""").first)

            val out = dir.resolve("out").path.replace("\\", "\\\\")
            val (e4, export) = call(14, "export_project", """{"project":"$project","out":"$out"}""")
            assertTrue(!e4, export)
            assertTrue(dir.resolve("out").resolve("game.bin").exists())
            assertNotNull(call(15, "get_logs", "{}").second)
        } finally {
            dir.deleteRecursively()
        }
    }
}
