// @skate-contract 1
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
// Local contrast below max(uFxaaEdgeThresholdMin, lumaMax * uFxaaEdgeThreshold) is not an edge (FxaaEdge.kt)
uniform float uFxaaEdgeThreshold;
uniform float uFxaaEdgeThresholdMin;

// Edge-aware FXAA (after Lottes' FXAA 3.11 console variant). Pixels that are not on a contrast edge are returned
// untouched, so textures and fine detail stay sharp; edge pixels are blended along the edge direction only.
const float REDUCE_MIN = 1.0 / 128.0;
const float REDUCE_MUL = 1.0 / 8.0;
const float SPAN_MAX = 8.0;

float luma(vec3 rgb) {
    return dot(rgb, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec2 uv = fTexCoords;
    vec2 t = uTexelSize;
    vec3 rgbM = texture(uTexture, uv).rgb;
    float lumaM = luma(rgbM);
    float lumaN = luma(texture(uTexture, uv + vec2(0.0, t.y)).rgb);
    float lumaS = luma(texture(uTexture, uv - vec2(0.0, t.y)).rgb);
    float lumaE = luma(texture(uTexture, uv + vec2(t.x, 0.0)).rgb);
    float lumaW = luma(texture(uTexture, uv - vec2(t.x, 0.0)).rgb);
    float lumaNW = luma(texture(uTexture, uv + vec2(-t.x, t.y)).rgb);
    float lumaNE = luma(texture(uTexture, uv + vec2(t.x, t.y)).rgb);
    float lumaSW = luma(texture(uTexture, uv + vec2(-t.x, -t.y)).rgb);
    float lumaSE = luma(texture(uTexture, uv + vec2(t.x, -t.y)).rgb);

    float lumaMin = min(lumaM, min(min(min(lumaN, lumaS), min(lumaE, lumaW)), min(min(lumaNW, lumaNE), min(lumaSW, lumaSE))));
    float lumaMax = max(lumaM, max(max(max(lumaN, lumaS), max(lumaE, lumaW)), max(max(lumaNW, lumaNE), max(lumaSW, lumaSE))));
    if (lumaMax - lumaMin < max(uFxaaEdgeThresholdMin, lumaMax * uFxaaEdgeThreshold)) {
        color = vec4(rgbM, 1.0);
        return;
    }

    vec2 dir = vec2(-((lumaNW + lumaNE) - (lumaSW + lumaSE)), (lumaNW + lumaSW) - (lumaNE + lumaSE));
    float dirReduce = max((lumaNW + lumaNE + lumaSW + lumaSE) * 0.25 * REDUCE_MUL, REDUCE_MIN);
    float rcpDirMin = 1.0 / (min(abs(dir.x), abs(dir.y)) + dirReduce);
    dir = clamp(dir * rcpDirMin, vec2(-SPAN_MAX), vec2(SPAN_MAX)) * t;

    vec3 rgbA = 0.5 * (texture(uTexture, uv + dir * (1.0 / 3.0 - 0.5)).rgb + texture(uTexture, uv + dir * (2.0 / 3.0 - 0.5)).rgb);
    vec3 rgbB = rgbA * 0.5 + 0.25 * (texture(uTexture, uv - dir * 0.5).rgb + texture(uTexture, uv + dir * 0.5).rgb);
    float lumaB = luma(rgbB);
    vec3 aa = (lumaB < lumaMin || lumaB > lumaMax) ? rgbA : rgbB;
    color = vec4(mix(rgbM, aa, uFxaaStrength), 1.0);
}
