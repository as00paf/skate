#type vertex
#version 330 core
layout (location = 0) in vec2 aPos;

out vec2 fUv;

// Full-screen quad over the capture target: texel (x, y) reads the sky at ((x + 0.5) / w, (y + 0.5) / h)
void main() {
    fUv = aPos * 0.5 + 0.5;
    gl_Position = vec4(aPos, 0.0, 1.0);
}

#type fragment
#version 330 core
in vec2 fUv;
out vec4 color;

uniform sampler2D uTexture;
uniform float uLod; // mip level matching the capture size, so each texel averages the sky texels it covers

// Copies the sky texture into the small capture target that EnvironmentProbe reads back and bakes (REN-27)
void main() {
    color = vec4(textureLod(uTexture, fUv, uLod).rgb, 1.0);
}
