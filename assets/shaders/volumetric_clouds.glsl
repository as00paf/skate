#type vertex
#version 330 core
layout (location = 0) in vec3 aPos;

out vec3 fPos;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;

void main()
{
    fPos = aPos;
    // We render a full-screen quad or a large box for raymarching
    // Let's assume a screen quad for now and we'll calculate ray dir in fragment
    gl_Position = vec4(aPos.x, aPos.y, 0.0, 1.0);
}

#type fragment
#version 330 core

in vec3 fPos;
out vec4 color;

uniform sampler3D uNoiseTexture;
uniform sampler2D uDepthTexture;
uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform vec3 uCameraPos;
uniform vec3 uSunDirection;
uniform vec3 uSunColor;
uniform vec3 uSkyColor;
uniform float uTime;

uniform float uFogDensity;
uniform float uFogGradient;

// Raymarching parameters
const int STEPS = 64;
const int LIGHT_STEPS = 6;
const float CLOUD_MIN_Y = 100.0;
const float CLOUD_MAX_Y = 300.0;

float beersLaw(float dist, float absorption) {
    return exp(-dist * absorption);
}

float getDensity(vec3 p) {
    if (p.y < CLOUD_MIN_Y || p.y > CLOUD_MAX_Y) return 0.0;
    
    vec3 uvw = p * 0.002 + vec3(uTime * 0.005, 0.0, 0.0);
    float noise = texture(uNoiseTexture, uvw).r;
    
    float heightFactor = smoothstep(CLOUD_MIN_Y, CLOUD_MIN_Y + 50.0, p.y) * 
                         (1.0 - smoothstep(CLOUD_MAX_Y - 50.0, CLOUD_MAX_Y, p.y));
    
    return max(0.0, noise - 0.4) * heightFactor * 2.0;
}

float lightMarch(vec3 p) {
    vec3 dirToSun = -normalize(uSunDirection);
    float stepSize = 5.0;
    float totalDensity = 0.0;
    for (int i = 0; i < LIGHT_STEPS; i++) {
        p += dirToSun * stepSize;
        totalDensity += getDensity(p) * stepSize;
    }
    return beersLaw(totalDensity, 0.3);
}

float getLinearDepth(float depth) {
    float z = depth * 2.0 - 1.0; 
    // Need near/far for true linear depth, but we can approximate or pass them
    return (2.0 * 0.1 * 1000.0) / (1000.0 + 0.1 - z * (1000.0 - 0.1));
}

void main()
{
    // Screen UV for depth sampling
    vec2 screenUv = (fPos.xy + 1.0) * 0.5;
    float sceneDepth = texture(uDepthTexture, screenUv).r;
    float linearSceneDepth = getLinearDepth(sceneDepth);

    // Reconstruct Ray Direction
    vec4 ndc = vec4(fPos.x, fPos.y, -1.0, 1.0);
    mat4 invProj = inverse(projectionMatrix);
    mat4 invView = inverse(viewMatrix);
    
    vec4 viewPos = invProj * ndc;
    viewPos /= viewPos.w;
    viewPos.z = -1.0;
    viewPos.w = 0.0;
    
    vec3 rayDir = normalize((invView * viewPos).xyz);
    vec3 rayOrigin = uCameraPos;

    // Ray-Plane Intersection
    float tMin = (CLOUD_MIN_Y - rayOrigin.y) / rayDir.y;
    float tMax = (CLOUD_MAX_Y - rayOrigin.y) / rayDir.y;
    
    if (tMin > tMax) { float tmp = tMin; tMin = tMax; tMax = tmp; }
    if (tMax < 0.0) discard;
    tMin = max(0.0, tMin);

    // Depth Sync: don't march past opaque geometry
    tMax = min(tMax, linearSceneDepth);
    if (tMin >= tMax) discard;

    vec3 currentPos = rayOrigin + rayDir * tMin;
    float totalDist = tMin;
    float stepSize = (tMax - tMin) / float(STEPS);
    
    float transparency = 1.0;
    vec3 lightEnergy = vec3(0.0);

    for (int i = 0; i < STEPS; i++) {
        float density = getDensity(currentPos);
        if (density > 0.01) {
            float lightTrans = lightMarch(currentPos);
            
            // Fog on clouds
            float fogVis = exp(-pow((totalDist * uFogDensity), uFogGradient));
            vec3 cloudColor = mix(uSkyColor, uSunColor, lightTrans); // Simple scattering approx
            
            lightEnergy += density * stepSize * transparency * cloudColor;
            transparency *= beersLaw(density, stepSize);
        }
        
        if (transparency < 0.01) break;
        currentPos += rayDir * stepSize;
        totalDist += stepSize;
    }

    color = vec4(lightEnergy, 1.0 - transparency);
}
