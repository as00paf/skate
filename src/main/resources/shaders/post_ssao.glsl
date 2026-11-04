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
uniform sampler2D uDepthTexture;
uniform float uSsaORadius;
uniform float uSsaOIntensity;

void main() {
    vec2 uv = vec2(fTexCoords.x, 1.0 - fTexCoords.y);
    float currentDepth = texture(uDepthTexture, uv).r;
    float occlusionSum = 0.0;
    int samples = 8;
    for (int i = 0; i < samples; i++) {
        float angle = float(i) * 0.3926990817;
        float offset = uSsaORadius;
        vec2 sampleUv = uv + vec2(cos(angle) * offset, sin(angle) * offset);
        if (sampleUv.x > 0.0 && sampleUv.x < 1.0 && sampleUv.y > 0.0 && sampleUv.y < 1.0) {
            float sampleDepth = texture(uDepthTexture, clamp(sampleUv, vec2(0.0), vec2(1.0))).r;
            if (sampleDepth < currentDepth) {
                occlusionSum = occlusionSum + 1.0;
            }
        }
    }
    float occlusion = occlusionSum / 8.0;
    float aoFactor = 1.0 - uSsaOIntensity * occlusion;
    vec4 texColor = texture(uTexture, uv);
    color = vec4(texColor.rgb * aoFactor, 1.0);
}
