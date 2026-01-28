#type vertex
#version 330 core
layout (location=0) in vec3 aPos;
layout (location=6) in ivec4 aJoints;
layout (location=7) in vec4 aWeights;

uniform mat4 transformationMatrix;
uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;

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
    gl_Position = projectionMatrix * viewMatrix * transformationMatrix * skinMatrix * vec4(aPos, 1.0);
}

#type fragment
#version 330 core

uniform float uEntityId;

out vec3 color;

void main()
{
    color = vec3(uEntityId, 0.0, 0.0);
}
