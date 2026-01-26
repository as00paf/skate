#type vertex
#version 330 core
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTexCoords;

out vec2 fTexCoords;
out vec3 fWorldPos;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 transformationMatrix;

void main()
{
    fTexCoords = aTexCoords;
    vec4 worldPos = transformationMatrix * vec4(aPos, 1.0);
    fWorldPos = worldPos.xyz;
    gl_Position = projectionMatrix * viewMatrix * worldPos;
}

#type fragment
#version 330 core

in vec2 fTexCoords;
in vec3 fWorldPos;
out vec4 color;

uniform sampler2D uNoiseBase;
uniform sampler2D uNoiseDetail;
uniform vec2 uOffsetBase;
uniform vec2 uOffsetDetail;

uniform vec3 uSunDirection;
uniform vec3 uSunColor;
uniform vec3 uSkyColor;
uniform vec3 uFogColor;
uniform float uFogDensity;
uniform float uFogGradient;
uniform vec3 uCameraPos;

void main()
{
    // Sample two noise layers with different panning
    float noise1 = texture(uNoiseBase, fTexCoords + uOffsetBase).r;
    float noise2 = texture(uNoiseDetail, fTexCoords + uOffsetDetail).r;
    
    // Combine noise
    float combined = max(0.0, noise1 + noise2 * 0.5 - 0.4);
    float cloudAlpha = smoothstep(0.3, 0.7, combined);
    
    if (cloudAlpha < 0.01) discard;

    // Basic Rim Lighting / Fake Volumetric look
    // Check if the sun is "behind" the cloud relative to view
    vec3 viewDir = normalize(uCameraPos - fWorldPos);
    vec3 L = normalize(-uSunDirection);
    float rim = pow(max(0.0, dot(viewDir, L)), 4.0); // Strong rim light when looking towards sun
    
    vec3 cloudBaseColor = vec3(1.0); // White clouds
    vec3 finalCloudColor = mix(cloudBaseColor, uSunColor, rim * 0.8);
    
    // Add shading based on noise values (shadows inside the cloud)
    finalCloudColor *= (0.6 + combined * 0.4);

    // Fog
    float dist = length(uCameraPos - fWorldPos);
    float visibility = exp(-pow((dist * uFogDensity), uFogGradient));
    visibility = clamp(visibility, 0.0, 1.0);

    color = vec4(mix(uFogColor, finalCloudColor, visibility), cloudAlpha * visibility);
}
