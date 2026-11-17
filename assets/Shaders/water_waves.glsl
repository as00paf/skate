// @skate-contract 1
// Gerstner waves (REN-29 §6.1), included by water.glsl. Mirrors com.pafoid.skate.engine.environment.WaterWaves; keep
// the two in sync (WaterWavesTest pins a table). Waves are evaluated at world XZ, so buoyancy (PHY-16) reads the same
// surface with WaterWaves.heightAt.
//
// Wave i: uWaveA<i> = (dirX, dirZ, k, amplitude), uWaveB<i> = (Q, omega, 0, 0); phase = k * dot(dir, p) - omega * t.

uniform int uWaveCount;
uniform vec4 uWaveA0;
uniform vec4 uWaveA1;
uniform vec4 uWaveA2;
uniform vec4 uWaveA3;
uniform vec4 uWaveB0;
uniform vec4 uWaveB1;
uniform vec4 uWaveB2;
uniform vec4 uWaveB3;

vec4 waveA(int i) { return i == 0 ? uWaveA0 : i == 1 ? uWaveA1 : i == 2 ? uWaveA2 : uWaveA3; }
vec4 waveB(int i) { return i == 0 ? uWaveB0 : i == 1 ? uWaveB1 : i == 2 ? uWaveB2 : uWaveB3; }

// Displacement of grid point p (world XZ) at time t; also the surface normal and the horizontal Jacobian
// (1 on flat water, towards 0 where crests pinch: crest foam).
vec3 gerstner(vec2 p, float t, out vec3 normal, out float jacobian)
{
    vec3 d = vec3(0.0);
    vec3 n = vec3(0.0, 1.0, 0.0);
    float jxx = 1.0, jzz = 1.0, jxz = 0.0;
    for (int i = 0; i < 4; ++i) {
        if (i >= uWaveCount) break;
        vec4 a = waveA(i);
        vec4 b = waveB(i);
        vec2 dir = a.xy;
        float k = a.z, amp = a.w, q = b.x, omega = b.y;
        float th = k * dot(dir, p) - omega * t;
        float c = cos(th), s = sin(th);
        d.xz += q * amp * dir * c;
        d.y += amp * s;
        n.xz -= dir * k * amp * c;
        n.y -= q * k * amp * s;
        float qka = q * k * amp * s;
        jxx -= qka * dir.x * dir.x;
        jzz -= qka * dir.y * dir.y;
        jxz -= qka * dir.x * dir.y;
    }
    normal = normalize(n);
    jacobian = jxx * jzz - jxz * jxz;
    return d;
}
