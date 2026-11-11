#type vertex
#version 330 core
layout (location = 0) in vec2 aPos;

out vec3 fNear;
out vec3 fFar;

uniform mat4 uInvViewProj;

vec3 unproject(vec2 ndc, float z) {
    vec4 p = uInvViewProj * vec4(ndc, z, 1.0);
    return p.xyz / p.w;
}

// Full-screen quad; each pixel's view ray (near to far plane) is intersected with the grid plane in the fragment shader
void main() {
    fNear = unproject(aPos, -1.0);
    fFar = unproject(aPos, 1.0);
    gl_Position = vec4(aPos, 0.0, 1.0);
}

#type fragment
#version 330 core
in vec3 fNear;
in vec3 fFar;
out vec4 color;

uniform mat4 uView;
uniform mat4 uProjection;
uniform vec3 uCameraPos;
uniform float uPixelAngle;
uniform float uGridY;
uniform float uMinorStep;
uniform float uLodRatio;
uniform float uMinPixels;
uniform vec3 uMinorColor;
uniform vec3 uMajorColor;
uniform float uMinorOpacity;
uniform float uMajorOpacity;
uniform float uMinorWidth;
uniform float uMajorWidth;
uniform float uAxisWidth;
uniform int uShowAxes;
uniform float uFadeStart;
uniform float uFadeEnd;

// Infinite editor grid (REN-25). Mirrors com.pafoid.skate.engine.render.renderer.GridMath: per-pixel LOD from the
// world size of a pixel, lines measured in pixels with derivatives (constant width at any angle, anti-aliased),
// distance fade. Depth pull as debugLine2D.glsl / GroundDepthPull so the grid wins against the floor it lies on.
const float BASE_PULL = 0.001;
const float GROUND_PULL_PIXELS = 2.5;
const vec3 X_AXIS_COLOR = vec3(0.9, 0.2, 0.2);
const vec3 Z_AXIS_COLOR = vec3(0.2, 0.35, 0.95);

float coverage(float distancePx, float widthPx) {
    return clamp(widthPx * 0.5 + 0.5 - distancePx, 0.0, 1.0);
}

// Coverage of the lines every `step` units, both directions, given the world units per pixel `d` along x and z
float gridCoverage(vec2 p, float step, vec2 d, float widthPx) {
    vec2 c = p / step;
    vec2 toLine = abs(fract(c - 0.5) - 0.5) * step / max(d, vec2(1e-6));
    return max(coverage(toLine.x, widthPx), coverage(toLine.y, widthPx));
}

void main() {
    float dy = fFar.y - fNear.y;
    float t = abs(dy) > 1e-6 ? (uGridY - fNear.y) / dy : -1.0;
    vec3 world = fNear + t * (fFar - fNear);
    vec2 p = world.xz;
    // Derivatives first, in uniform control flow
    vec2 d = fwidth(p);
    if (t <= 0.0) discard;

    float unitsPerPixel = max(d.x, d.y);
    float level = max(0.0, log(max(unitsPerPixel * uMinPixels / uMinorStep, 1e-6)) / log(uLodRatio) + 1.0);
    float fade = fract(level);
    float minor = uMinorStep * pow(uLodRatio, floor(level));
    float major = minor * uLodRatio;
    float nextMajor = major * uLodRatio;

    float a0 = gridCoverage(p, minor, d, uMinorWidth) * (1.0 - fade);
    float a1 = gridCoverage(p, major, d, mix(uMajorWidth, uMinorWidth, fade));
    float a2 = gridCoverage(p, nextMajor, d, uMajorWidth);

    vec3 rgb;
    float alpha;
    if (a2 > 0.0) {
        rgb = uMajorColor; alpha = a2 * uMajorOpacity;
    } else if (a1 > 0.0) {
        rgb = mix(uMajorColor, uMinorColor, fade); alpha = a1 * mix(uMajorOpacity, uMinorOpacity, fade);
    } else {
        rgb = uMinorColor; alpha = a0 * uMinorOpacity;
    }

    if (uShowAxes == 1) {
        // Red X axis (z = 0) and blue Z axis (x = 0), measured in pixels like the lines
        float xAxis = coverage(abs(p.y) / max(d.y, 1e-6), uAxisWidth);
        float zAxis = coverage(abs(p.x) / max(d.x, 1e-6), uAxisWidth);
        rgb = mix(rgb, X_AXIS_COLOR, xAxis); alpha = max(alpha, xAxis);
        rgb = mix(rgb, Z_AXIS_COLOR, zAxis); alpha = max(alpha, zAxis);
    }

    alpha *= 1.0 - smoothstep(uFadeStart, uFadeEnd, length(world.xz - uCameraPos.xz));
    if (alpha < 1.0 / 255.0) discard;

    vec4 viewPos = uView * vec4(world, 1.0);
    float depth = -viewPos.z;
    float h = max(abs(uCameraPos.y - uGridY), 0.05);
    float pull = min(depth * BASE_PULL + GROUND_PULL_PIXELS * depth * depth * uPixelAngle / h, depth * 0.5);
    vec4 clip = uProjection * vec4(viewPos.xy, viewPos.z + pull, 1.0);
    float ndcZ = clip.z / clip.w;
    if (ndcZ > 1.0) discard;
    gl_FragDepth = clamp(ndcZ * 0.5 + 0.5, 0.0, 1.0);
    color = vec4(rgb, alpha);
}
