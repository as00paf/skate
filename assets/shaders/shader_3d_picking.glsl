#type vertex
#version 330 core
layout (location=0) in vec3 aPos;
layout (location=1) in vec2 aTexCoords;
layout (location=6) in ivec4 aJoints;
layout (location=7) in vec4 aWeights;
layout (location=10) in float aEntityId;

uniform mat4 transformationMatrix;
uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform float uEntityId;
uniform bool uUseBatchId;
uniform float uTextureScale;

const int MAX_BONES = 100;
uniform mat4 u_JointMatrices[MAX_BONES];
uniform bool u_HasSkin;

flat out float fEntityId;
out vec2 fTexCoords;

void main()
{
    if (uUseBatchId) {
        fEntityId = aEntityId;
    } else {
        fEntityId = uEntityId;
    }

    mat4 skinMatrix = mat4(1.0);
    if (u_HasSkin) {
        skinMatrix =
            aWeights.x * u_JointMatrices[aJoints.x] +
            aWeights.y * u_JointMatrices[aJoints.y] +
            aWeights.z * u_JointMatrices[aJoints.z] +
            aWeights.w * u_JointMatrices[aJoints.w];
    }

    // Apply skinning then model-to-world transformation
    vec4 worldPos = transformationMatrix * skinMatrix * vec4(aPos, 1.0);

    // Transform to clip space
    gl_Position = projectionMatrix * viewMatrix * worldPos;

    float texScale = uTextureScale > 0.0 ? uTextureScale : 1.0;
    fTexCoords = aTexCoords * texScale;
}

#type fragment
#version 330 core

flat in float fEntityId;
in vec2 fTexCoords;

uniform sampler2D u_BaseColorTexture;
uniform int u_AlphaMode;// 0: OPAQUE, 1: MASK, 2: BLEND
uniform float u_AlphaCutoff;
uniform bool u_HasBaseColorTexture;

out vec3 color;

void main()
{
    if (u_AlphaMode > 0 && u_HasBaseColorTexture) {
        vec4 texColor = texture(u_BaseColorTexture, fTexCoords);
        if (texColor.a < u_AlphaCutoff) {
            discard;
        }
    }

    color = vec3(fEntityId, 0.0, 0.0);
}
