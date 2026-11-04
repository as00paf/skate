#type vertex
#version 330 core
// Per-vertex: unit quad corner in [-0.5, 0.5]
layout (location = 0) in vec2 aCorner;
// Per-instance (divisor 1)
layout (location = 1) in vec4 aPosSize;   // xyz = world position, w = size
layout (location = 2) in vec4 aColor;
layout (location = 3) in vec4 aUvRect;    // u0, v0 (top edge), width, height

uniform mat4 uProjection;
uniform mat4 uView;

out vec4 fColor;
out vec2 fTexCoords;
out vec2 fLocal;

void main()
{
    // Camera right/up are the first two rows of the view rotation, so quads always face the camera
    vec3 right = vec3(uView[0][0], uView[1][0], uView[2][0]);
    vec3 up = vec3(uView[0][1], uView[1][1], uView[2][1]);
    vec3 world = aPosSize.xyz + (right * aCorner.x + up * aCorner.y) * aPosSize.w;

    fColor = aColor;
    fLocal = aCorner;
    fTexCoords = vec2(aUvRect.x + (aCorner.x + 0.5) * aUvRect.z, aUvRect.y + (0.5 - aCorner.y) * aUvRect.w);
    gl_Position = uProjection * uView * vec4(world, 1.0);
}

#type fragment
#version 330 core

in vec4 fColor;
in vec2 fTexCoords;
in vec2 fLocal;

uniform sampler2D uTexture;
uniform int uHasTexture;

out vec4 color;

void main()
{
    if (uHasTexture == 1) {
        color = fColor * texture(uTexture, fTexCoords);
    } else {
        // No texture: soft round dot
        float d = length(fLocal) * 2.0;
        color = vec4(fColor.rgb, fColor.a * (1.0 - smoothstep(0.4, 1.0, d)));
    }
    if (color.a < 0.004) discard;
}
