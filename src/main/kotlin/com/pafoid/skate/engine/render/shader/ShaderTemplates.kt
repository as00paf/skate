package com.pafoid.skate.engine.render.shader

/**
 * Starting points for new project shaders (AST-26 §5.6): the Asset Browser's "New Shader" writes [PBR_MATERIAL] to
 * `Assets/Shaders/<name>.glsl`. It declares what the colour pass uploads (matrices, the PBR material uniforms) and
 * lights through `lighting.glsl` and `fog.glsl`, so it renders like the default mesh shader until you change it. It
 * stays inside the GLSL subset the Vulkan backend supports (docs/shaders.md).
 */
object ShaderTemplates {
    val PBR_MATERIAL: String = """
        |// A custom material shader. Assign it in a material's "Shader" field. See docs/shaders.md.
        |#type vertex
        |#version 330 core
        |layout (location=0) in vec3 aPos;
        |layout (location=1) in vec2 aTexCoords;
        |layout (location=2) in vec3 aNormal;
        |layout (location=4) in vec4 aColor;
        |
        |out vec2 fTexCoords;
        |out vec3 fWorldPos;
        |out vec3 fNormal;
        |out vec4 fColor;
        |out float fViewDepth;
        |
        |uniform mat4 transformationMatrix;
        |uniform mat4 projectionMatrix;
        |uniform mat4 viewMatrix;
        |uniform float uTextureScale;
        |
        |void main()
        |{
        |    vec4 worldPos = transformationMatrix * vec4(aPos, 1.0);
        |    vec4 viewPos = viewMatrix * worldPos;
        |    fWorldPos = worldPos.xyz;
        |    fViewDepth = -viewPos.z;
        |    fNormal = normalize(mat3(transformationMatrix) * aNormal);
        |    fTexCoords = aTexCoords * uTextureScale;
        |    fColor = aColor;
        |    gl_Position = projectionMatrix * viewPos;
        |}
        |
        |#type fragment
        |#version 330 core
        |in vec2 fTexCoords;
        |in vec3 fWorldPos;
        |in vec3 fNormal;
        |in vec4 fColor;
        |in float fViewDepth;
        |
        |// Material (PBR schema)
        |uniform sampler2D u_BaseColorTexture;
        |uniform sampler2D u_NormalMap;
        |uniform sampler2D u_MetallicRoughnessTexture;
        |uniform sampler2D u_AOTexture;
        |uniform sampler2D u_EmissiveTexture;
        |uniform vec4 uBaseColor;
        |uniform float u_BaseColorFactor;
        |uniform float u_MetallicFactor;
        |uniform float u_RoughnessFactor;
        |uniform vec3 u_EmissiveFactor;
        |uniform int u_AlphaMode;
        |uniform float u_AlphaCutoff;
        |uniform bool uHasBaseColorTexture;
        |uniform bool u_HasNormalMap;
        |uniform bool u_HasMetallicRoughnessTexture;
        |uniform bool u_HasAOTexture;
        |uniform bool u_HasEmissiveTexture;
        |uniform float uSelected;
        |
        |// Sun, lights, shadows, image-based lighting and fog: the same as the default mesh shader
        |#include "lighting.glsl"
        |
        |out vec4 color;
        |
        |void main()
        |{
        |    vec4 albedo = uBaseColor * u_BaseColorFactor * fColor;
        |    if (uHasBaseColorTexture) albedo *= texture(u_BaseColorTexture, fTexCoords);
        |    if (u_AlphaMode == 1 && albedo.a < u_AlphaCutoff) discard;
        |
        |    vec3 N = normalize(fNormal);
        |    vec3 V = normalize(uCameraPos - fWorldPos);
        |    float metallic = u_MetallicFactor;
        |    float roughness = u_RoughnessFactor;
        |    vec3 F0 = mix(vec3(0.04), albedo.rgb, metallic);
        |
        |    vec3 lit = sunLighting(fWorldPos, fViewDepth, N, V, N, F0, albedo.rgb, roughness, metallic)
        |        + localLighting(fWorldPos, N, V, N, F0, albedo.rgb, roughness, metallic)
        |        + ambientLighting(N, V, F0, albedo.rgb, roughness, metallic, 1.0)
        |        + u_EmissiveFactor;
        |
        |    lit = lit / (lit + vec3(1.0));          // Reinhard, like the default shader
        |    lit = pow(lit, vec3(1.0 / 2.2));
        |    if (uSelected > 0.5) lit = mix(lit, vec3(0.0, 1.0, 0.0), uSelected > 1.5 ? 0.15 : 0.4);
        |    color = vec4(applyFog(lit, uCameraPos, fWorldPos), u_AlphaMode == 2 ? albedo.a : 1.0);
        |}
        |""".trimMargin()
}
