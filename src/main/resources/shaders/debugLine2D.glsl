#type vertex
#version 330 core
layout (location=0) in vec3 aPos;
layout (location=1) in vec3 aColor;

out vec3 fColor;

uniform mat4 uView;
uniform mat4 uProjection;
uniform vec3 uCameraPos;
uniform float uPixelAngle; // radians per pixel at the view centre
uniform int uGround;       // 1: these lines lie on a horizontal floor (the editor grid)

// Pulls debug geometry toward the camera so lines lying on a surface (the grid on the floor, a collider on its mesh)
// win the depth test instead of z-fighting. Mirrors com.pafoid.skate.engine.render.renderer.GroundDepthPull:
// a fraction of the view depth for everything, plus, for ground lines, the floor's depth change across a few
// pixels, which grows with distance squared at grazing angles (REN-19).
const float BASE_PULL = 0.001;
const float GROUND_PULL_PIXELS = 2.5;

void main()
{
    fColor = aColor;
    vec4 viewPos = uView * vec4(aPos, 1.0);
    float depth = -viewPos.z;
    if (depth > 0.0) {
        float pull = depth * BASE_PULL;
        if (uGround == 1) {
            float h = max(abs(uCameraPos.y - aPos.y), 0.05);
            pull += GROUND_PULL_PIXELS * depth * depth * uPixelAngle / h;
        }
        viewPos.z += min(pull, depth * 0.5);
    }
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