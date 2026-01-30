#type vertex
#version 330 core
layout (location = 0) in vec3 aPos;      // Local space sphere coordinates
layout (location = 1) in vec2 aTexCoords; // Spherical UV coordinates

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
    
    // Transform to clip space
    vec4 clipPos = projectionMatrix * viewMatrix * worldPos;
    
    // --- The xyww trick ---
    // By setting z to w, the depth (z/w) becomes 1.0 after perspective division.
    // This ensures the sky is always rendered at the maximum depth (the far plane),
    // allowing other objects to be rendered on top of it.
    gl_Position = clipPos.xyww;
}

#type fragment
#version 330 core

in vec2 fTexCoords;
in vec3 fWorldPos;
out vec4 color;

uniform sampler2D u_hdriTexture; // Lat-Long HDRI map
uniform vec3 u_skyTint;
uniform float u_exposure;

uniform vec3 uFogColor;
uniform float uFogDensity;
uniform float uFogGradient;
uniform vec3 uCameraPos;

void main()
{
    // textureLod is used to avoid mipmap artifacts at the UV wrap-around seam.
    vec4 texColor = textureLod(u_hdriTexture, fTexCoords, 0.0);
    
    // Exposure & Tinting
    vec3 finalSkyColor = texColor.rgb * u_skyTint * u_exposure;

    // Horizon Blending: Fades the sky color into the fog color near the horizon.
    // This helps ground the scene and hide the sphere's edge.
    float horizonFactor = smoothstep(0.4, 0.55, fTexCoords.y);
    finalSkyColor = mix(uFogColor, finalSkyColor, horizonFactor);

    color = vec4(finalSkyColor, 1.0);
}
