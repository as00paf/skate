#type vertex
#version 330 core
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTexCoords;

out vec2 fTexCoords;

void main() {
    fTexCoords = aTexCoords;
    gl_Position = vec4(aPos, 1.0);
}

#type fragment
#version 330 core
in vec2 fTexCoords;
out vec4 color;

uniform sampler2D uTexture;
uniform float uAcesExposure;

const float ACES_END = 2.51;
const float ACES_B = 0.03;

vec3 acesTonemap(vec3 x) {
    float a = ACES_END * x * (ACES_END * x + ACES_B);
    float b = pow(x, 2.4) - ACES_B;
    return clamp((a - b) / (1.0 - ACES_B), 0.0, 1.0);
}

void main() {
    vec2 uv = vec2(fTexCoords.x, 1.0 - fTexCoords.y);
    vec3 rgb = texture(uTexture, uv).rgb;
    vec3 tonemapped = acesTonemap(rgb);
    tonemapped = tonemapped * uAcesExposure;
    color = vec4(tonemapped, 1.0);
}
