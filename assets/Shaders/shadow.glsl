// @skate-contract 1
#type vertex
#version 330 core
layout (location=0) in vec3 aPos;
layout (location=1) in vec2 aTexCoords;
layout (location=6) in ivec4 aJoints;
layout (location=7) in vec4 aWeights;
#ifdef WIND
layout (location=4) in vec4 aColor;
#include "wind.glsl"
#endif

uniform mat4 uModelMatrix;
#ifdef INSTANCED
// REN-29 §4.1: world matrix rows from the instance stream (VertexLayouts.INSTANCE_TRANSFORM), plus params
layout (location=10) in vec4 aInstanceRow0;
layout (location=11) in vec4 aInstanceRow1;
layout (location=12) in vec4 aInstanceRow2;
layout (location=13) in vec4 aInstanceParams; // tint, random, wind phase, LOD fade
#define MODEL_MATRIX transpose(mat4(aInstanceRow0, aInstanceRow1, aInstanceRow2, vec4(0.0, 0.0, 0.0, 1.0)))
#else
#define MODEL_MATRIX uModelMatrix
#endif
uniform mat4 uLightSpaceMatrix;
uniform bool u_HasSkin;

const int MAX_BONES = 100;
layout(std140) uniform SkinBlock {
    mat4 u_JointMatrices[MAX_BONES];
};

out vec2 fTexCoords;

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

    mat4 worldMatrix = MODEL_MATRIX * skinMatrix;
    vec4 worldPos = worldMatrix * vec4(aPos, 1.0);
#ifdef WIND
    worldPos.xyz += windDisplacement(worldPos.xyz, MODEL_MATRIX[3].xyz, aPos.y, aColor);
#endif
    gl_Position = uLightSpaceMatrix * worldPos;

    fTexCoords = aTexCoords;
}

#type fragment
#version 330 core

in vec2 fTexCoords;

uniform sampler2D u_BaseColorTexture;
uniform int u_AlphaMode;// 0: OPAQUE, 1: MASK, 2: BLEND
uniform float u_AlphaCutoff;
uniform bool uHasBaseColorTexture;

void main()
{
#ifdef ALPHA_TEST
    // MASK parts only (ShadowRenderer draws them with this variant): discard texels below the cutoff
    if (u_AlphaMode == 1 && uHasBaseColorTexture) {
        vec4 baseColor = texture(u_BaseColorTexture, fTexCoords);
        if (baseColor.a < u_AlphaCutoff) {
            discard;
        }
    }
#endif
    // Default variant: no discard, so the GPU keeps its fast depth-only path (REN-22)
}
