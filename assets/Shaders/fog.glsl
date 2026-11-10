// Shared fog (REN-09), included by every shader that draws scene colour: models and floors (shader_3d_default),
// the sky dome and particles, so all of them fog alike. Evaluated per fragment from world positions.
// Mirrors com.pafoid.skate.engine.render.fog.FogMath; keep the two in sync (FogMathTest covers the math).
//
// Distance part by uFogMode: 0 = off, 1 = linear (uFogStart..uFogEnd), 2 = exp, 3 = exp2 (uFogDensity).
// Height part (uFogHeightFalloff > 0): density e^(-falloff * (y - uFogHeight)), averaged along the camera ray
// analytically and capped at 1, so fog pools below uFogHeight and thins above it.

uniform int uFogMode;
uniform vec3 uFogColor;
uniform float uFogDensity;
uniform float uFogStart;
uniform float uFogEnd;
uniform float uFogHeight;
uniform float uFogHeightFalloff;

float fogDistanceAmount(float d) {
    if (uFogMode == 1) return clamp((d - uFogStart) / max(uFogEnd - uFogStart, 1e-4), 0.0, 1.0);
    if (uFogMode == 2) return 1.0 - exp(-uFogDensity * d);
    if (uFogMode == 3) { float x = uFogDensity * d; return 1.0 - exp(-x * x); }
    return 0.0;
}

float fogHeightWeight(vec3 cameraPos, vec3 worldPos) {
    if (uFogHeightFalloff <= 0.0) return 1.0;
    float k = uFogHeightFalloff;
    float t = k * (worldPos.y - cameraPos.y);
    float ray = abs(t) < 1e-4 ? 1.0 : (1.0 - exp(-t)) / t;
    return clamp(exp(-k * (cameraPos.y - uFogHeight)) * ray, 0.0, 1.0);
}

float fogAmount(vec3 cameraPos, vec3 worldPos) {
    if (uFogMode == 0) return 0.0;
    return clamp(fogDistanceAmount(distance(cameraPos, worldPos)) * fogHeightWeight(cameraPos, worldPos), 0.0, 1.0);
}

vec3 applyFog(vec3 color, vec3 cameraPos, vec3 worldPos) {
    return mix(color, uFogColor, fogAmount(cameraPos, worldPos));
}
