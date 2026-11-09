package com.pafoid.skate.engine.render.lighting

import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Image-based lighting math (REN-27), GL-free and unit-tested. [EnvironmentProbe] bakes the sky with it;
 * `shader_3d_default.glsl` mirrors the encoding, the sky mapping and the spherical-harmonics evaluation.
 *
 * - **Radiance encoding.** The sky texture holds display values (the sky shader draws them as is), while meshes are lit
 *   in linear radiance and then tonemapped (Reinhard) and gamma-corrected. [decode] inverts that, so a perfect mirror
 *   shows the sky exactly as the dome draws it. [encode] is the forward curve, used to store the baked radiance in
 *   RGBA8 textures, which upload the same way on every backend.
 * - **Sky mapping.** [skyUv]/[skyDir] use the sky dome's UV sphere convention: `v` 0 at the nadir, 1 at the zenith.
 * - **Specular.** GGX importance sampling ([importanceSampleGgx], [prefilter]) for one roughness per mip level, and the
 *   split-sum BRDF lookup table ([integrateBrdf], [brdfLut]).
 * - **Diffuse.** Irradiance as 9 spherical-harmonic coefficients ([shProject], [shIrradiance]).
 */
object IblMath {
    /** Brightest radiance the encoding keeps (a display value of about 0.98). */
    const val MAX_RADIANCE = 16f
    private const val GAMMA = 2.2f
    private val MAX_LINEAR = MAX_RADIANCE / (1f + MAX_RADIANCE)

    /** Display value (0..1) whose Reinhard + gamma output is [radiance]'s; the inverse of [decode]. */
    fun encode(radiance: Float): Float {
        val r = radiance.coerceIn(0f, MAX_RADIANCE)
        return (r / (1f + r)).pow(1f / GAMMA)
    }

    /** Linear radiance that the mesh shader's tonemap turns into display value [display]. */
    fun decode(display: Float): Float {
        val linear = display.coerceIn(0f, 1f).pow(GAMMA).coerceAtMost(MAX_LINEAR)
        return linear / (1f - linear)
    }

    // ---- Sky mapping (SkyDomeRenderer's UV sphere) ----

    /** Sky texture coordinates of the sky-local direction [dir] (any length) into [out] (u, v). */
    fun skyUv(dir: Vector3f, out: FloatArray): FloatArray {
        val len = dir.length()
        val y = (dir.y / len).coerceIn(-1f, 1f)
        var u = (atan2(dir.z.toDouble(), dir.x.toDouble()) / (2 * PI)).toFloat()
        if (u < 0f) u += 1f
        out[0] = u
        out[1] = ((asin(y.toDouble()) + PI / 2) / PI).toFloat()
        return out
    }

    /** Unit sky-local direction of texture coordinates ([u], [v]). */
    fun skyDir(u: Float, v: Float, out: Vector3f): Vector3f {
        val elevation = -PI / 2 + PI * v
        val azimuth = 2 * PI * u
        return out.set(
            (cos(azimuth) * cos(elevation)).toFloat(),
            sin(elevation).toFloat(),
            (sin(azimuth) * cos(elevation)).toFloat(),
        )
    }

    /**
     * World direction [world] in sky-local space, for a dome turned by [rotation] radians around Y (the dome's model
     * matrix is `rotateY(rotation)`, so this applies the inverse).
     */
    fun toSkyLocal(world: Vector3f, rotation: Float, out: Vector3f): Vector3f {
        val c = cos(rotation)
        val s = sin(rotation)
        return out.set(c * world.x - s * world.z, world.y, s * world.x + c * world.z)
    }

    /** How much of the sky (vs the fog colour) the dome shows at [v]: it fades to fog below the horizon. */
    fun horizonBlend(v: Float): Float = smoothstep(0.4f, 0.55f, v)

    // ---- Sampling ----

    /** Point [i] of [n] of the Hammersley set into [out] (x, y in 0..1). */
    fun hammersley(i: Int, n: Int, out: FloatArray): FloatArray {
        var bits = i
        bits = (bits shl 16) or (bits ushr 16)
        bits = ((bits and 0x55555555) shl 1) or ((bits and 0xAAAAAAAA.toInt()) ushr 1)
        bits = ((bits and 0x33333333) shl 2) or ((bits and 0xCCCCCCCC.toInt()) ushr 2)
        bits = ((bits and 0x0F0F0F0F) shl 4) or ((bits and 0xF0F0F0F0.toInt()) ushr 4)
        bits = ((bits and 0x00FF00FF) shl 8) or ((bits and 0xFF00FF00.toInt()) ushr 8)
        out[0] = i.toFloat() / n
        out[1] = (bits.toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
        return out
    }

    /** GGX half vector around unit normal [n] for sample ([x1], [x2]) and perceptual [roughness], into [out]. */
    fun importanceSampleGgx(x1: Float, x2: Float, n: Vector3f, roughness: Float, out: Vector3f): Vector3f {
        val a = roughness * roughness
        val phi = 2f * PI.toFloat() * x1
        val cosTheta = sqrt((1f - x2) / (1f + (a * a - 1f) * x2))
        val sinTheta = sqrt(max(0f, 1f - cosTheta * cosTheta))
        val hx = sinTheta * cos(phi)
        val hy = sinTheta * sin(phi)
        // Tangent frame around n
        val upX: Float; val upY: Float; val upZ: Float
        if (abs(n.z) < 0.999f) { upX = 0f; upY = 0f; upZ = 1f } else { upX = 1f; upY = 0f; upZ = 0f }
        var tx = upY * n.z - upZ * n.y
        var ty = upZ * n.x - upX * n.z
        var tz = upX * n.y - upY * n.x
        val tl = sqrt(tx * tx + ty * ty + tz * tz)
        tx /= tl; ty /= tl; tz /= tl
        val bx = n.y * tz - n.z * ty
        val by = n.z * tx - n.x * tz
        val bz = n.x * ty - n.y * tx
        return out.set(
            tx * hx + bx * hy + n.x * cosTheta,
            ty * hx + by * hy + n.y * cosTheta,
            tz * hx + bz * hy + n.z * cosTheta,
        ).normalize()
    }

    /** GGX normal distribution for perceptual [roughness]. */
    fun ggx(nDotH: Float, roughness: Float): Float {
        val a2 = (roughness * roughness).let { it * it }
        val d = nDotH * nDotH * (a2 - 1f) + 1f
        return a2 / (PI.toFloat() * d * d)
    }

    // ---- Radiance images ----

    /** Linear RGB radiance over the sky's (u, v) square: [rgb] holds rows of `v` 0 (nadir) upward. */
    class RadianceImage(val width: Int, val height: Int, val rgb: FloatArray = FloatArray(width * height * 3)) {
        init {
            require(rgb.size == width * height * 3) { "${rgb.size} floats for ${width}x$height" }
        }

        fun set(x: Int, y: Int, r: Float, g: Float, b: Float) {
            val i = (y * width + x) * 3
            rgb[i] = r; rgb[i + 1] = g; rgb[i + 2] = b
        }

        /** Bilinear sample at ([u], [v]): `u` wraps around, `v` clamps at the poles. Adds [weight] times it to [acc]. */
        fun sampleAdd(u: Float, v: Float, weight: Float, acc: FloatArray) {
            val fx = u * width - 0.5f
            val fy = (v * height - 0.5f).coerceIn(0f, height - 1f)
            val x0 = floor(fx).toInt()
            val y0 = floor(fy).toInt()
            val tx = fx - x0
            val ty = fy - y0
            val y1 = min(y0 + 1, height - 1)
            val xa = Math.floorMod(x0, width)
            val xb = Math.floorMod(x0 + 1, width)
            for (c in 0 until 3) {
                val top = rgb[(y0 * width + xa) * 3 + c] * (1f - tx) + rgb[(y0 * width + xb) * 3 + c] * tx
                val bottom = rgb[(y1 * width + xa) * 3 + c] * (1f - tx) + rgb[(y1 * width + xb) * 3 + c] * tx
                acc[c] += weight * (top * (1f - ty) + bottom * ty)
            }
        }

        /** Half the size, each texel the average of four. */
        fun downsample(): RadianceImage {
            val w = max(1, width / 2)
            val h = max(1, height / 2)
            val out = RadianceImage(w, h)
            for (y in 0 until h) for (x in 0 until w) for (c in 0 until 3) {
                var sum = 0f
                for (dy in 0..1) for (dx in 0..1) {
                    val sx = min(x * 2 + dx, width - 1)
                    val sy = min(y * 2 + dy, height - 1)
                    sum += rgb[(sy * width + sx) * 3 + c]
                }
                out.rgb[(y * w + x) * 3 + c] = sum / 4f
            }
            return out
        }
    }

    /** [base] and its successive [RadianceImage.downsample]s down to 8 texels wide. */
    fun pyramid(base: RadianceImage): List<RadianceImage> {
        val levels = arrayListOf(base)
        while (levels.last().width > 8) levels += levels.last().downsample()
        return levels
    }

    /**
     * GGX-prefiltered radiance ([width] x [height]) for [roughness]: each texel integrates the environment around its
     * mirror direction (N = V = R) with [samples] importance samples, reading coarser [source] levels for wide
     * samples (filtered importance sampling) so few samples stay smooth.
     */
    fun prefilter(source: List<RadianceImage>, width: Int, height: Int, roughness: Float, samples: Int): RadianceImage {
        val out = RadianceImage(width, height)
        val n = Vector3f(); val h = Vector3f(); val l = Vector3f()
        val xi = FloatArray(2); val uv = FloatArray(2); val acc = FloatArray(3)
        val base = source.first()
        val texelSolidAngle = 4f * PI.toFloat() / (base.width * base.height)
        for (y in 0 until height) for (x in 0 until width) {
            skyDir((x + 0.5f) / width, (y + 0.5f) / height, n)
            acc.fill(0f)
            var weight = 0f
            for (i in 0 until samples) {
                hammersley(i, samples, xi)
                importanceSampleGgx(xi[0], xi[1], n, roughness, h)
                val vDotH = n.dot(h)
                l.set(h).mul(2f * vDotH).sub(n)
                val nDotL = n.dot(l)
                if (nDotL <= 0f) continue
                val pdf = ggx(max(vDotH, 0f), roughness) / 4f
                val sampleSolidAngle = 1f / (samples * pdf + 1e-4f)
                val level = if (roughness <= 0f) 0 else
                    (0.5f * log2(sampleSolidAngle / texelSolidAngle) + 1f).roundToInt().coerceIn(0, source.size - 1)
                skyUv(l, uv)
                source[level].sampleAdd(uv[0], uv[1], nDotL, acc)
                weight += nDotL
            }
            if (weight > 0f) out.set(x, y, acc[0] / weight, acc[1] / weight, acc[2] / weight)
        }
        return out
    }

    // ---- BRDF lookup table (split sum) ----

    /** Smith geometry term for image-based lighting (k = alpha / 2). */
    private fun geometrySmithIbl(nDotV: Float, nDotL: Float, roughness: Float): Float {
        val k = roughness * roughness / 2f
        return (nDotV / (nDotV * (1f - k) + k)) * (nDotL / (nDotL * (1f - k) + k))
    }

    /**
     * Split-sum scale and bias (A, B) into [out]: specular IBL = prefiltered * (F0 * A + B), for a view at [nDotV] to
     * the normal and perceptual [roughness].
     */
    fun integrateBrdf(nDotV: Float, roughness: Float, samples: Int, out: FloatArray): FloatArray {
        val v = Vector3f(sqrt(max(0f, 1f - nDotV * nDotV)), 0f, nDotV)
        val n = Vector3f(0f, 0f, 1f)
        val h = Vector3f(); val xi = FloatArray(2)
        var a = 0f; var b = 0f
        for (i in 0 until samples) {
            hammersley(i, samples, xi)
            importanceSampleGgx(xi[0], xi[1], n, roughness, h)
            val vDotH = max(v.dot(h), 0f)
            val lz = 2f * vDotH * h.z - v.z
            if (lz <= 0f) continue
            val nDotH = max(h.z, 0f)
            val gVis = geometrySmithIbl(nDotV, lz, roughness) * vDotH / (nDotH * nDotV + 1e-6f)
            val fc = (1f - vDotH).pow(5)
            a += (1f - fc) * gVis
            b += fc * gVis
        }
        out[0] = a / samples
        out[1] = b / samples
        return out
    }

    /** [size] x [size] table of (A, B): x = NdotV, y = roughness, at texel centres; 2 floats per texel, row-major. */
    fun brdfLut(size: Int, samples: Int): FloatArray {
        val out = FloatArray(size * size * 2)
        val ab = FloatArray(2)
        for (y in 0 until size) for (x in 0 until size) {
            integrateBrdf((x + 0.5f) / size, (y + 0.5f) / size, samples, ab)
            out[(y * size + x) * 2] = ab[0]
            out[(y * size + x) * 2 + 1] = ab[1]
        }
        return out
    }

    // ---- Spherical harmonics (irradiance) ----

    /** The 9 real SH basis functions (bands 0-2) of unit direction (x, y, z) into [out]. */
    fun shBasis(x: Float, y: Float, z: Float, out: FloatArray): FloatArray {
        out[0] = 0.282095f
        out[1] = 0.488603f * y
        out[2] = 0.488603f * z
        out[3] = 0.488603f * x
        out[4] = 1.092548f * x * y
        out[5] = 1.092548f * y * z
        out[6] = 0.315392f * (3f * z * z - 1f)
        out[7] = 1.092548f * x * z
        out[8] = 0.546274f * (x * x - y * y)
        return out
    }

    /** Projects [image] onto 9 SH coefficients per channel: 27 floats, coefficient-major (c0 r, g, b, c1 r, ...). */
    fun shProject(image: RadianceImage): FloatArray {
        val sh = FloatArray(27)
        val dir = Vector3f(); val basis = FloatArray(9)
        val dPhi = 2f * PI.toFloat() / image.width
        val dTheta = PI.toFloat() / image.height
        for (y in 0 until image.height) {
            val v = (y + 0.5f) / image.height
            val solidAngle = dPhi * dTheta * cos(-PI.toFloat() / 2f + PI.toFloat() * v)
            for (x in 0 until image.width) {
                skyDir((x + 0.5f) / image.width, v, dir)
                shBasis(dir.x, dir.y, dir.z, basis)
                val i = (y * image.width + x) * 3
                for (k in 0 until 9) for (c in 0 until 3) sh[k * 3 + c] += image.rgb[i + c] * basis[k] * solidAngle
            }
        }
        return sh
    }

    /** Cosine-lobe convolution weights per band (Ramamoorthi and Hanrahan). */
    private val BAND_WEIGHT = floatArrayOf(PI.toFloat(), 2f * PI.toFloat() / 3f, PI.toFloat() / 4f)
    private val BAND_OF = intArrayOf(0, 1, 1, 1, 2, 2, 2, 2, 2)

    /** Coefficients scaled by their band's cosine weight, so evaluating them gives irradiance (what the shader gets). */
    fun shConvolve(sh: FloatArray): FloatArray = FloatArray(27) { sh[it] * BAND_WEIGHT[BAND_OF[it / 3]] }

    /** Irradiance E arriving at a surface facing unit direction [n] from convolved coefficients [convolved]. */
    fun shIrradiance(convolved: FloatArray, n: Vector3f, out: Vector3f): Vector3f {
        val basis = shBasis(n.x, n.y, n.z, FloatArray(9))
        var r = 0f; var g = 0f; var b = 0f
        for (k in 0 until 9) {
            r += convolved[k * 3] * basis[k]
            g += convolved[k * 3 + 1] * basis[k]
            b += convolved[k * 3 + 2] * basis[k]
        }
        return out.set(max(r, 0f), max(g, 0f), max(b, 0f))
    }

    private fun log2(x: Float): Float = (ln(x.toDouble()) / ln(2.0)).toFloat()

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
