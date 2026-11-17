// @skate-contract 1
// ----------------------------------------------------------------------------
// Water surface (REN-29 §6): Gerstner waves on a chunked grid, refraction of the scene copy, Beer-Lambert absorption,
// IBL + screen-space reflections (task 8), shore and crest foam, sun and local light speculars, fog.
// The PICKING variant writes the entity id instead (picking pass).
// Uniforms: frame lighting from SceneFrame (lighting.glsl), waves and surface from WaterRenderer, material params
// from the "water" schema (WaterSchema).
// ----------------------------------------------------------------------------

#type vertex
#version 330 core
layout (location=0) in vec3 aPos; // water-local XZ; y = 0 on the surface, -1 on skirt vertices

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 uWaterModel;  // translation + yaw of the water entity
uniform float uWaveTime;   // EnvironmentClock time
uniform float uSkirtDepth; // how far skirts hang below the surface

#include "water_waves.glsl"

out vec3 fWorldPos;
out vec3 fWaveNormal;
out float fJacobian;
out float fViewDepth;
out vec2 fRestXZ;

void main()
{
    vec4 rest = uWaterModel * vec4(aPos.x, 0.0, aPos.z, 1.0);
    vec3 n;
    float j;
    vec3 world = rest.xyz + gerstner(rest.xz, uWaveTime, n, j);
    world.y += aPos.y * uSkirtDepth;
    fWorldPos = world;
    fWaveNormal = n;
    fJacobian = j;
    fRestXZ = rest.xz;
    vec4 view = viewMatrix * vec4(world, 1.0);
    fViewDepth = -view.z;
    gl_Position = projectionMatrix * view;
}

#type fragment
#version 330 core

in vec3 fWorldPos;
in vec3 fWaveNormal;
in float fJacobian;
in float fViewDepth;
in vec2 fRestXZ;

#ifdef PICKING

uniform float uEntityId;
out vec3 color;
void main() { color = vec3(uEntityId, 0.0, 0.0); }

#else

out vec4 color;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform float uWaveTime;

// Scene copy (colour + depth) taken before the water draws
uniform sampler2D uSceneColor;
uniform sampler2D uSceneDepth;
uniform vec2 uScreenSize;
uniform vec2 uNearFar;
uniform int uSsrSteps;        // 0: IBL reflections only (quality Low)
uniform float uWaterDepth;    // absorption fallback where nothing is below
uniform vec2 uWaterFlow;      // m/s, world XZ
uniform int uReceiveShadows;

// "water" material schema
uniform vec3 uShallowColor;
uniform vec3 uDeepColor;
uniform float uAbsorptionDistance;
uniform float uWaterRoughness;
uniform float uRefractionStrength;
uniform vec2 uNormalScale;
uniform vec2 uNormalTiling;
uniform float uFoamDistance;
uniform vec3 uFoamColor;
uniform bool uSsrEnabled;
uniform sampler2D normalA;
uniform sampler2D normalB;
uniform sampler2D foam;
uniform bool uHasNormalA;
uniform bool uHasNormalB;
uniform bool uHasFoam;

#include "lighting.glsl"
#include "wind.glsl"

float linearDepth(float d)
{
    float z = d * 2.0 - 1.0;
    float n = uNearFar.x, f = uNearFar.y;
    return 2.0 * n * f / (f + n - z * (f - n));
}

// Slope of a procedural ripple field (no texture): sum of 4 travelling sines
vec2 rippleSlope(vec2 p)
{
    vec2 s = vec2(0.0);
    const vec2 D0 = vec2(0.80, 0.60), D1 = vec2(-0.45, 0.89), D2 = vec2(0.96, -0.28), D3 = vec2(-0.70, -0.71);
    s += D0 * cos(dot(p, D0) * 6.1 + 1.3) * 0.50;
    s += D1 * cos(dot(p, D1) * 9.7 + 4.1) * 0.30;
    s += D2 * cos(dot(p, D2) * 15.3 + 2.2) * 0.15;
    s += D3 * cos(dot(p, D3) * 23.9 + 0.7) * 0.08;
    return s;
}

// Detail slope from the two scrolling normal maps (or the procedural ripples), drifting with flow and wind
vec2 detailSlope(vec2 p, float t)
{
    vec2 drift = uWaterFlow + uWindDir * uWindSpeed * 0.03;
    vec2 pa = p * uNormalTiling.x + drift * t * uNormalTiling.x;
    vec2 pb = p * uNormalTiling.y - vec2(drift.y, -drift.x) * t * uNormalTiling.y * 0.7;
    vec2 a = uHasNormalA ? texture(normalA, pa).xy * 2.0 - 1.0 : rippleSlope(pa * 6.0) * 0.5;
    vec2 b = uHasNormalB ? texture(normalB, pb).xy * 2.0 - 1.0 : rippleSlope(pb * 6.0 + 17.0) * 0.5;
    return a * uNormalScale.x + b * uNormalScale.y;
}

float foamPattern(vec2 p, float t)
{
    vec2 q = p * 0.35 + uWaterFlow * t * 0.35;
    if (uHasFoam) return texture(foam, q).r;
    return clamp(0.5 + 0.5 * sin(q.x * 13.0 + sin(q.y * 7.0)) * sin(q.y * 11.0 + sin(q.x * 5.0 + t)), 0.0, 1.0);
}

vec2 projectToUv(vec3 viewPos)
{
    vec4 clip = projectionMatrix * vec4(viewPos, 1.0);
    return clip.xy / clip.w * 0.5 + 0.5;
}

// Screen-space reflection (task 8): view-space march against the scene depth copy, binary refine; rgb = radiance,
// a = confidence (0 on a miss, faded at screen edges and for rays toward the camera)
vec4 screenSpaceReflection(vec3 worldPos, vec3 R)
{
    if (!uSsrEnabled || uSsrSteps <= 0) return vec4(0.0);
    vec3 origin = (viewMatrix * vec4(worldPos, 1.0)).xyz;
    vec3 dir = normalize(mat3(viewMatrix) * R);
    if (dir.z > 0.6) return vec4(0.0); // towards the camera: nothing on screen to reflect
    float maxDistance = 40.0;
    float stepLength = maxDistance / float(uSsrSteps);
    float t = stepLength * 0.5;
    float prevT = 0.0;
    for (int i = 0; i < 64; ++i) {
        if (i >= uSsrSteps) break;
        vec3 p = origin + dir * t;
        if (p.z > -uNearFar.x) break;
        vec2 uv = projectToUv(p);
        if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) break;
        float sceneZ = linearDepth(texture(uSceneDepth, uv).r);
        float rayZ = -p.z;
        if (rayZ > sceneZ && rayZ - sceneZ < 0.5 + t * 0.05) {
            float lo = prevT, hi = t;
            for (int r = 0; r < 5; ++r) {
                float mid = 0.5 * (lo + hi);
                vec3 m = origin + dir * mid;
                if (-m.z > linearDepth(texture(uSceneDepth, projectToUv(m)).r)) hi = mid; else lo = mid;
            }
            vec2 hitUv = projectToUv(origin + dir * hi);
            vec2 edge = smoothstep(vec2(0.0), vec2(0.08), hitUv) * (1.0 - smoothstep(vec2(0.92), vec2(1.0), hitUv));
            float fade = edge.x * edge.y * (1.0 - smoothstep(0.2, 0.6, dir.z)) * (1.0 - hi / maxDistance);
            return vec4(iblDecode(texture(uSceneColor, hitUv).rgb), clamp(fade, 0.0, 1.0));
        }
        prevT = t;
        t += stepLength;
    }
    return vec4(0.0);
}

void main()
{
    vec3 V = normalize(uCameraPos - fWorldPos);
    vec3 geomN = normalize(fWaveNormal);
    vec2 slope = detailSlope(fRestXZ, uWaveTime);
    vec3 N = normalize(geomN + vec3(-slope.x, 0.0, -slope.y));
    if (dot(N, V) < 0.0) N = geomN; // grazing detail must not flip the surface away

    // Scene behind the water
    vec2 uv = gl_FragCoord.xy / uScreenSize;
    float waterZ = fViewDepth;
    float rawDepth = texture(uSceneDepth, uv).r;
    float thickness = rawDepth >= 1.0 ? uWaterDepth : max(linearDepth(rawDepth) - waterZ, 0.0);
    vec2 refractUv = clamp(uv + N.xz * uRefractionStrength * clamp(thickness, 0.0, 1.0), vec2(0.001), vec2(0.999));
    float refractDepth = texture(uSceneDepth, refractUv).r;
    if (refractDepth < 1.0 && linearDepth(refractDepth) < waterZ) refractUv = uv; // object above the surface: no halo
    else if (refractDepth < 1.0) thickness = max(linearDepth(refractDepth) - waterZ, 0.0);
    vec3 refracted = iblDecode(texture(uSceneColor, refractUv).rgb);

    // Light reaching the water body
    vec3 L = normalize(-uSunDirection);
    float sunLit = uReceiveShadows == 1 ? sunShadow(fWorldPos, fViewDepth, geomN, L) : 1.0;
    vec3 bodyLight = uAmbientLight + uSunColor * max(L.y, 0.0) * sunLit;
    if (uEnvEnabled == 1) bodyLight += iblIrradiance(vec3(0.0, 1.0, 0.0)) / PI * uEnvSkyLight;

    // Beer-Lambert absorption towards the deep colour
    float absorbed = 1.0 - exp(-thickness / max(uAbsorptionDistance, 0.01));
    vec3 body = mix(refracted * uShallowColor, uDeepColor * bodyLight, absorbed);

    // Reflections: IBL always, SSR where the scene has the reflected point
    vec3 R = reflect(-V, N);
    R.y = abs(R.y);
    vec3 envReflection = uEnvEnabled == 1 ? iblSpecularRadiance(R, uWaterRoughness) * uEnvReflection : uFogColor * 0.8;
    vec4 ssr = screenSpaceReflection(fWorldPos, R);
    vec3 reflection = mix(envReflection, ssr.rgb, ssr.a);

    float NdotV = max(dot(N, V), 0.0);
    vec3 F0 = vec3(0.02);
    float F = fresnelSchlick(NdotV, F0).r;
    vec3 surface = mix(body, reflection, F);

    // Sun and local light speculars (GGX)
    vec3 H = normalize(V + L);
    float rough = max(uWaterRoughness, 0.02);
    vec3 sunSpec = DistributionGGX(N, H, rough) * GeometrySmith(N, V, L, rough) * fresnelSchlick(max(dot(H, V), 0.0), F0)
        / (4.0 * NdotV * max(dot(N, L), 0.0) + 0.001) * uSunColor * max(dot(N, L), 0.0) * sunLit;
    surface += sunSpec + localLighting(fWorldPos, N, V, geomN, F0, vec3(0.0), rough, 0.0);

    // Foam: shoreline / intersections and steep crests, masked by a scrolling pattern
    float shore = 1.0 - smoothstep(0.0, max(uFoamDistance, 0.001), thickness);
    float crest = 1.0 - smoothstep(0.2, 0.6, fJacobian);
    float foamAmount = clamp((shore + crest) * foamPattern(fRestXZ, uWaveTime) * 1.6, 0.0, 1.0);
    surface = mix(surface, uFoamColor * bodyLight, foamAmount);

    // Same output transform as the mesh shader: Reinhard, gamma, then fog
    vec3 mapped = surface / (surface + vec3(1.0));
    mapped = pow(mapped, vec3(1.0 / 2.2));
    color = vec4(applyFog(mapped, uCameraPos, fWorldPos), 1.0);
}

#endif
