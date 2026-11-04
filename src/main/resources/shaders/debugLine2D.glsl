#type vertex
#version 330 core
layout (location=0) in vec3 aPos;
layout (location=1) in vec3 aColor;

out vec3 fColor;

uniform mat4 uView;
uniform mat4 uProjection;

// Pulls debug geometry toward the camera by a fraction of its view depth so lines lying on a surface (the grid on a
// floor at y = 0, a collider on its mesh) win the depth test instead of z-fighting. Proportional to distance, so it
// stays above depth-buffer precision far away while hardly moving lines in front of nearby geometry.
const float DEPTH_PULL = 0.001;

void main()
{
    fColor = aColor;
    vec4 viewPos = uView * vec4(aPos, 1.0);
    viewPos.z *= 1.0 - DEPTH_PULL;
    gl_Position = uProjection * viewPos;
}

#type fragment
#version 330 core

in vec3 fColor;
out vec4 color;

void main()
{
    color = vec4(fColor, 1);
}