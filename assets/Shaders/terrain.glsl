// @skate-contract 1
// ----------------------------------------------------------------------------
// Terrain chunks (REN-28 §4): a shared grid (sample column, row, skirt flag) plus a per-chunk stream (height, normal
// x, normal z, dominant biome). Lit with lighting.glsl like meshes. Variants: PICKING writes the entity id, DEPTH_ONLY
// is the shadow caster. Materials (REN-28 task 5): 8 splat weights per sample from the chunk's SplatAtlas layer,
// reduced to the 4 strongest per pixel (TerrainWeights.topFour), height-blended, triplanar on steep slopes for the
// layers that ask for it, and a far tiling blend against repetition. Chunks without splat shade by height and slope.
// ----------------------------------------------------------------------------

#type vertex
#version 330 core
layout (location=0) in vec3 aGrid;   // i, j, skirt (1 on skirt copies)
layout (location=1) in vec4 aStream; // height (m, terrain-local), normal x, normal z, dominant biome

uniform vec3 uTerrainCorner; // world position of the chunk's (0, 0) sample at height 0
uniform float uTerrainSpacing;
uniform float uSkirtDrop;
#ifdef DEPTH_ONLY
uniform mat4 uLightSpaceMatrix;
#else
uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
#endif

out vec3 fWorldPos;
out vec3 fNormal;
out float fViewDepth;
out vec2 fGrid;
out float fBiome;

void main()
{
    fGrid = aGrid.xy;
    fBiome = aStream.w;
    vec3 world = uTerrainCorner + vec3(aGrid.x * uTerrainSpacing, aStream.x - aGrid.z * uSkirtDrop, aGrid.y * uTerrainSpacing);
    fWorldPos = world;
    fNormal = vec3(aStream.y, sqrt(max(1.0 - aStream.y * aStream.y - aStream.z * aStream.z, 0.0)), aStream.z);
#ifdef DEPTH_ONLY
    fViewDepth = 0.0;
    gl_Position = uLightSpaceMatrix * vec4(world, 1.0);
#else
    vec4 view = viewMatrix * vec4(world, 1.0);
    fViewDepth = -view.z;
    gl_Position = projectionMatrix * view;
#endif
}

#type fragment
#version 330 core

in vec3 fWorldPos;
in vec3 fNormal;
in float fViewDepth;
in vec2 fGrid;
in float fBiome;

#if defined(DEPTH_ONLY)

void main() {}

#elif defined(PICKING)

uniform float uEntityId;
out vec3 color;
void main() { color = vec3(uEntityId, 0.0, 0.0); }

#else

out vec4 color;
uniform float uSelected; // 1 selected, 2 hovered (SelectionHighlight)
uniform float uTerrainBase; // world height of the terrain's origin

#include "lighting.glsl"

// Materials
uniform int uSplatLayer;          // chunk's SplatAtlas layer, -1: no weights (height/slope fallback)
uniform float uChunkQuads;
uniform sampler2DArray uSplat0;   // weights of layers 0-3
uniform sampler2DArray uSplat1;   // weights of layers 4-7
uniform sampler2DArray uLayerAlbedo; // rgb albedo, a = height
uniform sampler2DArray uLayerNormal;
uniform sampler2DArray uLayerOrm;    // occlusion, roughness, metallic
uniform vec4 uLayerTilingA;
uniform vec4 uLayerTilingB;
uniform vec4 uLayerTriplanarA;
uniform vec4 uLayerTriplanarB;
uniform int uBiomeDebug;
uniform int uFarTile;       // REN-28 §15.4: 1 far-field tile with macro colour, 2 far tile without it yet, 0 detail
uniform sampler2D uMacro;   // macro colour map of the whole terrain
uniform vec3 uMacroArea;    // (min x, min z, size) in world metres
uniform vec3 uBiomeColor0;
uniform vec3 uBiomeColor1;
uniform vec3 uBiomeColor2;
uniform vec3 uBiomeColor3;
uniform vec3 uBiomeColor4;
uniform vec3 uBiomeColor5;
uniform vec3 uBiomeColor6;
uniform vec3 uBiomeColor7;

const float TRIPLANAR_SLOPE = 0.35;   // 1 - N.y above which triplanar layers project on X and Z
const float HEIGHT_BLEND_DEPTH = 0.2;
const float FAR_TILING_START = 30.0;  // metres
const float FAR_TILING_END = 120.0;

float layerParam(vec4 a, vec4 b, int i) { return i < 4 ? a[i] : b[i - 4]; }

vec3 biomeColor(int b)
{
    if (b == 0) return uBiomeColor0; if (b == 1) return uBiomeColor1; if (b == 2) return uBiomeColor2;
    if (b == 3) return uBiomeColor3; if (b == 4) return uBiomeColor4; if (b == 5) return uBiomeColor5;
    if (b == 6) return uBiomeColor6; return uBiomeColor7;
}

// One map of one layer at this fragment: planar (XZ) or triplanar, blended with an 8x coarser tiling far away
vec4 layerSample(sampler2DArray tex, int layer, vec3 n, float farMix)
{
    float tiling = layerParam(uLayerTilingA, uLayerTilingB, layer);
    bool tri = layerParam(uLayerTriplanarA, uLayerTriplanarB, layer) > 0.5 && (1.0 - n.y) > TRIPLANAR_SLOPE;
    vec3 p = fWorldPos / tiling;
    if (!tri) {
        vec4 near = texture(tex, vec3(p.xz, float(layer)));
        vec4 far = texture(tex, vec3(p.xz * 0.125, float(layer)));
        return mix(near, far, farMix);
    }
    vec3 w = pow(abs(n), vec3(4.0));
    w /= (w.x + w.y + w.z);
    return texture(tex, vec3(p.zy, float(layer))) * w.x + texture(tex, vec3(p.xz, float(layer))) * w.y + texture(tex, vec3(p.xy, float(layer))) * w.z;
}

// Placeholder by height and slope when the chunk has no splat weights
vec3 fallbackAlbedo(vec3 n, float y)
{
    vec3 albedo = mix(vec3(0.76, 0.70, 0.50), vec3(0.30, 0.45, 0.18), smoothstep(0.5, 2.0, y - uTerrainBase));
    return mix(albedo, vec3(0.42, 0.40, 0.38), smoothstep(0.75, 0.6, n.y));
}

void main()
{
    vec3 geomN = normalize(fNormal);
    vec3 V = normalize(uCameraPos - fWorldPos);
    vec3 albedo;
    vec3 N = geomN;
    float roughness = 0.9;
    float metallic = 0.0;
    float ao = 1.0;

    if (uFarTile == 1) {
        albedo = texture(uMacro, (fWorldPos.xz - uMacroArea.xy) / uMacroArea.z).rgb;
    } else if (uSplatLayer >= 0) {
        vec3 suv = vec3((fGrid + 0.5) / (uChunkQuads + 1.0), float(uSplatLayer));
        vec4 wa = texture(uSplat0, suv);
        vec4 wb = texture(uSplat1, suv);
        float all8[8] = float[8](wa.x, wa.y, wa.z, wa.w, wb.x, wb.y, wb.z, wb.w);
        // The 4 strongest layers (TerrainWeights.topFour)
        int ids[4];
        float ws[4];
        for (int k = 0; k < 4; ++k) {
            int best = 0;
            for (int i = 1; i < 8; ++i) if (all8[i] > all8[best]) best = i;
            ids[k] = best; ws[k] = all8[best]; all8[best] = -1.0;
        }
        float farMix = smoothstep(FAR_TILING_START, FAR_TILING_END, fViewDepth);
        vec4 alb[4];
        float heights[4];
        float top = -1e9;
        for (int k = 0; k < 4; ++k) {
            alb[k] = layerSample(uLayerAlbedo, ids[k], geomN, farMix);
            heights[k] = alb[k].a + ws[k];
            top = max(top, heights[k]);
        }
        // Height blend: stones poke through sand instead of a flat cross-fade
        float bw[4];
        float total = 0.0;
        for (int k = 0; k < 4; ++k) { bw[k] = ws[k] > 0.0 ? max(heights[k] - (top - HEIGHT_BLEND_DEPTH), 0.0) : 0.0; total += bw[k]; }
        albedo = vec3(0.0);
        vec3 nts = vec3(0.0);
        vec3 orm = vec3(0.0);
        for (int k = 0; k < 4; ++k) {
            float w = bw[k] / max(total, 1e-5);
            if (w <= 0.0) continue;
            albedo += alb[k].rgb * w;
            nts += (layerSample(uLayerNormal, ids[k], geomN, farMix).xyz * 2.0 - 1.0) * w;
            orm += layerSample(uLayerOrm, ids[k], geomN, farMix).xyz * w;
        }
        // Planar tangent frame: u along +X, v along +Z
        vec3 T = normalize(vec3(1.0, 0.0, 0.0) - geomN * geomN.x);
        vec3 B = cross(T, geomN);
        N = normalize(T * nts.x + B * nts.y + geomN * max(nts.z, 0.2));
        ao = orm.r;
        roughness = clamp(orm.g, 0.04, 1.0);
        metallic = orm.b;
    } else {
        albedo = fallbackAlbedo(geomN, fWorldPos.y);
    }
    if (uBiomeDebug == 1) albedo = mix(albedo, biomeColor(int(fBiome + 0.5)), 0.75);

    vec3 F0 = mix(vec3(0.04), albedo, metallic);
    vec3 lit = sunLighting(fWorldPos, fViewDepth, N, V, geomN, F0, albedo, roughness, metallic)
        + localLighting(fWorldPos, N, V, geomN, F0, albedo, roughness, metallic)
        + ambientLighting(N, V, F0, albedo, roughness, metallic, ao);
    vec3 mapped = lit / (lit + vec3(1.0));
    mapped = pow(mapped, vec3(1.0 / 2.2));
    if (uSelected > 0.5) mapped = mix(mapped, vec3(1.0, 0.6, 0.1), uSelected > 1.5 ? 0.12 : 0.25);
    color = vec4(applyFog(mapped, uCameraPos, fWorldPos), 1.0);
}

#endif
