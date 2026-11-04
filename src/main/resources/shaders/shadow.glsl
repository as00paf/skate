#type vertex
#version 330 core
layout (location=0) in vec3 aPos;
layout (location=1) in vec2 aTexCoords;
layout (location=6) in ivec4 aJoints;
layout (location=7) in vec4 aWeights;

uniform mat4 uModelMatrix;
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

    mat4 worldMatrix = uModelMatrix * skinMatrix;
    gl_Position = uLightSpaceMatrix * worldMatrix * vec4(aPos, 1.0);

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
