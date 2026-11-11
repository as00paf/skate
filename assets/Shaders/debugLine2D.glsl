#type vertex
#version 330 core
layout (location=0) in vec3 aPos;
layout (location=1) in vec3 aColor;

out vec3 fColor;
out vec3 fViewPos;
out float fWorldY;

uniform mat4 uView;
uniform mat4 uProjection;

void main()
{
    fColor = aColor;
    vec4 viewPos = uView * vec4(aPos, 1.0);
    fViewPos = viewPos.xyz;
    fWorldY = aPos.y;
    // Vertices are never moved: the depth pull happens per fragment (below), so lines stay exactly where they are
    // on screen (REN-19: pulling the vertex z shifted far axis ends far above the floor).
    gl_Position = uProjection * viewPos;
}

#type fragment
#version 330 core

in vec3 fColor;
in vec3 fViewPos;
in float fWorldY;
out vec4 color;

uniform mat4 uProjection;
uniform vec3 uCameraPos;
uniform float uPixelAngle; // radians per pixel at the view centre
uniform int uGround;       // 1: this geometry lies on a horizontal floor (the editor grid and its X/Z axes)

// Depth pull toward the camera so debug geometry lying on a surface (the grid on the floor, a collider on its mesh)
// wins the depth test instead of z-fighting. Mirrors com.pafoid.skate.engine.render.renderer.GroundDepthPull:
// a fraction of the view depth for everything, plus, for ground geometry, the floor's depth change across a few
// pixels, which grows with distance squared at grazing angles. Evaluated per fragment from its own view depth and
// written to gl_FragDepth, so it never moves anything on screen and is exact along long segments.
const float BASE_PULL = 0.001;
const float GROUND_PULL_PIXELS = 2.5;

void main()
{
    color = vec4(fColor, 1);
    float depth = -fViewPos.z;
    if (depth <= 0.0) { gl_FragDepth = gl_FragCoord.z; return; }
    float pull = depth * BASE_PULL;
    if (uGround == 1) {
        float h = max(abs(uCameraPos.y - fWorldY), 0.05);
        pull += GROUND_PULL_PIXELS * depth * depth * uPixelAngle / h;
    }
    pull = min(pull, depth * 0.5);
    vec4 clip = uProjection * vec4(fViewPos.xy, fViewPos.z + pull, 1.0);
    gl_FragDepth = clamp((clip.z / clip.w) * 0.5 + 0.5, 0.0, 1.0);
}
