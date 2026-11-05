package com.pafoid.skate.engine.render.rhi.vulkan

/**
 * Minimal SPIR-V reflection for the Vulkan spike (REN-13): enough to map named [ShaderParams][com.pafoid.skate.engine.render.rhi.ShaderParams]
 * onto std140 offsets and texture units onto descriptors, without SPIRV-Cross. Reads the debug names glslang keeps
 * (`OpName`, `OpMemberName`), decorations and types. Pure Kotlin, unit-tested on hand-built modules.
 */
class SpirvReflection private constructor(words: IntArray) {

    /** Scalar kind of a block member or vertex input. */
    enum class Scalar { FLOAT, INT, UINT, BOOL }

    /** A std140 block member: [components] scalars per column, [columns] columns [matrixStride] apart, [count] elements. */
    data class Member(
        val name: String,
        val offset: Int,
        val scalar: Scalar,
        val components: Int,
        val columns: Int = 1,
        val matrixStride: Int = 0,
        val count: Int = 1,
        val arrayStride: Int = 0,
    )

    enum class DescriptorKind { UNIFORM_BUFFER, COMBINED_IMAGE_SAMPLER }

    /** Image dimension of a sampler: 2D, 2D array or cube; [depth] samplers compare against a reference. */
    enum class ImageDim { D2, D2_ARRAY, CUBE }

    data class Descriptor(
        val binding: Int,
        val name: String,
        val kind: DescriptorKind,
        val count: Int = 1,
        val dim: ImageDim? = null,
        val depth: Boolean = false,
        /** Byte size of a uniform block. */
        val size: Int = 0,
        val members: List<Member> = emptyList(),
    )

    data class VertexInput(val location: Int, val scalar: Scalar, val components: Int)

    val descriptors: List<Descriptor>
    val vertexInputs: List<VertexInput>

    private sealed interface Type
    private data class ScalarType(val scalar: Scalar) : Type
    private data class VectorType(val component: Int, val count: Int) : Type
    private data class MatrixType(val column: Int, val columns: Int) : Type
    private data class ArrayType(val element: Int, val lengthId: Int) : Type
    private data class StructType(val members: IntArray) : Type
    private data class ImageType(val dim: Int, val depth: Int, val arrayed: Int) : Type
    private data class SampledImageType(val image: Int) : Type
    private data class PointerType(val storage: Int, val pointee: Int) : Type

    init {
        require(words.size >= 5 && words[0] == MAGIC) { "Not a SPIR-V module" }
        val names = HashMap<Int, String>()
        val memberNames = HashMap<Long, String>()
        val decorations = HashMap<Long, Int>() // (id, decoration) -> literal
        val memberDecorations = HashMap<Long, Int>() // (struct, member, decoration) -> literal
        val types = HashMap<Int, Type>()
        val constants = HashMap<Int, Int>()
        val variables = ArrayList<Triple<Int, Int, Int>>() // (pointer type, id, storage)

        var i = 5
        while (i < words.size) {
            val count = words[i] ushr 16
            val op = words[i] and 0xFFFF
            require(count > 0 && i + count <= words.size) { "Truncated SPIR-V at word $i" }
            val a = i + 1
            when (op) {
                OP_NAME -> names[words[a]] = string(words, a + 1, i + count)
                OP_MEMBER_NAME -> memberNames[key(words[a], words[a + 1])] = string(words, a + 2, i + count)
                OP_DECORATE -> decorations[key(words[a], words[a + 1])] = if (count > 3) words[a + 2] else 1
                OP_MEMBER_DECORATE ->
                    memberDecorations[key3(words[a], words[a + 1], words[a + 2])] = if (count > 4) words[a + 3] else 1
                OP_TYPE_BOOL -> types[words[a]] = ScalarType(Scalar.BOOL)
                OP_TYPE_INT -> types[words[a]] = ScalarType(if (words[a + 2] != 0) Scalar.INT else Scalar.UINT)
                OP_TYPE_FLOAT -> types[words[a]] = ScalarType(Scalar.FLOAT)
                OP_TYPE_VECTOR -> types[words[a]] = VectorType(words[a + 1], words[a + 2])
                OP_TYPE_MATRIX -> types[words[a]] = MatrixType(words[a + 1], words[a + 2])
                OP_TYPE_IMAGE -> types[words[a]] = ImageType(words[a + 2], words[a + 3], words[a + 4])
                OP_TYPE_SAMPLED_IMAGE -> types[words[a]] = SampledImageType(words[a + 1])
                OP_TYPE_ARRAY -> types[words[a]] = ArrayType(words[a + 1], words[a + 2])
                OP_TYPE_STRUCT -> types[words[a]] = StructType(words.copyOfRange(a + 1, i + count))
                OP_TYPE_POINTER -> types[words[a]] = PointerType(words[a + 1], words[a + 2])
                OP_CONSTANT -> constants[words[a + 1]] = words[a + 2]
                OP_VARIABLE -> variables += Triple(words[a], words[a + 1], words[a + 2])
            }
            i += count
        }

        fun scalarOf(id: Int): Pair<Scalar, Int> = when (val t = types[id]) {
            is ScalarType -> t.scalar to 1
            is VectorType -> scalarOf(t.component).first to t.count
            else -> throw IllegalArgumentException("Type $id is not a scalar or vector")
        }

        fun members(struct: Int): List<Member> {
            val st = types[struct] as StructType
            return st.members.mapIndexed { index, typeId ->
                val name = memberNames[key(struct, index)] ?: "member$index"
                val offset = memberDecorations[key3(struct, index, DECORATION_OFFSET)] ?: 0
                var t = typeId
                var arrayCount = 1
                var arrayStride = 0
                (types[t] as? ArrayType)?.let { arr ->
                    arrayCount = constants[arr.lengthId] ?: 1
                    arrayStride = decorations[key(t, DECORATION_ARRAY_STRIDE)] ?: 0
                    t = arr.element
                }
                when (val mt = types[t]) {
                    is MatrixType -> {
                        val (scalar, comps) = scalarOf(mt.column)
                        Member(name, offset, scalar, comps, mt.columns,
                            memberDecorations[key3(struct, index, DECORATION_MATRIX_STRIDE)] ?: 16, arrayCount, arrayStride)
                    }
                    is StructType -> Member(name, offset, Scalar.FLOAT, 0, 1, 0, arrayCount, arrayStride)
                    else -> {
                        val (scalar, comps) = scalarOf(t)
                        Member(name, offset, scalar, comps, count = arrayCount, arrayStride = arrayStride)
                    }
                }
            }
        }

        fun blockSize(struct: Int): Int = members(struct).maxOfOrNull { m ->
            val element = if (m.columns > 1) m.matrixStride * m.columns else m.components * 4
            m.offset + if (m.count > 1) m.arrayStride * m.count else element
        } ?: 0

        val descriptorList = ArrayList<Descriptor>()
        val inputs = ArrayList<VertexInput>()
        for ((pointerType, id, storage) in variables) {
            val pointee = (types[pointerType] as? PointerType)?.pointee ?: continue
            when (storage) {
                STORAGE_UNIFORM, STORAGE_UNIFORM_CONSTANT -> {
                    val binding = decorations[key(id, DECORATION_BINDING)] ?: continue
                    var t = pointee
                    var arrayCount = 1
                    (types[t] as? ArrayType)?.let { arr -> arrayCount = constants[arr.lengthId] ?: 1; t = arr.element }
                    when (val target = types[t]) {
                        is StructType -> descriptorList += Descriptor(
                            binding, names[t] ?: names[id] ?: "block$binding", DescriptorKind.UNIFORM_BUFFER,
                            size = blockSize(t), members = members(t),
                        )
                        is SampledImageType -> {
                            val image = types[target.image] as ImageType
                            val dim = when {
                                image.dim == DIM_CUBE -> ImageDim.CUBE
                                image.arrayed != 0 -> ImageDim.D2_ARRAY
                                else -> ImageDim.D2
                            }
                            descriptorList += Descriptor(
                                binding, names[id] ?: "sampler$binding", DescriptorKind.COMBINED_IMAGE_SAMPLER,
                                arrayCount, dim, depth = image.depth == 1,
                            )
                        }
                        else -> {}
                    }
                }
                STORAGE_INPUT -> {
                    val location = decorations[key(id, DECORATION_LOCATION)] ?: continue
                    if (decorations.containsKey(key(id, DECORATION_BUILT_IN))) continue
                    val (scalar, comps) = runCatching { scalarOf(pointee) }.getOrNull() ?: continue
                    inputs += VertexInput(location, scalar, comps)
                }
            }
        }
        descriptors = descriptorList.sortedBy { it.binding }
        vertexInputs = inputs.sortedBy { it.location }
    }

    companion object {
        const val MAGIC = 0x07230203

        const val OP_NAME = 5
        const val OP_MEMBER_NAME = 6
        const val OP_TYPE_BOOL = 20
        const val OP_TYPE_INT = 21
        const val OP_TYPE_FLOAT = 22
        const val OP_TYPE_VECTOR = 23
        const val OP_TYPE_MATRIX = 24
        const val OP_TYPE_IMAGE = 25
        const val OP_TYPE_SAMPLED_IMAGE = 27
        const val OP_TYPE_ARRAY = 28
        const val OP_TYPE_STRUCT = 30
        const val OP_TYPE_POINTER = 32
        const val OP_CONSTANT = 43
        const val OP_VARIABLE = 59
        const val OP_DECORATE = 71
        const val OP_MEMBER_DECORATE = 72

        const val DECORATION_ARRAY_STRIDE = 6
        const val DECORATION_MATRIX_STRIDE = 7
        const val DECORATION_BUILT_IN = 11
        const val DECORATION_LOCATION = 30
        const val DECORATION_BINDING = 33
        const val DECORATION_OFFSET = 35

        const val STORAGE_UNIFORM_CONSTANT = 0
        const val STORAGE_INPUT = 1
        const val STORAGE_UNIFORM = 2
        const val DIM_CUBE = 3

        fun of(words: IntArray) = SpirvReflection(words)

        private fun key(a: Int, b: Int): Long = (a.toLong() shl 32) or (b.toLong() and 0xFFFFFFFFL)
        private fun key3(a: Int, b: Int, c: Int): Long = (a.toLong() shl 40) or ((b.toLong() and 0xFFFFF) shl 20) or (c.toLong() and 0xFFFFF)

        /** A nul-terminated UTF-8 literal packed little-endian into words [from, to). */
        fun string(words: IntArray, from: Int, to: Int): String {
            val bytes = java.io.ByteArrayOutputStream()
            loop@ for (w in from until to) for (shift in 0 until 4) {
                val b = (words[w] ushr (shift * 8)) and 0xFF
                if (b == 0) break@loop
                bytes.write(b)
            }
            return bytes.toString(Charsets.UTF_8)
        }
    }
}
