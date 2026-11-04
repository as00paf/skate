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
uniform vec2 uTexelSize;
uniform float uFxaaStrength;

void main() {
    vec2 uv = fTexCoords;
    vec4 center = texture(uTexture, uv);
    vec2 texel = uTexelSize;
    vec4 sampleA = texture(uTexture, uv + vec2(-texel.x, texel.y));
    vec4 sampleB = texture(uTexture, uv + vec2(texel.x, texel.y));
    vec4 sampleC = texture(uTexture, uv + vec2(texel.x, -texel.y));
    vec4 sampleD = texture(uTexture, uv + vec2(-texel.x, -texel.y));
    vec4 blended = (sampleA + sampleB + sampleC + sampleD) * 0.25;
    color = vec4(mix(center.rgb, blended.rgb, uFxaaStrength), 1.0);
}
