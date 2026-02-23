#type vertex
#version 330 core

layout (location=0) in vec3 aPos;
layout (location=6) in ivec4 aJoints;
layout (location=7) in vec4 aWeights;

uniform mat4 uModelMatrix;
uniform mat4 uLightSpaceMatrix;
uniform bool uHasSkin;

const int MAX_BONES = 100;
uniform mat4 u_JointMatrices[MAX_BONES];

void main()
{
    mat4 skinMatrix = mat4(1.0);
    if (uHasSkin) {
        skinMatrix =
        aWeights.x * u_JointMatrices[aJoints.x] +
        aWeights.y * u_JointMatrices[aJoints.y] +
        aWeights.z * u_JointMatrices[aJoints.z] +
        aWeights.w * u_JointMatrices[aJoints.w];
    }

    mat4 worldMatrix = uModelMatrix * skinMatrix;
    gl_Position = uLightSpaceMatrix * worldMatrix * vec4(aPos, 1.0);
}

#type fragment
#version 330 core

void main()
{
    // Depth-only rendering - no color output needed
    // Fragment shader is empty, depth is written automatically
}
