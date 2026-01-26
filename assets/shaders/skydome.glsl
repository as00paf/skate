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
    gl_Position = (projectionMatrix * viewMatrix * worldPos).xyww; // Force max depth
}

#type fragment
#version 330 core

in vec2 fTexCoords;
in vec3 fWorldPos;
out vec4 color;

uniform sampler2D u_hdriTexture;
uniform vec3 u_skyTint;
uniform float u_exposure;

uniform vec3 uFogColor;
uniform float uFogDensity;
uniform float uFogGradient;
uniform vec3 uCameraPos;

void main()
{
    vec4 texColor = texture(u_hdriTexture, fTexCoords);
    
    // Apply exposure and tint
    vec3 finalSkyColor = texColor.rgb * u_skyTint * u_exposure;

    // Horizon blending with fog
    // We want the bottom part of the sphere to fade into the fog color
    // fTexCoords.y goes from 0 (bottom) to 1 (top)
    float horizonFactor = smoothstep(0.4, 0.55, fTexCoords.y);
    finalSkyColor = mix(uFogColor, finalSkyColor, horizonFactor);

    color = vec4(finalSkyColor, 1.0);
}
