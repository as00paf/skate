package com.pafoid.skate.mcp

import com.pafoid.skate.cli.CliException
import com.pafoid.skate.cli.MarbleRunProjectBuilder
import com.pafoid.skate.cli.ShowcaseProjectBuilder
import com.pafoid.skate.cli.TetrisProjectBuilder
import com.pafoid.skate.cli.SkateCliService
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

private const val PROJECT = "Path to a .skateproject file or its folder"

/** Raised for invalid tool arguments; reported to the client as a tool error, not a protocol error. */
class ToolException(message: String) : Exception(message)

/**
 * Model Context Protocol server (JSON-RPC 2.0 over newline-delimited stdio messages).
 * Tools delegate to [SkateCliService] so the CLI and MCP share one implementation.
 */
class SkateMcpServer(
    private val service: SkateCliService = SkateCliService(out = {}),
    private val serializer: Serializer = Serializer(),
) {
    companion object {
        const val PROTOCOL_VERSION = "2024-11-05"
        const val SERVER_NAME = "skate-mcp"
        const val SERVER_VERSION = "0.1.0"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private class Tool(
        val name: String,
        val description: String,
        val properties: Map<String, String>,
        val required: List<String>,
        val handler: (JsonObject) -> JsonElement,
    )

    private val tools: List<Tool> = listOf(
        Tool("create_project", "Create a new Skate project", mapOf("name" to "Project name", "dir" to "Parent folder"),
            listOf("name", "dir")) { a ->
            val file = service.createProject(a.str("name"), File(a.str("dir")))
            buildJsonObject { put("project", file.path) }
        },
        Tool("create_sample", "Generate a sample project (feature-showcase, marble-run or tetris)",
            mapOf("dir" to "Parent folder", "name" to "Sample name (default feature-showcase)"), listOf("dir")) { a ->
            val dir = File(a.str("dir"))
            val project = when (val name = a.optStr("name") ?: "feature-showcase") {
                "feature-showcase" -> ShowcaseProjectBuilder(serializer).build(dir)
                "marble-run" -> MarbleRunProjectBuilder(serializer).build(dir)
                "tetris" -> TetrisProjectBuilder(serializer).build(dir)
                else -> throw ToolException("Unknown sample '$name'")
            }
            buildJsonObject { put("project", project.path) }
        },
        Tool("validate_project", "Validate a project and its scenes", mapOf("project" to PROJECT), listOf("project")) { a ->
            val r = service.validate(a.str("project"))
            buildJsonObject {
                put("valid", r.isValid)
                put("sceneCount", r.sceneCount)
                putJsonArray("errors") { r.errors.forEach { add(JsonPrimitive(it)) } }
                putJsonArray("warnings") { r.warnings.forEach { add(JsonPrimitive(it)) } }
            }
        },
        Tool("export_project", "Validate, pack and export a project's build output",
            mapOf("project" to PROJECT, "out" to "Output folder"), listOf("project", "out")) { a ->
            buildJsonObject { put("output", service.export(a.str("project"), File(a.str("out"))).path) }
        },
        Tool("run_scene", "Run a scene headless for N frames and return engine stats",
            mapOf("project" to PROJECT, "scene" to "Scene name (default scene if omitted)", "frames" to "Frames (default 60)"),
            listOf("project")) { a ->
            val s = service.run(a.str("project"), a.optStr("scene"), a.optStr("frames")?.toIntOrNull() ?: 60, 1f / 60f)
            buildJsonObject {
                put("scene", s.sceneName)
                put("frames", s.frames)
                put("simulatedSeconds", s.simulatedSeconds)
                put("wallMillis", s.wallMillis)
                put("gameObjects", s.gameObjects)
                put("components", s.components)
            }
        },
        Tool("list_scenes", "List the scenes of a project", mapOf("project" to PROJECT), listOf("project")) { a ->
            val project = service.loadProject(a.str("project"))
            buildJsonObject {
                put("default", File(project.defaultScene.replace('\\', '/')).nameWithoutExtension)
                putJsonArray("scenes") {
                    service.loadScenes(project).forEach { s ->
                        add(buildJsonObject { put("name", s.name); put("rootEntities", s.children.size) })
                    }
                }
            }
        },
        Tool("list_entities", "Scene tree: entities with their component types",
            mapOf("project" to PROJECT, "scene" to "Scene name"), listOf("project", "scene")) { a ->
            val scene = findScene(a)
            buildJsonObject {
                put("scene", scene.name)
                putJsonArray("sceneComponents") { scene.components.forEach { add(JsonPrimitive(it::class.simpleName)) } }
                put("entities", entityTree(scene.children, ""))
            }
        },
        Tool("get_entity", "Full serialized components of one entity, addressed by path (e.g. Parent/Child)",
            mapOf("project" to PROJECT, "scene" to "Scene name", "path" to "Entity path"),
            listOf("project", "scene", "path")) { a ->
            val scene = findScene(a)
            val entity = a.str("path").split('/').fold(scene as GameObject) { node, name ->
                node.children.firstOrNull { it.name == name } ?: throw ToolException("Entity '${a.str("path")}' not found")
            }
            json.parseToJsonElement(serializer.encode(entity))
        },
        Tool("get_logs", "Engine log entries produced in this server session", mapOf("limit" to "Max entries (default 100)"),
            emptyList()) { a ->
            val limit = a.optStr("limit")?.toIntOrNull() ?: 100
            buildJsonObject {
                putJsonArray("logs") {
                    service.logs.toList().takeLast(limit).forEach {
                        add(buildJsonObject {
                            put("level", it.level.name); put("source", it.source); put("message", it.message)
                        })
                    }
                }
            }
        },
    )

    private fun findScene(a: JsonObject): Scene {
        val name = a.str("scene")
        val scenes = service.loadScenes(service.loadProject(a.str("project")))
        return scenes.firstOrNull { it.name == name } ?: throw ToolException("Scene '$name' not found")
    }

    private fun entityTree(nodes: List<GameObject>, prefix: String): JsonArray = buildJsonArray {
        nodes.forEach { n ->
            val path = prefix + n.name
            add(buildJsonObject {
                put("name", n.name)
                put("path", path)
                putJsonArray("components") { n.components.forEach { add(JsonPrimitive(it::class.simpleName)) } }
                put("children", entityTree(n.children, "$path/"))
            })
        }
    }

    private fun JsonObject.optStr(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.str(key: String): String = optStr(key) ?: throw ToolException("Missing argument '$key'")

    /** Handles one JSON-RPC message; returns the response line, or null for notifications. */
    fun handle(line: String): String? {
        val request = try {
            json.parseToJsonElement(line).jsonObject
        } catch (e: Exception) {
            return error(JsonNull, -32700, "Parse error")
        }
        val id = request["id"]
        val method = request["method"]?.jsonPrimitive?.contentOrNull ?: return error(id ?: JsonNull, -32600, "Invalid request")
        if (id == null) return null // notification (e.g. notifications/initialized)
        val params = request["params"] as? JsonObject ?: JsonObject(emptyMap())
        return try {
            result(id, when (method) {
                "initialize" -> buildJsonObject {
                    put("protocolVersion", PROTOCOL_VERSION)
                    putJsonObject("capabilities") { putJsonObject("tools") {} }
                    putJsonObject("serverInfo") { put("name", SERVER_NAME); put("version", SERVER_VERSION) }
                }
                "ping" -> JsonObject(emptyMap())
                "tools/list" -> buildJsonObject { put("tools", toolList()) }
                "tools/call" -> callTool(params)
                else -> return error(id, -32601, "Method not found: $method")
            })
        } catch (e: ToolException) {
            error(id, -32602, e.message.orEmpty())
        }
    }

    private fun toolList() = buildJsonArray {
        tools.forEach { t ->
            add(buildJsonObject {
                put("name", t.name)
                put("description", t.description)
                putJsonObject("inputSchema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        t.properties.forEach { (k, d) -> putJsonObject(k) { put("type", "string"); put("description", d) } }
                    }
                    putJsonArray("required") { t.required.forEach { add(JsonPrimitive(it)) } }
                }
            })
        }
    }

    private fun callTool(params: JsonObject): JsonElement {
        val name = params["name"]?.jsonPrimitive?.contentOrNull ?: throw ToolException("Missing tool name")
        val tool = tools.firstOrNull { it.name == name } ?: throw ToolException("Unknown tool '$name'")
        val args = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        return try {
            toolResult(json.encodeToString(JsonElement.serializer(), tool.handler(args)), isError = false)
        } catch (e: ToolException) {
            toolResult(e.message.orEmpty(), isError = true)
        } catch (e: CliException) {
            toolResult(e.message.orEmpty(), isError = true)
        } catch (e: Exception) {
            toolResult("${e::class.simpleName}: ${e.message}", isError = true)
        }
    }

    private fun toolResult(text: String, isError: Boolean) = buildJsonObject {
        putJsonArray("content") { add(buildJsonObject { put("type", "text"); put("text", text) }) }
        put("isError", isError)
    }

    private fun result(id: JsonElement, result: JsonElement) = json.encodeToString(JsonElement.serializer(),
        buildJsonObject { put("jsonrpc", "2.0"); put("id", id); put("result", result) })

    private fun error(id: JsonElement, code: Int, message: String) = json.encodeToString(JsonElement.serializer(),
        buildJsonObject {
            put("jsonrpc", "2.0"); put("id", id)
            putJsonObject("error") { put("code", code); put("message", message) }
        })
}

fun main() {
    val server = SkateMcpServer()
    // stdout carries protocol messages only; engine logging must not pollute it
    val protocolOut = System.out
    System.setOut(System.err)
    generateSequence(::readlnOrNull).filter { it.isNotBlank() }.forEach { line ->
        server.handle(line)?.let { protocolOut.println(it); protocolOut.flush() }
    }
}
