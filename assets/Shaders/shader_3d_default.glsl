// ----------------------------------------------------------------------------
// PBR 3D Shader - Main Rendering Pipeline
// ----------------------------------------------------------------------------
// Handles:
// - Skeletal Skinning (up to 4 bone influences)
// - PBR (Metallic-Roughness) lighting model
// - Normal Mapping (TBN Space)
// - Lighting (shared lighting.glsl: sun, lights, shadows, IBL)
// - Fog (shared fog.glsl: linear/exp/exp2 + height fog)
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
out float fViewDepth;  // Distance along the camera's forward axis, used to pick the shadow cascade

uniform mat4 transformationMatrix; // Model-to-World Matrix
uniform mat4 projectionMatrix;     // View-to-Clip Matrix
uniform mat4 viewMatrix;           // World-to-View (Camera) Matrix

uniform float uTextureScale;

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
}

#type fragment
#version 330 core

in vec2 fTexCoords;
in vec2 fTexCoords1;
in vec3 fWorldPos;
in vec3 fNormal;
in vec4 fColor;
in mat3 fTBN;
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

// --- Scene lighting: sun, lights, shadows, IBL, fog (lighting.glsl includes fog.glsl) ---
#include "fog.glsl"
#include "lighting.glsl"

// --- Feature Toggles ---
uniform bool uHasBaseColorTexture;
uniform bool u_HasNormalMap;
uniform bool u_HasMetallicRoughnessTexture;
uniform bool u_HasAOTexture;
uniform bool u_HasEmissiveTexture;

uniform float uSelected; // SelectionHighlight: 0.0 = none, 1.0 = selected, 2.0 = hovered

out vec4 color;

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
    Lo += sunLighting(fWorldPos, fViewDepth, N, V, geomN, F0, albedo.rgb, roughness, metallic);

    // --- Point and Spot Lights Pass ---
    Lo += localLighting(fWorldPos, N, V, geomN, F0, albedo.rgb, roughness, metallic);

    // 5. Ambient & Final Composition
    vec3 ambient = ambientLighting(N, V, F0, albedo.rgb, roughness, metallic, ao);
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

    // 7. Atmospheric fog (fog.glsl), per fragment so large floor triangles fog correctly
    color = vec4(applyFog(finalColor.rgb, uCameraPos, fWorldPos), finalColor.a);
}

    