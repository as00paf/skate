// @skate-contract 1
// ----------------------------------------------------------------------------
// Terrain chunks (REN-28 §4): a shared grid (sample column, row, skirt flag) plus a per-chunk stream (height, normal
// x, normal z). Lit with lighting.glsl like meshes. Variants: PICKING writes the entity id, DEPTH_ONLY is the shadow
// caster. Shading by height and slope until the splat materials (REN-28 task 5).
// ----------------------------------------------------------------------------

#type vertex
#version 330 core
layout (location=0) in vec3 aGrid;   // i, j, skirt (1 on skirt copies)
layout (location=1) in vec3 aStream; // height (m, terrain-local), normal x, normal z

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

void main()
{
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

// Placeholder material by height and slope until the splat layers land (REN-28 task 5)
vec3 terrainAlbedo(vec3 n, float y)
{
    vec3 sand = vec3(0.76, 0.70, 0.50);
    vec3 grass = vec3(0.30, 0.45, 0.18);
    vec3 rock = vec3(0.42, 0.40, 0.38);
    vec3 albedo = mix(sand, grass, smoothstep(0.5, 2.0, y - uTerrainBase));
    return mix(albedo, rock, smoothstep(0.75, 0.6, n.y));
}
void main()
{
    vec3 N = normalize(fNormal);
    vec3 V = normalize(uCameraPos - fWorldPos);
    vec3 albedo = terrainAlbedo(N, fWorldPos.y);
    float roughness = 0.9;
    vec3 F0 = vec3(0.04);
    vec3 lit = sunLighting(fWorldPos, fViewDepth, N, V, N, F0, albedo, roughness, 0.0)
        + localLighting(fWorldPos, N, V, N, F0, albedo, roughness, 0.0)
        + ambientLighting(N, V, F0, albedo, roughness, 0.0, 1.0);
    vec3 mapped = lit / (lit + vec3(1.0));
    mapped = pow(mapped, vec3(1.0 / 2.2));
    if (uSelected > 0.5) mapped = mix(mapped, vec3(1.0, 0.6, 0.1), uSelected > 1.5 ? 0.12 : 0.25);
    color = vec4(applyFog(mapped, uCameraPos, fWorldPos), 1.0);
}

#endif
