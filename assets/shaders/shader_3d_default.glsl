#type vertex
#version 330 core
layout (location=0) in vec3 aPos;
layout (location=1) in vec2 aTexCoords;
layout (location=2) in vec3 aNormal;
layout (location=3) in vec3 aTangent;
layout (location=4) in vec4 aColor;
layout (location=5) in vec2 aTexCoords1;
layout (location=6) in ivec4 aJoints;
layout (location=7) in vec4 aWeights;

out vec2 fTexCoords;
out vec2 fTexCoords1;
out vec3 fWorldPos;
out vec3 fNormal;
out vec4 fColor;
out mat3 fTBN;
out float fVisibility;

uniform mat4 transformationMatrix;
uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;

uniform float uTextureScale;
uniform float uFogDensity;
uniform float uFogGradient;

const int MAX_BONES = 100;
uniform mat4 u_JointMatrices[MAX_BONES];
uniform bool u_HasSkin;

void main()
{
    mat4 skinMatrix = mat4(1.0);
    if (u_HasSkin) {
        skinMatrix = 
            aWeights.x * u_JointMatrices[aJoints.x] +
            aWeights.y * u_JointMatrices[aJoints.y] +
            aWeights.z * u_JointMatrices[aJoints.z] +
            aWeights.w * u_JointMatrices[aJoints.w];
    }

    vec4 worldPos = transformationMatrix * skinMatrix * vec4(aPos, 1.0);
    fWorldPos = worldPos.xyz;
    
    vec4 posRelativeToCamera = viewMatrix * worldPos;
    gl_Position = projectionMatrix * posRelativeToCamera;
    
    fTexCoords = aTexCoords * uTextureScale;
    fTexCoords1 = aTexCoords1 * uTextureScale;
    fColor = aColor;

    // Normal mapping setup
    vec3 T = normalize(vec3(transformationMatrix * skinMatrix * vec4(aTangent, 0.0)));
    vec3 N = normalize(vec3(transformationMatrix * skinMatrix * vec4(aNormal, 0.0)));
    T = normalize(T - dot(T, N) * N);
    vec3 B = cross(N, T);
    fTBN = mat3(T, B, N);
    fNormal = N;

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

// PBR Textures
uniform sampler2D u_BaseColorTexture;
uniform sampler2D u_NormalMap;
uniform sampler2D u_MetallicRoughnessTexture;
uniform sampler2D u_AOTexture;
uniform sampler2D u_EmissiveTexture;

// PBR Factors
uniform vec4 u_BaseColorFactor;
uniform float u_MetallicFactor;
uniform float u_RoughnessFactor;
uniform vec3 u_EmissiveFactor;
uniform int u_AlphaMode; // 0: OPAQUE, 1: MASK, 2: BLEND
uniform float u_AlphaCutoff;

// Scene Lighting
uniform vec3 uCameraPos;
uniform vec3 uSunDirection;
uniform vec3 uSunColor;
uniform vec3 uMoonDirection;
uniform vec3 uMoonColor;
uniform vec3 uAmbientLight;
uniform vec3 uFogColor;

// Flags
uniform bool u_HasNormalMap;
uniform bool u_HasMetallicRoughnessTexture;
uniform bool u_HasAOTexture;
uniform bool u_HasEmissiveTexture;

out vec4 color;

const float PI = 3.14159265359;

// ----------------------------------------------------------------------------
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
// ----------------------------------------------------------------------------
float GeometrySchlickGGX(float NdotV, float roughness)
{
    float r = (roughness + 1.0);
    float k = (r*r) / 8.0;

    float nom   = NdotV;
    float denom = NdotV * (1.0 - k) + k;

    return nom / denom;
}
// ----------------------------------------------------------------------------
float GeometrySmith(vec3 N, vec3 V, vec3 L, float roughness)
{
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    float ggx2 = GeometrySchlickGGX(NdotV, roughness);
    float ggx1 = GeometrySchlickGGX(NdotL, roughness);

    return ggx2 * ggx1;
}
// ----------------------------------------------------------------------------
vec3 fresnelSchlick(float cosTheta, vec3 F0)
{
    return F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}
// ----------------------------------------------------------------------------

void main()
{
    vec4 baseColorSample = texture(u_BaseColorTexture, fTexCoords);
    vec4 albedo = baseColorSample * u_BaseColorFactor * fColor;

    float alpha = albedo.a;
    if (u_AlphaMode == 0) { // OPAQUE
        alpha = 1.0;
    } else if (u_AlphaMode == 1) { // MASK
        if (alpha < u_AlphaCutoff) discard;
        alpha = 1.0;
    }
    // BLEND (2) just uses alpha as is

    vec3 N;
    if (u_HasNormalMap) {
        N = texture(u_NormalMap, fTexCoords).rgb;
        N = normalize(N * 2.0 - 1.0);
        N = normalize(fTBN * N);
    } else {
        N = normalize(fNormal);
    }

    vec3 V = normalize(uCameraPos - fWorldPos);

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

    vec3 F0 = vec3(0.04); 
    F0 = mix(F0, albedo.rgb, metallic);

    vec3 Lo = vec3(0.0);
    
    // Sun light
    {
        vec3 L = normalize(-uSunDirection);
        vec3 H = normalize(V + L);
        float distance = 1.0; // Directional
        float attenuation = 1.0;
        vec3 radiance = uSunColor * attenuation;

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

    // Moon light
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

    vec3 ambient = uAmbientLight * albedo.rgb * ao;
    vec3 colorOut = ambient + Lo + emissive;

    // HDR tonemapping (Simple Reinard)
    colorOut = colorOut / (colorOut + vec3(1.0));
    // Gamma correction
    colorOut = pow(colorOut, vec3(1.0/2.2)); 

    vec4 finalColor = vec4(colorOut, alpha);
    color = mix(vec4(uFogColor, 1.0), finalColor, fVisibility);
}