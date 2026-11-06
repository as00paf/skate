package com.pafoid.skate.engine.render.rhi.vulkan

/**
 * Rewrites the engine's GLSL 330 programs (REN-05 preprocessed) into Vulkan GLSL 450 (REN-13 spike), so the same
 * shader files and the same named [com.pafoid.skate.engine.render.rhi.ShaderParams] work on both backends:
 *
 * - Loose (non-opaque) uniforms of both stages move into one std140 block, `Params`, at set 0 binding [PARAMS_BINDING],
 *   declared identically in both stages (vertex members first). Both stages' `#define`s are hoisted above it (first
 *   definition wins) so array sizes resolve.
 * - Existing std140 blocks and samplers get explicit bindings, shared by name across the stages.
 * - Vertex outputs get sequential locations and fragment inputs the location of the vertex output of the same name;
 *   the fragment colour output gets location 0.
 * - The vertex stage remaps GL clip depth ([-w, w]) to Vulkan's [0, w] ([ClipSpace.vulkanZ]); the backend flips Y with a
 *   negative viewport height, so projection matrices stay GL-style.
 *
 * GL-free and Vulkan-free text processing, unit-tested. It handles the constructs the engine's shaders use, not GLSL
 * in general: uniforms are single declarations at global scope, outside conditionals.
 */
object GlslToVulkan {
    const val PARAMS_BINDING = 0
    const val PARAMS_BLOCK = "Params"

    /** A program ready for shaderc, plus the bindings it was given (the device builds its descriptor layout from them). */
    data class Result(
        val vertex: String,
        val fragment: String,
        /** Loose uniforms moved into [PARAMS_BLOCK], in block order (declaration text without `uniform`). */
        val params: List<String>,
        /** Std140 block name -> binding. */
        val blocks: Map<String, Int>,
        /** Sampler name -> binding. */
        val samplers: Map<String, Int>,
    )

    private val VERSION = Regex("""^\s*#version\s+\d+.*$""")
    private val DEFINE = Regex("""^\s*#define\s+\w+.*$""")
    private val LOOSE_UNIFORM = Regex("""^\s*uniform\s+(\w+)\s+([^;{]+);""")
    private val BLOCK = Regex("""^\s*layout\s*\(\s*std140\s*\)\s*uniform\s+(\w+)\s*\{?""")
    private val VARYING = Regex("""^\s*(flat\s+|smooth\s+|noperspective\s+)?(in|out)\s+(\w+)\s+(\w+)\s*(\[\s*(\d+)\s*])?\s*;""")
    private val MAIN = Regex("""\bvoid\s+main\s*\(\s*\)""")

    fun convert(vertexSource: String, fragmentSource: String): Result {
        val vertex = vertexSource.lines()
        val fragment = fragmentSource.lines()

        // Shared binding numbers: Params first, then blocks, then samplers, each in first-seen order
        val params = LinkedHashMap<String, String>()
        val blocks = LinkedHashMap<String, Int>()
        val samplers = LinkedHashMap<String, Int>()
        var next = PARAMS_BINDING + 1
        for (stage in listOf(vertex, fragment)) for (raw in stage) {
            val line = stripComment(raw)
            BLOCK.find(line)?.let { m -> if (m.groupValues[1] !in blocks) blocks[m.groupValues[1]] = next++ }
        }
        for (stage in listOf(vertex, fragment)) for (raw in stage) {
            val line = stripComment(raw)
            val m = LOOSE_UNIFORM.find(line) ?: continue
            val type = m.groupValues[1]
            val declarator = m.groupValues[2].trim()
            require('=' !in declarator) { "Uniform initializers are not supported in Vulkan blocks: '$line'" }
            require(',' !in declarator) { "One uniform per declaration: '$line'" }
            val name = declarator.substringBefore('[').trim()
            if (isOpaque(type)) {
                if (name !in samplers) samplers[name] = next++
            } else if (name !in params) {
                params[name] = "$type $declarator"
            }
        }

        // The shared Params block may size arrays with either stage's macros: both stages get every #define
        val defines = LinkedHashMap<String, String>()
        for (stage in listOf(vertex, fragment)) for (raw in stage) {
            if (DEFINE.matches(stripComment(raw))) defines.putIfAbsent(raw.trim().split(Regex("""\s+"""))[1], raw.trim())
        }
        val outputs = LinkedHashMap<String, Int>()
        val convertedVertex = convertStage(vertex, defines.values.toList(), params.values.toList(), blocks, samplers, outputs, isVertex = true)
        val convertedFragment = convertStage(fragment, defines.values.toList(), params.values.toList(), blocks, samplers, outputs, isVertex = false)
        return Result(convertedVertex, convertedFragment, params.values.toList(), blocks, samplers)
    }

    private fun convertStage(
        lines: List<String>,
        defines: List<String>,
        params: List<String>,
        blocks: Map<String, Int>,
        samplers: Map<String, Int>,
        outputs: MutableMap<String, Int>,
        isVertex: Boolean,
    ): String {
        val body = ArrayList<String>()
        var nextLocation = 0
        var fragmentOutputs = 0
        for (raw in lines) {
            val line = stripComment(raw)
            when {
                VERSION.matches(line) -> continue
                DEFINE.matches(line) -> continue
                BLOCK.containsMatchIn(line) -> {
                    val name = BLOCK.find(line)!!.groupValues[1]
                    body += raw.replaceFirst(Regex("""layout\s*\(\s*std140\s*\)"""), "layout(std140, set = 0, binding = ${blocks.getValue(name)})")
                }
                LOOSE_UNIFORM.containsMatchIn(line) -> {
                    val m = LOOSE_UNIFORM.find(line)!!
                    val name = m.groupValues[2].substringBefore('[').trim()
                    if (isOpaque(m.groupValues[1])) {
                        body += raw.replaceFirst("uniform", "layout(set = 0, binding = ${samplers.getValue(name)}) uniform")
                    }
                    // Non-opaque uniforms live in the Params block
                }
                !line.trimStart().startsWith("layout") && VARYING.containsMatchIn(line) -> {
                    val m = VARYING.find(line)!!
                    val direction = m.groupValues[2]
                    val type = m.groupValues[3]
                    val name = m.groupValues[4]
                    val count = m.groupValues[6].toIntOrNull() ?: 1
                    val location = when {
                        isVertex && direction == "out" -> nextLocation.also {
                            outputs[name] = it
                            nextLocation += locationsOf(type) * count
                        }
                        !isVertex && direction == "in" -> outputs[name]
                            ?: throw IllegalArgumentException("Fragment input '$name' has no matching vertex output")
                        !isVertex && direction == "out" -> fragmentOutputs++
                        else -> throw IllegalArgumentException("Vertex inputs need an explicit location: '$line'")
                    }
                    body += "layout(location = $location) ${raw.trimStart()}"
                }
                else -> body += raw
            }
        }

        val out = StringBuilder("#version 450\n")
        defines.forEach { out.append(it).append('\n') }
        if (params.isNotEmpty()) {
            out.append("layout(std140, set = 0, binding = $PARAMS_BINDING) uniform $PARAMS_BLOCK {\n")
            params.forEach { out.append("    ").append(it).append(";\n") }
            out.append("};\n")
        }
        var text = body.joinToString("\n")
            .replace("gl_VertexID", "gl_VertexIndex")
            .replace("gl_InstanceID", "gl_InstanceIndex")
        if (isVertex) {
            require(MAIN.containsMatchIn(text)) { "Vertex stage has no main()" }
            text = MAIN.replace(text, "void skate_main()") + "\n" + DEPTH_FIXUP
        }
        out.append(text).append('\n')
        return out.toString()
    }

    /** GL clip depth [-w, w] -> Vulkan [0, w]; see [ClipSpace.vulkanZ]. */
    private const val DEPTH_FIXUP = """
void main() {
    skate_main();
    gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;
}
"""

    fun isOpaque(type: String) = type.startsWith("sampler") || type.startsWith("isampler") || type.startsWith("usampler")

    /** Interface locations a varying of [type] uses (matrices take one per column). */
    fun locationsOf(type: String): Int = when (type) {
        "mat2" -> 2
        "mat3" -> 3
        "mat4" -> 4
        else -> 1
    }

    private fun stripComment(line: String) = line.substringBefore("//")
}

/** Clip-space conventions the Vulkan backend fixes up so GL-style projection matrices keep working (REN-12 §7.4). */
object ClipSpace {
    /** Vulkan clip z for GL clip ([z], [w]): NDC depth -1..1 becomes 0..1, so depth tests and clears agree. */
    fun vulkanZ(z: Float, w: Float): Float = (z + w) * 0.5f

    /**
     * Viewport that flips Y (VK_KHR_maintenance1, core 1.1): GL's bottom-left target origin over Vulkan's top-left, so
     * the image lands in memory top row first, like a picture. Returns (x, y, width, height).
     */
    fun flippedViewport(x: Int, y: Int, width: Int, height: Int, targetHeight: Int): FloatArray =
        floatArrayOf(x.toFloat(), (targetHeight - y).toFloat(), width.toFloat(), -height.toFloat())

    /** A GL scissor (bottom-left origin) in Vulkan framebuffer coordinates (top-left origin). Returns (x, y, w, h). */
    fun scissor(x: Int, y: Int, width: Int, height: Int, targetHeight: Int): IntArray =
        intArrayOf(x, targetHeight - (y + height), width, height)
}
