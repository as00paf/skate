package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.models.RawModel
import org.joml.Vector2f
import org.joml.Vector3f
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.lang.Exception

class ObjLoader {

    private data class VertexIndices(val p: Int, val t: Int, val n: Int)

    fun loadObjModel(fileName: String, loader: VAOLoader): RawModel {
        try {
            val file = File(fileName)
            val reader = BufferedReader(FileReader(file))
            
            val positions = mutableListOf<Vector3f>()
            val texCoords = mutableListOf<Vector2f>()
            val normals = mutableListOf<Vector3f>()
            val faces = mutableListOf<List<VertexIndices>>()

            reader.forEachLine { line ->
                val tokens = line.trim().split(Regex("\\s+"))
                if (tokens.isEmpty()) return@forEachLine

                when (tokens[0]) {
                    "v" -> positions.add(Vector3f(tokens[1].toFloat(), tokens[2].toFloat(), tokens[3].toFloat()))
                    "vt" -> texCoords.add(Vector2f(tokens[1].toFloat(), tokens[2].toFloat()))
                    "vn" -> normals.add(Vector3f(tokens[1].toFloat(), tokens[2].toFloat(), tokens[3].toFloat()))
                    "f" -> {
                        val faceIndices = mutableListOf<VertexIndices>()
                        for (i in 1 until tokens.size) {
                            val parts = tokens[i].split("/")
                            val p = parts[0].toInt() - 1
                            val t = if (parts.size > 1 && parts[1].isNotEmpty()) parts[1].toInt() - 1 else -1
                            val n = if (parts.size > 2 && parts[2].isNotEmpty()) parts[2].toInt() - 1 else -1
                            faceIndices.add(VertexIndices(p, t, n))
                        }
                        // Triangulate fan if necessary
                        for (i in 1 until faceIndices.size - 1) {
                            faces.add(listOf(faceIndices[0], faceIndices[i], faceIndices[i + 1]))
                        }
                    }
                }
            }
            reader.close()

            // Map unique combinations to new vertex indices
            val uniqueVertices = mutableMapOf<VertexIndices, Int>()
            val outPositions = mutableListOf<Float>()
            val outTexCoords = mutableListOf<Float>()
            val outNormals = mutableListOf<Float>()
            val outColors = mutableListOf<Float>() // Added for vertex colors
            val outIndices = mutableListOf<Int>()

            for (face in faces) {
                for (vIndices in face) {
                    if (!uniqueVertices.containsKey(vIndices)) {
                        val index = uniqueVertices.size
                        uniqueVertices[vIndices] = index
                        
                        val p = positions[vIndices.p]
                        outPositions.add(p.x); outPositions.add(p.y); outPositions.add(p.z)
                        
                        if (vIndices.t != -1) {
                            val t = texCoords[vIndices.t]
                            outTexCoords.add(t.x); outTexCoords.add(1f - t.y)
                        } else {
                            outTexCoords.add(0f); outTexCoords.add(0f)
                        }
                        
                        if (vIndices.n != -1) {
                            val n = normals[vIndices.n]
                            outNormals.add(n.x); outNormals.add(n.y); outNormals.add(n.z)
                        } else {
                            outNormals.add(0f); outNormals.add(1f); outNormals.add(0f)
                        }

                        // Default to white vertex color
                        outColors.add(1f); outColors.add(1f); outColors.add(1f); outColors.add(1f)
                    }
                    outIndices.add(uniqueVertices[vIndices]!!)
                }
            }

            val posArray = outPositions.toFloatArray()
            return loader.loadToVAO(
                posArray, 
                outTexCoords.toFloatArray(), 
                outNormals.toFloatArray(), 
                outIndices.toIntArray(),
                posArray,
                floatArrayOf(), // tangents
                outColors.toFloatArray() // colors
            )
        } catch (e: Exception) {
            println("Could not load obj file $fileName: ${e.message}")
            throw e
        }
    }

    companion object {
        const val CUBE = "assets/obj/cube.obj"
        const val RAIL = "assets/obj/rail.obj"
        const val LEDGE = "assets/obj/ledge.obj"
        const val KICKER = "assets/obj/kicker.obj"
        const val MANUAL_PAD = "assets/obj/manual_pad.obj"
        const val BANK = "assets/obj/bank.obj"
        const val QUARTER_PIPE = "assets/obj/quarter_pipe.obj"
        const val SKATEBOARD_GLB = "assets/obj/skateboard_free_model.glb"
        const val PLAYER_GLTF = "assets/characters/Superhero_Male_FullBody.gltf"
    }
}