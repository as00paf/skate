#type vertex
#version 400 core
layout (location=0) in vec3 aPos;
layout (location=1) in vec2 aTexCoords;
layout (location=2) in vec3 aNormal;

out vec2 fTexCoords;
out vec3 fSurfaceNormal;
out vec3 fToLightVector;

uniform mat4 transformationMatrix;
uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform vec3 lightPosition;

void main()
{
    vec4 worldPosition = transformationMatrix * vec4(aPos, 1.0);
    gl_Position = projectionMatrix * viewMatrix * worldPosition;
    fTexCoords = aTexCoords;

    fSurfaceNormal = (transformationMatrix * vec4(aNormal, 0.0)).xyz;
    fToLightVector = lightPosition - worldPosition.xyz;
}

#type fragment
#version 400 core

in vec2 fTexCoords;
in vec3 fSurfaceNormal;
in vec3 fToLightVector;

uniform sampler2D textureSampler;
uniform vec3 lightColor;

out vec4 color;

void main()
{
    vec3 unitNormal = normalize(fSurfaceNormal);
    vec3 unitLightVector = normalize(fToLightVector);

    float nDot1 = dot(unitNormal, unitLightVector);
    float brightness = max(nDot1, 0.0);
    vec3 diffuse = brightness * lightColor;

    color = vec4(diffuse, 1.0) * texture(textureSampler, fTexCoords);
}