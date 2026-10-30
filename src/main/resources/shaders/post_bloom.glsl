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
uniform float uBloomIntensity;
uniform float uBloomThreshold;

void main() {
    vec2 uv = vec2(fTexCoords.x, 1.0 - fTexCoords.y);
    vec3 hdr = texture(uTexture, uv).rgb;
    vec3 glow = hdr - vec3(uBloomThreshold);
    glow = max(glow, vec3(0.0));
    vec3 bloom = glow * glow;
    vec3 finalColor = hdr + bloom * uBloomIntensity;
    color = vec4(finalColor, 1.0);
}
