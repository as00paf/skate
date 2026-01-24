#type vertex
#version 330 core
layout (location = 0) in vec3 aPos;

out vec3 fTexCoords;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;

void main()
{
    fTexCoords = aPos;
    // Remove translation from view matrix to keep skybox centered at camera
    mat4 staticView = mat4(mat3(viewMatrix)); 
    vec4 pos = projectionMatrix * staticView * vec4(aPos, 1.0);
    // Set z to w so that z/w = 1.0 (maximum depth)
    gl_Position = pos.xyww;
}

#type fragment
#version 330 core

in vec3 fTexCoords;
out vec4 color;

uniform samplerCube skybox;

void main()
{
    color = texture(skybox, fTexCoords);
}