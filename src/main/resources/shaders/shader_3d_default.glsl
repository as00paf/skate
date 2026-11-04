// ----------------------------------------------------------------------------
// PBR 3D Shader - Main Rendering Pipeline
// ----------------------------------------------------------------------------
// Handles:
// - Skeletal Skinning (up to 4 bone influences)
// - PBR (Metallic-Roughness) lighting model
// - Normal Mapping (TBN Space)
// - Distance-based Fog
// - HDR Tonemapping & Gamma Correction
// ----------------------------------------------------------------------------

#type vertex
#version 330 core
layout (location=0) in vec3 aPos;      // Local Space (Model coordinates)
layout (location=1) in vec2 aTexCoords;
layout (location=2) in vec3 aNormal;   // Local Space Normal
layout (location=3) in vec3 aTangent;  // Local Space Tangent
layout (location=4) in vec4 aColor;
layout (location=5) in vec2 aTexCoords1;
layout (location=6) in ivec4 aJoints;  // Bone IDs for skinning
layout (location=7) in vec4 aWeights; // Bone weights for skinning

out vec2 fTexCoords;
out vec2 fTexCoords1;
out vec3 fWorldPos;    // World Space Position (Used for lighting)
out vec3 fNormal;      // World Space Normal
out vec4 fColor;
out mat3 fTBN;         // Tangent-Bitangent-Normal matrix for Normal Mapping
out float fVisibility;
out float fViewDepth;  // Distance along the camera's forward axis, used to pick the shadow cascade

uniform mat4 transformationMatrix; // Model-to-World Matrix
uniform mat4 projectionMatrix;     // View-to-Clip Matrix
uniform mat4 viewMatrix;           // World-to-View (Camera) Matrix

uniform float uTextureScale;
uniform float uFogDensity;
uniform float uFogGradient;

const int MAX_BONES = 100;
layout(std140) uniform SkinBlock {
    mat4 u_JointMatrices[MAX_BONES];
};
uniform bool u_HasSkin;

void main()
{
    // --- Skeletal Skinning ---
    // Computes the weighted average of joint matrices in local space.
    mat4 skinMatrix = mat4(1.0);
    if (u_HasSkin) {
        skinMatrix = 
            aWeights.x * u_JointMatrices[aJoints.x] +
            aWeights.y * u_JointMatrices[aJoints.y] +
            aWeights.z * u_JointMatrices[aJoints.z] +
            aWeights.w * u_JointMatrices[aJoints.w];
    }

    // --- Coordinate Transformations ---
    // 1. World Space: Apply skinning then model-to-world transformation
    vec4 worldPos = transformationMatrix * skinMatrix * vec4(aPos, 1.0);
    fWorldPos = worldPos.xyz;

    // 2. View Space: Transform world coordinates relative to the camera
    vec4 posRelativeToCamera = viewMatrix * worldPos;
    fViewDepth = -posRelativeToCamera.z;

    // 3. Clip Space: Final transformation for rasterization
    gl_Position = projectionMatrix * posRelativeToCamera;
    
    fTexCoords = aTexCoords * uTextureScale;
    fTexCoords1 = aTexCoords1 * uTextureScale;
    fColor = aColor;

    // --- Normal Mapping (TBN Matrix) ---
    // Transform tangent and normal into world space to create the TBN basis.
    // This allows us to perform lighting in world space using normal map details.
    vec3 T = normalize(vec3(transformationMatrix * skinMatrix * vec4(aTangent, 0.0)));
    vec3 N = normalize(vec3(transformationMatrix * skinMatrix * vec4(aNormal, 0.0)));
    T = normalize(T - dot(T, N) * N); // Re-orthogonalize T with respect to N
    vec3 B = cross(N, T);
    fTBN = mat3(T, B, N);
    fNormal = N;

    // --- Fog Calculation ---
    // Distance-based visibility using the distance from the camera in view space.
    float distance = length(posRelativeToCamera.xyz);
    fVisibility = exp(-pow((distance * uFogDensity), uFogGradient));
    fVisibility = clamp(fVisibility, 0.0, 1.0);
}

#type fragment
#version 330 core

in vec2 fTexCoords;
in vec2 fTexCoords1;
in vec3 fWorldPos;
in vec3 fNormal;
in vec4 fColor;
in mat3 fTBN;
in float fVisibility;
in float fViewDepth;

// --- PBR Textures (glTF 2.0 Standard) ---
uniform sampler2D u_BaseColorTexture;
uniform sampler2D u_NormalMap;
uniform sampler2D u_MetallicRoughnessTexture; // Blue = Metallic, Green = Roughness
uniform sampler2D u_AOTexture;
uniform sampler2D u_EmissiveTexture;

// --- PBR Material Factors ---
uniform vec4 uBaseColor;
uniform float u_BaseColorFactor;
uniform float u_MetallicFactor;
uniform float u_RoughnessFactor;
uniform vec3 u_EmissiveFactor;
uniform int u_AlphaMode; // 0: OPAQUE, 1: MASK, 2: BLEND
uniform float u_AlphaCutoff;

// --- Scene Environmental Data ---
uniform vec3 uCameraPos;
uniform vec3 uSunDirection;
uniform vec3 uSunColor;
uniform vec3 uAmbientLight;
uniform vec3 uFogColor;

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

// --- Feature Toggles ---
uniform bool uHasBaseColorTexture;
uniform bool u_HasNormalMap;
uniform bool u_HasMetallicRoughnessTexture;
uniform bool u_HasAOTexture;
uniform bool u_HasEmissiveTexture;

uniform float uSelected; // SelectionHighlight: 0.0 = none, 1.0 = selected, 2.0 = hovered

out vec4 color;

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
float sunShadow(vec3 geomN, vec3 L)
{
    if (uCascadeCount <= 0) return 1.0;
    float far = uCascadeSplits[uCascadeCount - 1];
    if (fViewDepth > far) return 1.0;
    int c = 0;
    for (int i = 0; i < uCascadeCount - 1; ++i) {
        if (fViewDepth > uCascadeSplits[i]) c = i + 1;
    }
    vec3 p = fWorldPos + geomN * (uCascadeNormalOffset[c] * sinToLight(geomN, L));
    vec4 ls = uCascadeMatrices[c] * vec4(p, 1.0);
    vec3 uvz = ls.xyz / ls.w * 0.5 + 0.5;
    if (outsideMap(uvz)) return 1.0;
    uvz.z -= uCascadeDepthBias[c];
    float lit = pcf(uCascadeShadowMap, uvz, float(c), uShadowTexelSize);
    float fade = clamp((fViewDepth - uShadowFadeStart) / max(far - uShadowFadeStart, 0.001), 0.0, 1.0);
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
float localShadow(int lightIdx, vec3 lightPos, bool isPoint, vec3 geomN, vec3 L)
{
    for (int s = 0; s < uShadowedLightCount && s < MAX_SHADOWED_LIGHTS; ++s) {
        if (uShadowLightIndex[s] != lightIdx) continue;
        float dist = length(fWorldPos - lightPos);
        float offset = uShadowLightTexel[s] * dist * (1.0 + 0.5 * float(uPcfRadius)) * sinToLight(geomN, L);
        vec3 p = fWorldPos + geomN * offset;
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
vec3 CalcPointLight(PointLight light, vec3 N, vec3 V, vec3 F0, vec3 albedo, float roughness, float metallic)
{
    // 1. Light vector pointing from fragment to light position
    vec3 L = normalize(light.position - fWorldPos);
    vec3 H = normalize(V + L);

    // 2. Distance Attenuation
    float distance = length(light.position - fWorldPos);
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
vec3 CalcSpotLight(SpotLight light, vec3 N, vec3 V, vec3 F0, vec3 albedo, float roughness, float metallic)
{
    vec3 L = normalize(light.position - fWorldPos);
    vec3 H = normalize(V + L);

    // Distance Attenuation
    float distance = length(light.position - fWorldPos);
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

void main()
{
    // 1. Albedo & Alpha Setup
    vec4 baseColorSample = texture(u_BaseColorTexture, fTexCoords);
    vec4 albedo = uBaseColor * u_BaseColorFactor * fColor;
    if (uHasBaseColorTexture) {
        albedo *= baseColorSample;
    }

    float alpha = albedo.a;
    if (u_AlphaMode == 0) { // OPAQUE
        alpha = 1.0;
    } else if (u_AlphaMode == 1) { // MASK
        if (alpha < u_AlphaCutoff) discard;
        alpha = 1.0;
    }

    // 2. Normal Reconstruction (Tangent Space to World Space)
    vec3 N;
    if (u_HasNormalMap) {
        N = texture(u_NormalMap, fTexCoords).rgb;
        N = normalize(N * 2.0 - 1.0);
        N = normalize(fTBN * N);
    } else {
        N = normalize(fNormal);
    }

    vec3 V = normalize(uCameraPos - fWorldPos);
    vec3 geomN = normalize(fNormal); // geometric normal for shadow normal offset (normal maps would add noise)

    // 3. Material Properties
    float metallic = u_MetallicFactor;
    float roughness = u_RoughnessFactor;
    if (u_HasMetallicRoughnessTexture) {
        vec4 mrSample = texture(u_MetallicRoughnessTexture, fTexCoords);
        metallic *= mrSample.b;
        roughness *= mrSample.g;
    }

    float ao = 1.0;
    if (u_HasAOTexture) {
        ao = texture(u_AOTexture, fTexCoords).r;
    }

    vec3 emissive = u_EmissiveFactor;
    if (u_HasEmissiveTexture) {
        emissive *= texture(u_EmissiveTexture, fTexCoords).rgb;
    }

    // 4. Cook-Torrance BRDF calculation
    vec3 F0 = vec3(0.04); 
    F0 = mix(F0, albedo.rgb, metallic);

    vec3 Lo = vec3(0.0);
    
    // --- Sun Light Pass ---
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
        float lit = sunShadow(geomN, L);
        Lo += (kD * albedo.rgb / PI + specular) * radiance * NdotL * lit;
    }

    // --- Point Lights Pass ---
    // (and spot lights): only the lights selected for this object on the CPU
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
            float lit = localShadow(idx, p.position, true, geomN, normalize(p.position - fWorldPos));
            Lo += CalcPointLight(p, N, V, F0, albedo.rgb, roughness, metallic) * lit;
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
            float lit = localShadow(idx, sl.position, false, geomN, normalize(sl.position - fWorldPos));
            Lo += CalcSpotLight(sl, N, V, F0, albedo.rgb, roughness, metallic) * lit;
        }
    }

    // 5. Ambient & Final Composition
    vec3 ambient = uAmbientLight * albedo.rgb * ao;
    vec3 colorOut = ambient + Lo + emissive;

    // 6. HDR Tonemapping & Gamma Correction
    // Simple Reinhard tonemapping to bring HDR values into [0,1] range.
    colorOut = colorOut / (colorOut + vec3(1.0));

    // Linear to sRGB conversion.
    colorOut = pow(colorOut, vec3(1.0/2.2));

    vec4 finalColor = vec4(colorOut, alpha);

    // Selection highlight: green tint on colour only. Lowering alpha here made selected opaque objects see-through,
    // because the editor viewport composites the scene texture with its alpha.
    // Hover is a lighter tint of the same colour so it reads as "would select" without looking selected.
    if (uSelected > 1.5) {
        finalColor.rgb = mix(finalColor.rgb, vec3(0.0, 1.0, 0.0), 0.15);
    } else if (uSelected > 0.5) {
        finalColor.rgb = mix(finalColor.rgb, vec3(0.0, 1.0, 0.0), 0.4);
    }

    // 7. Atmospheric Fog
    color = mix(vec4(uFogColor, 1.0), finalColor, fVisibility);
}

    