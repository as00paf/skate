#type vertex
#version 330 core
layout (location=0) in vec3 aPos;
layout (location=1) in vec2 aTexCoords;
layout (location=2) in vec3 aNormal;

uniform mat4 transformationMatrix;
uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;

void main()
{
    gl_Position = projectionMatrix * viewMatrix * transformationMatrix * vec4(aPos, 1.0);
}

#type fragment
#version 330 core

uniform float uEntityId;

out vec3 color;

void main()
{
    color = vec3(uEntityId, uEntityId, uEntityId);
}
