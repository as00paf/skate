// ----------------------------------------------------------------------------
// Shared scene lighting (REN-28 task 1): sun with cascaded shadows, point/spot lights from the LightBlock UBO with
// local shadow maps, image-based lighting (REN-27) and the Cook-Torrance BRDF.
// Include it in any lit fragment shader (meshes now, terrain/water/vegetation later). It includes fog.glsl, because
// the IBL reflection fades to the fog colour below the horizon. Positions are parameters, not varyings, so each
// shader passes its own world position and view depth.
// Uniforms are uploaded by LightingUniformsLoader, LightBuffer, ShadowPass and EnvironmentProbe.
// ----------------------------------------------------------------------------

#include "fog.glsl"

// --- Scene Environmental Data ---
uniform vec3 uCameraPos;
uniform vec3 uSunDirection;
uniform vec3 uSunColor;
uniform vec3 uAmbientLight;

// Image-based lighting from the sky (REN-27, EnvironmentProbe / IblMath). uEnvEnabled 0: plain ambient as before.
uniform int uEnvEnabled;
uniform vec4 uEnvSH[9];            // cosine-convolved SH: evaluating gives irradiance (rgb)
uniform float uEnvRotation;        // sky dome turn around Y (radians)
uniform float uEnvMaxLod;          // last prefiltered level (roughness 1)
uniform float uEnvReflection;      // specular strength
uniform float uEnvSkyLight;        // diffuse strength
uniform vec3 uEnvSkyScale;         // sky tint * exposure, for the full-resolution sky sample
uniform sampler2D uEnvPrefiltered; // GGX-prefiltered radiance, encoded (IblMath.encode), one roughness per mip
uniform sampler2D uEnvSky;         // the sky texture itself: mirror reflections at roughness 0
uniform sampler2D uEnvBrdfLut;     // split-sum (A, B) by (NdotV, roughness)

// --- Shadow Mapping Uniforms (see ShadowPass / LightingUniformsLoader) ---
#define MAX_CASCADES 4
#define MAX_LOCAL_SHADOW_LAYERS 18   // LocalShadows.MAX_LAYERS
#define MAX_SHADOWED_LIGHTS 8        // LocalShadows.MAX_SHADOWED_LIGHTS

uniform sampler2DArrayShadow uCascadeShadowMap; // one layer per cascade, hardware depth compare
uniform sampler2DArrayShadow uLocalShadowMap;   // spot: 1 layer, point: 6 cube-face layers
uniform int uCascadeCount;                      // 0 disables sun shadows
uniform mat4 uCascadeMatrices[MAX_CASCADES];
uniform vec4 uCascadeSplits;                    // far view depth of each cascade
uniform vec4 uCascadeNormalOffset;              // world normal offset at grazing angles
uniform vec4 uCascadeDepthBias;                 // receiver bias in [0,1] depth units
uniform float uShadowTexelSize;                 // 1 / cascade resolution
uniform float uShadowFadeStart;                 // view depth where sun shadows start fading out
uniform int uPcfRadius;                         // 0: 2x2 hardware, 1: 3x3, 2: 5x5 taps

uniform int uShadowedLightCount;
uniform int uShadowLightIndex[MAX_SHADOWED_LIGHTS]; // index into uLights
uniform int uShadowLightBase[MAX_SHADOWED_LIGHTS];  // first layer in uLocalShadowMap
uniform float uShadowLightTexel[MAX_SHADOWED_LIGHTS]; // texel world size per metre from the light
uniform mat4 uLocalShadowMatrices[MAX_LOCAL_SHADOW_LAYERS];
uniform float uLocalShadowTexelSize;

const float PI = 3.14159265359;

// --- PBR Math Functions (Cook-Torrance Microfacet Model) ---

// Shadow Mapping Functions (all return the LIT fraction: 1 = fully lit)

// Percentage-closer filtering: (2r+1)^2 hardware-compared taps, each itself a bilinear 2x2 PCF.
float pcf(sampler2DArrayShadow map, vec3 uvz, float layer, float texel)
{
    float sum = 0.0;
    int n = 0;
    for (int x = -uPcfRadius; x <= uPcfRadius; ++x) {
        for (int y = -uPcfRadius; y <= uPcfRadius; ++y) {
            sum += texture(map, vec4(uvz.xy + vec2(x, y) * texel, layer, uvz.z));
            n++;
        }
    }
    return sum / float(n);
}

bool outsideMap(vec3 uvz)
{
    return uvz.x < 0.0 || uvz.x > 1.0 || uvz.y < 0.0 || uvz.y > 1.0 || uvz.z > 1.0;
}

// Normal offset grows with the angle to the light: none when facing it, full at grazing angles.
float sinToLight(vec3 geomN, vec3 L)
{
    float c = clamp(dot(geomN, L), 0.0, 1.0);
    return sqrt(1.0 - c * c);
}

// Cascaded sun shadow. Receivers are pushed along the geometric normal (normal offset) plus a tiny depth bias;
// casters get slope-scaled polygon offset in the shadow pass, so no large constant bias (no peter-panning).
float sunShadow(vec3 worldPos, float viewDepth, vec3 geomN, vec3 L)
{
    if (uCascadeCount <= 0) return 1.0;
    float far = uCascadeSplits[uCascadeCount - 1];
    if (viewDepth > far) return 1.0;
    int c = 0;
    for (int i = 0; i < uCascadeCount - 1; ++i) {
        if (viewDepth > uCascadeSplits[i]) c = i + 1;
    }
    vec3 p = worldPos + geomN * (uCascadeNormalOffset[c] * sinToLight(geomN, L));
    vec4 ls = uCascadeMatrices[c] * vec4(p, 1.0);
    vec3 uvz = ls.xyz / ls.w * 0.5 + 0.5;
    if (outsideMap(uvz)) return 1.0;
    uvz.z -= uCascadeDepthBias[c];
    float lit = pcf(uCascadeShadowMap, uvz, float(c), uShadowTexelSize);
    float fade = clamp((viewDepth - uShadowFadeStart) / max(far - uShadowFadeStart, 0.001), 0.0, 1.0);
    return mix(lit, 1.0, fade);
}

// Mirrors LocalShadows.cubeFace: +X, -X, +Y, -Y, +Z, -Z.
int cubeFace(vec3 v)
{
    vec3 a = abs(v);
    if (a.x >= a.y && a.x >= a.z) return v.x > 0.0 ? 0 : 1;
    if (a.y >= a.z) return v.y > 0.0 ? 2 : 3;
    return v.z > 0.0 ? 4 : 5;
}

// Spot / point light shadow for light [lightIdx]; 1.0 when the light has no shadow map this frame.
float localShadow(vec3 worldPos, int lightIdx, vec3 lightPos, bool isPoint, vec3 geomN, vec3 L)
{
    for (int s = 0; s < uShadowedLightCount && s < MAX_SHADOWED_LIGHTS; ++s) {
        if (uShadowLightIndex[s] != lightIdx) continue;
        float dist = length(worldPos - lightPos);
        float offset = uShadowLightTexel[s] * dist * (1.0 + 0.5 * float(uPcfRadius)) * sinToLight(geomN, L);
        vec3 p = worldPos + geomN * offset;
        int layer = uShadowLightBase[s];
        if (isPoint) layer += cubeFace(p - lightPos);
        vec4 ls = uLocalShadowMatrices[layer] * vec4(p, 1.0);
        if (ls.w <= 0.0) return 1.0;
        vec3 uvz = ls.xyz / ls.w * 0.5 + 0.5;
        if (outsideMap(uvz)) return 1.0;
        return pcf(uLocalShadowMap, uvz, float(layer), uLocalShadowTexelSize);
    }
    return 1.0;
}

// Normal Distribution Function (NDF) - GGX/Trowbridge-Reitz
// Describes the alignment of microfacets.
float DistributionGGX(vec3 N, vec3 H, float roughness)
{
    float a = roughness*roughness;
    float a2 = a*a;
    float NdotH = max(dot(N, H), 0.0);
    float NdotH2 = NdotH*NdotH;

    float nom   = a2;
    float denom = (NdotH2 * (a2 - 1.0) + 1.0);
    denom = PI * denom * denom;

    return nom / denom;
}

// Geometry Function - Schlick-GGX
// Describes the self-shadowing of microfacets.
float GeometrySchlickGGX(float NdotV, float roughness)
{
    float r = (roughness + 1.0);
    float k = (r*r) / 8.0;

    float nom   = NdotV;
    float denom = NdotV * (1.0 - k) + k;

    return nom / denom;
}

// Smith's method for Geometry Shadowing
float GeometrySmith(vec3 N, vec3 V, vec3 L, float roughness)
{
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    float ggx2 = GeometrySchlickGGX(NdotV, roughness);
    float ggx1 = GeometrySchlickGGX(NdotL, roughness);

    return ggx2 * ggx1;
}

// Fresnel Equation - Schlick Approximation
// Describes the ratio of light reflected vs refracted.
// ---- Image-based lighting (mirrors IblMath) ----
const float IBL_MAX_RADIANCE = 16.0;

// Display value -> radiance the tonemap below maps back to it (IblMath.decode)
vec3 iblDecode(vec3 display)
{
    vec3 lin = min(pow(clamp(display, 0.0, 1.0), vec3(2.2)), vec3(IBL_MAX_RADIANCE / (1.0 + IBL_MAX_RADIANCE)));
    return lin / (1.0 - lin);
}

// World direction -> sky texture UV of the dome (IblMath.toSkyLocal + skyUv)
vec2 iblSkyUv(vec3 world)
{
    float c = cos(uEnvRotation);
    float s = sin(uEnvRotation);
    vec3 d = normalize(vec3(c * world.x - s * world.z, world.y, s * world.x + c * world.z));
    float u = atan(d.z, d.x) / (2.0 * PI);
    return vec2(u < 0.0 ? u + 1.0 : u, (asin(clamp(d.y, -1.0, 1.0)) + PI * 0.5) / PI);
}

// Irradiance from the SH coefficients (IblMath.shIrradiance), for a world normal
vec3 iblIrradiance(vec3 world)
{
    float c = cos(uEnvRotation);
    float s = sin(uEnvRotation);
    vec3 n = normalize(vec3(c * world.x - s * world.z, world.y, s * world.x + c * world.z));
    vec3 e = uEnvSH[0].rgb * 0.282095
        + uEnvSH[1].rgb * 0.488603 * n.y + uEnvSH[2].rgb * 0.488603 * n.z + uEnvSH[3].rgb * 0.488603 * n.x
        + uEnvSH[4].rgb * 1.092548 * n.x * n.y + uEnvSH[5].rgb * 1.092548 * n.y * n.z
        + uEnvSH[6].rgb * 0.315392 * (3.0 * n.z * n.z - 1.0) + uEnvSH[7].rgb * 1.092548 * n.x * n.z
        + uEnvSH[8].rgb * 0.546274 * (n.x * n.x - n.y * n.y);
    return max(e, vec3(0.0));
}

// Radiance reflected along R for a surface of the given roughness: the prefiltered level, sharpened toward the
// full-resolution sky (faded to fog below the horizon, like the dome) for near-mirror surfaces
vec3 iblSpecularRadiance(vec3 R, float roughness)
{
    vec2 uv = iblSkyUv(R);
    float lod = roughness * uEnvMaxLod;
    vec3 prefiltered = iblDecode(textureLod(uEnvPrefiltered, uv, lod).rgb);
    if (lod >= 1.0) return prefiltered;
    vec3 sky = textureLod(uEnvSky, uv, 0.0).rgb * uEnvSkyScale;
    sky = mix(uFogColor, sky, smoothstep(0.4, 0.55, uv.y));
    return mix(iblDecode(sky), prefiltered, lod);
}

vec3 fresnelSchlickRoughness(float cosTheta, vec3 F0, float roughness)
{
    return F0 + (max(vec3(1.0 - roughness), F0) - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}

vec3 fresnelSchlick(float cosTheta, vec3 F0)
{
    return F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}

struct PointLight {
    vec3 position;
    vec3 color;
    float constant;
    float linear;
    float quadratic;
};

struct SpotLight {
    vec3 position;
    vec3 direction;
    vec3 color;
    float cutOff;      // cos(innerAngle)
    float outerCutOff; // cos(outerAngle)
    float constant;
    float linear;
    float quadratic;
};

// Lights live in a shared uniform buffer (see LightPacking). Each draw lists the indices of the lights that reach it.
#define MAX_LIGHTS 64
#define MAX_LIGHTS_PER_OBJECT 16

struct Light {
    vec4 positionRange;  // xyz position, w range
    vec4 colorType;      // rgb colour * intensity, w: 0 point, 1 spot
    vec4 directionCos;   // xyz direction, w cos(inner cone)
    vec4 attenuation;    // x constant, y linear, z quadratic, w cos(outer cone)
};

layout(std140) uniform LightBlock {
    Light uLights[MAX_LIGHTS];
};

uniform int uLightCount;
uniform int uObjLightCount;
uniform int uObjLights[MAX_LIGHTS_PER_OBJECT];

// Calculates Cook-Torrance PBR contribution for a single Point Light
vec3 CalcPointLight(vec3 worldPos, PointLight light, vec3 N, vec3 V, vec3 F0, vec3 albedo, float roughness, float metallic)
{
    // 1. Light vector pointing from fragment to light position
    vec3 L = normalize(light.position - worldPos);
    vec3 H = normalize(V + L);

    // 2. Distance Attenuation
    float distance = length(light.position - worldPos);
    float attenuation = 1.0 / (light.constant + light.linear * distance + light.quadratic * (distance * distance));
    vec3 radiance = light.color * attenuation;

    // 3. Cook-Torrance BRDF
    float NDF = DistributionGGX(N, H, roughness);
    float G = GeometrySmith(N, V, L, roughness);
    vec3 F = fresnelSchlick(max(dot(H, V), 0.0), F0);

    vec3 nominator = NDF * G * F;
    float denominator = 4.0 * max(dot(N, V), 0.0) * max(dot(N, L), 0.0) + 0.001;
    vec3 specular = nominator / denominator;

    vec3 kS = F;
    vec3 kD = vec3(1.0) - kS;
    kD *= 1.0 - metallic;

    float NdotL = max(dot(N, L), 0.0);

    // Diffuse + Specular scaled by radiance & incidence angle
    return (kD * albedo / PI + specular) * radiance * NdotL;
}

// Calculates Cook-Torrance PBR contribution for a single Spot Light
vec3 CalcSpotLight(vec3 worldPos, SpotLight light, vec3 N, vec3 V, vec3 F0, vec3 albedo, float roughness, float metallic)
{
    vec3 L = normalize(light.position - worldPos);
    vec3 H = normalize(V + L);

    // Distance Attenuation
    float distance = length(light.position - worldPos);
    float attenuation = 1.0 / (light.constant + light.linear * distance + light.quadratic * (distance * distance));

    // Spot Light Cone Intensity (falloff between cutOff and outerCutOff)
    float theta = dot(L, normalize(-light.direction));
    float epsilon = light.cutOff - light.outerCutOff;
    float intensity = clamp((theta - light.outerCutOff) / epsilon, 0.0, 1.0);

    vec3 radiance = light.color * attenuation * intensity;

    // Cook-Torrance BRDF
    float NDF = DistributionGGX(N, H, roughness);
    float G = GeometrySmith(N, V, L, roughness);
    vec3 F = fresnelSchlick(max(dot(H, V), 0.0), F0);

    vec3 nominator = NDF * G * F;
    float denominator = 4.0 * max(dot(N, V), 0.0) * max(dot(N, L), 0.0) + 0.001;
    vec3 specular = nominator / denominator;

    vec3 kS = F;
    vec3 kD = vec3(1.0) - kS;
    kD *= 1.0 - metallic;

    float NdotL = max(dot(N, L), 0.0);

    return (kD * albedo / PI + specular) * radiance * NdotL;
}

// Sun (directional) light with cascaded shadows: Cook-Torrance, the lit fraction from sunShadow
vec3 sunLighting(vec3 worldPos, float viewDepth, vec3 N, vec3 V, vec3 geomN, vec3 F0, vec3 albedo, float roughness, float metallic)
{
    vec3 L = normalize(-uSunDirection);
    vec3 H = normalize(V + L);
    float radianceScale = 1.0;
    vec3 radiance = uSunColor * radianceScale;

    float NDF = DistributionGGX(N, H, roughness);
    float G   = GeometrySmith(N, V, L, roughness);
    vec3 F    = fresnelSchlick(max(dot(H, V), 0.0), F0);

    vec3 nominator    = NDF * G * F;
    float denominator = 4.0 * max(dot(N, V), 0.0) * max(dot(N, L), 0.0) + 0.001;
    vec3 specular = nominator / denominator;

    vec3 kS = F;
    vec3 kD = vec3(1.0) - kS;
    kD *= 1.0 - metallic;

    float NdotL = max(dot(N, L), 0.0);

    // Apply shadow factor to sun light
    float lit = sunShadow(worldPos, viewDepth, geomN, L);
    return (kD * albedo / PI + specular) * radiance * NdotL * lit;
}

// Point and spot lights selected for this object on the CPU (uObjLights), each with its shadow map if any
vec3 localLighting(vec3 worldPos, vec3 N, vec3 V, vec3 geomN, vec3 F0, vec3 albedo, float roughness, float metallic)
{
    vec3 Lo = vec3(0.0);
    for (int n = 0; n < uObjLightCount && n < MAX_LIGHTS_PER_OBJECT; ++n) {
        int idx = uObjLights[n];
        if (idx < 0 || idx >= uLightCount) continue;
        Light l = uLights[idx];
        if (l.colorType.w < 0.5) {
            PointLight p;
            p.position = l.positionRange.xyz;
            p.color = l.colorType.rgb;
            p.constant = l.attenuation.x;
            p.linear = l.attenuation.y;
            p.quadratic = l.attenuation.z;
            float lit = localShadow(worldPos, idx, p.position, true, geomN, normalize(p.position - worldPos));
            Lo += CalcPointLight(worldPos, p, N, V, F0, albedo, roughness, metallic) * lit;
        } else {
            SpotLight sl;
            sl.position = l.positionRange.xyz;
            sl.direction = l.directionCos.xyz;
            sl.color = l.colorType.rgb;
            sl.cutOff = l.directionCos.w;
            sl.outerCutOff = l.attenuation.w;
            sl.constant = l.attenuation.x;
            sl.linear = l.attenuation.y;
            sl.quadratic = l.attenuation.z;
            float lit = localShadow(worldPos, idx, sl.position, false, geomN, normalize(sl.position - worldPos));
            Lo += CalcSpotLight(worldPos, sl, N, V, F0, albedo, roughness, metallic) * lit;
        }
    }

    return Lo;
}

// Ambient term: flat ambient, or image-based lighting from the sky when uEnvEnabled == 1 (REN-27)
vec3 ambientLighting(vec3 N, vec3 V, vec3 F0, vec3 albedo, float roughness, float metallic, float ao)
{
    vec3 ambient = uAmbientLight * albedo * ao;
    if (uEnvEnabled == 1) {
        // Image-based lighting: sky light (diffuse) and reflections (split-sum specular), REN-27
        float NdotV = max(dot(N, V), 0.0);
        vec3 F = fresnelSchlickRoughness(NdotV, F0, roughness);
        vec3 kD = (vec3(1.0) - F) * (1.0 - metallic);
        vec3 diffuse = kD * albedo * (uAmbientLight + iblIrradiance(N) / PI * uEnvSkyLight);
        vec2 ab = texture(uEnvBrdfLut, vec2(NdotV, roughness)).rg;
        vec3 specular = iblSpecularRadiance(reflect(-V, N), roughness) * (F0 * ab.x + ab.y) * uEnvReflection;
        ambient = (diffuse + specular) * ao;
    }
    return ambient;
}
