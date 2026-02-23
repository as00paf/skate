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
out vec3 fFragPosLightSpace;// Position in light space for shadow mapping (xyz)
out float fFragPosLightSpaceW;// w component for perspective divide

uniform mat4 transformationMatrix; // Model-to-World Matrix
uniform mat4 projectionMatrix;     // View-to-Clip Matrix
uniform mat4 viewMatrix;           // World-to-View (Camera) Matrix

uniform float uTextureScale;
uniform float uFogDensity;
uniform float uFogGradient;

// --- Shadow Mapping Uniforms ---
uniform mat4 uLightSpaceMatrix;// Light's view-projection matrix

const int MAX_BONES = 100;
uniform mat4 u_JointMatrices[MAX_BONES];
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

    // 2. Light Space: Transform to light's view space for shadow mapping
    vec4 fragPosLightSpace = uLightSpaceMatrix * worldPos;
    fFragPosLightSpace = fragPosLightSpace.xyz;
    fFragPosLightSpaceW = fragPosLightSpace.w;

    // 3. View Space: Transform world coordinates relative to the camera
    vec4 posRelativeToCamera = viewMatrix * worldPos;

    // 4. Clip Space: Final transformation for rasterization
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
in vec3 fFragPosLightSpace;
in float fFragPosLightSpaceW;

// --- PBR Textures (glTF 2.0 Standard) ---
uniform sampler2D u_BaseColorTexture;
uniform sampler2D u_NormalMap;
uniform sampler2D u_MetallicRoughnessTexture; // Blue = Metallic, Green = Roughness
uniform sampler2D u_AOTexture;
uniform sampler2D u_EmissiveTexture;

// --- PBR Material Factors ---
uniform vec4 u_BaseColorFactor;
uniform float u_MetallicFactor;
uniform float u_RoughnessFactor;
uniform vec3 u_EmissiveFactor;
uniform int u_AlphaMode; // 0: OPAQUE, 1: MASK, 2: BLEND
uniform float u_AlphaCutoff;

// --- Scene Environmental Data ---
uniform vec3 uCameraPos;
uniform vec3 uSunDirection;
uniform vec3 uSunColor;
uniform vec3 uMoonDirection;
uniform vec3 uMoonColor;
uniform vec3 uAmbientLight;
uniform vec3 uFogColor;

// --- Shadow Mapping ---
uniform sampler2D uShadowMap;// Shadow map depth texture

// --- Feature Toggles ---
uniform bool u_HasNormalMap;
uniform bool u_HasMetallicRoughnessTexture;
uniform bool u_HasAOTexture;
uniform bool u_HasEmissiveTexture;

uniform float uSelected; // 0.0 = None, 1.0 = Selected

out vec4 color;

const float PI = 3.14159265359;

// --- PBR Math Functions (Cook-Torrance Microfacet Model) ---

// Shadow Mapping Functions

// Calculate shadow factor using depth comparison
float calculateShadow(vec3 fragPosLightSpace, float fragPosLightSpaceW)
{
    // Perform perspective divide to get NDC coordinates
    vec3 projCoords = fragPosLightSpace / fragPosLightSpaceW;

    // Transform from [-1,1] to [0,1] range
    projCoords = projCoords * 0.5 + 0.5;

    // Get depth of current fragment from light's perspective
    float currentDepth = projCoords.z;

    // Sample closest depth from shadow map
    float closestDepth = texture(uShadowMap, projCoords.xy).r;

    // Simple depth comparison with bias
    float bias = 0.005;
    float shadow = currentDepth - bias > closestDepth ? 1.0 : 0.0;

    return shadow;
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

void main()
{
    // 1. Albedo & Alpha Setup
    vec4 baseColorSample = texture(u_BaseColorTexture, fTexCoords);
    vec4 albedo = baseColorSample * u_BaseColorFactor * fColor;

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
        float shadow = calculateShadow(fFragPosLightSpace, fFragPosLightSpaceW);
        Lo += (kD * albedo.rgb / PI + specular) * radiance * NdotL * (1.0 - shadow);
    }

    // --- Moon Light Pass ---
    {
        vec3 L = normalize(-uMoonDirection);
        vec3 H = normalize(V + L);
        vec3 radiance = uMoonColor;

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
        Lo += (kD * albedo.rgb / PI + specular) * radiance * NdotL;
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

        

        // Selection Highlight (Transparent Green Silhouette)

        if (uSelected > 0.5) {

            // Mix with green (R=0, G=1, B=0, A=0.5)

            // If we want it to look like a "hologram" or silhouette, we can override the color mostly.

            finalColor = mix(finalColor, vec4(0.0, 1.0, 0.0, 0.5), 0.6);

        }

        

        // 7. Atmospheric Fog

        color = mix(vec4(uFogColor, 1.0), finalColor, fVisibility);

    }

    