#type vertex
#version 330 core
layout (location = 0) in vec3 aPos; // Local space cube coordinates

out vec3 fTexCoords; // 3D texture coordinates for the cubemap

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;

void main()
{
    // For a skybox, the texture coordinates are simply the local positions of the cube vertices.
    fTexCoords = aPos;
    
    // --- Translation Removal ---
    // We strip the translation part of the view matrix (last column) to keep the 
    // skybox centered at the camera's origin, regardless of the camera's world position.
    mat4 staticView = mat4(mat3(viewMatrix)); 
    
    vec4 pos = projectionMatrix * staticView * vec4(aPos, 1.0);
    
    // --- The xyww trick ---
    // Forces the skybox to the far plane (depth = 1.0).
    gl_Position = pos.xyww;
}

#type fragment
#version 330 core

in vec3 fTexCoords;
out vec4 color;

uniform samplerCube skybox; // 6-sided cubemap sampler

void main()
{
    // Sample the cubemap using the 3D direction vector.
    color = texture(skybox, fTexCoords);
}