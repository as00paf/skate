// @skate-contract 1
// Shared wind (REN-29 §5), included by vertex stages that sway (WIND variants of the mesh, shadow and picking shaders,
// so shadows and clicks match) and by water.glsl (detail normals drift with the wind).
// Mirrors com.pafoid.skate.engine.environment.WindField; keep the two in sync (WindFieldTest pins a table).
//
// uWindDir: unit XZ direction the wind blows towards. uWindGust: (strength, scale in m). uWindTime: animation clock.
// Per draw: uWindProfile 0 = none, 1 = grass, 2 = tree; uWindBend = (stiffness 0..1, flutter 0..1);
// uWindHeight = (root Y, height) of the model in local space, for the tree bend.

uniform vec2 uWindDir;
uniform float uWindSpeed;
uniform vec2 uWindGust;
uniform float uWindTurbulence;
uniform float uWindTime;
uniform int uWindProfile;
uniform vec2 uWindBend;
uniform vec2 uWindHeight;

const float WIND_TAU = 6.28318530718;

float windGust(vec2 p, float t) {
    if (uWindGust.y <= 0.0) return 0.0;
    float along = dot(p, uWindDir) / uWindGust.y - t * uWindSpeed / uWindGust.y;
    float across = (-p.x * uWindDir.y + p.y * uWindDir.x) / uWindGust.y;
    float g = 0.5 * sin(WIND_TAU * along) + 0.3 * sin(WIND_TAU * 2.3 * along + 1.7) + 0.2 * sin(WIND_TAU * 0.37 * along + 4.1);
    return g * (0.75 + 0.25 * sin(WIND_TAU * 0.37 * across + 0.6));
}

// Wind velocity (m/s) at world XZ p and time t.
vec3 windSample(vec2 p, float t) {
    if (uWindSpeed == 0.0) return vec3(0.0);
    float s = uWindSpeed * (1.0 + uWindGust.x * windGust(p, t));
    return vec3(uWindDir.x * s, 0.0, uWindDir.y * s);
}

// World-space displacement of a vertex at worldPos whose model origin is at root (world). localY is the vertex height
// in model space; color is the vertex colour (grass: a = height in clump; tree: r = branch, g = flutter, b = phase).
vec3 windDisplacement(vec3 worldPos, vec3 root, float localY, vec4 color) {
    if (uWindProfile == 0 || uWindSpeed == 0.0) return vec3(0.0);
    float stiffness = max(uWindBend.x, 0.05);
    vec3 w = windSample(root.xz, uWindTime) / 16.0; // storm speed bends by about 1
    float phase = dot(root.xz, vec2(0.37, 0.71));
    vec3 offset;
    float h;
    if (uWindProfile == 1) {
        h = clamp(color.a, 0.0, 1.0);
        offset = w * (h * h / stiffness);
        float flutter = uWindTurbulence * uWindBend.y * sin(uWindTime * 9.0 + phase + worldPos.x) * 0.05;
        offset.xz += uWindDir * flutter * h;
    } else {
        h = clamp((localY - uWindHeight.x) / max(uWindHeight.y, 1e-3), 0.0, 1.0);
        offset = w * (h * h / stiffness) * 0.5;
        float branch = color.r * sin(uWindTime * 2.1 + phase + color.b * WIND_TAU) * 0.08;
        float leaf = color.g * uWindTurbulence * uWindBend.y * sin(uWindTime * 11.0 + color.b * WIND_TAU + worldPos.y) * 0.03;
        offset.xz += uWindDir * branch * length(w);
        offset += vec3(leaf, leaf * 0.5, leaf);
    }
    // Keep the length: lower the vertex as it bends
    float bend = length(offset.xz);
    offset.y -= bend * bend * 0.5 / max(h, 0.1);
    return offset;
}
