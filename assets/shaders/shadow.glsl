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
uniform mat4 u_JointMatrices[MAX_BONES];

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

uniform sampler2D uBaseColorTexture;
uniform int uAlphaMode;// 0: OPAQUE, 1: MASK, 2: BLEND
uniform float uAlphaCutoff;
uniform bool uHasBaseColorTexture;

void main()
{
    // Alpha masking: discard fragments below cutoff threshold
    if (uAlphaMode == 1 && uHasBaseColorTexture) { // MASK mode
        vec4 baseColor = texture(uBaseColorTexture, fTexCoords);
        if (baseColor.a < uAlphaCutoff) {
            discard;
        }
    }
    // OPAQUE and BLEND modes: render all fragments (depth-only)
}
