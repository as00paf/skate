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
uniform float uGamma;

void main() {
    vec2 uv = vec2(fTexCoords.x, 1.0 - fTexCoords.y);
    vec4 texColor = texture(uTexture, uv);
    vec3 rgb = texColor.rgb;
    rgb = pow(max(rgb, vec3(0.0)), vec3(1.0 / uGamma));
    color = vec4(rgb, texColor.a);
}
