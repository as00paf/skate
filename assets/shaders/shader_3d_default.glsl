#type vertex
#version 330 core
layout (location=0) in vec3 aPos;
layout (location=1) in vec2 aTexCoords;
layout (location=2) in vec3 aNormal;

out vec2 fTexCoords;
out vec3 fSurfaceNormal;
out vec3 fToLightVector;
out vec3 fToCameraVector;

uniform mat4 transformationMatrix;
uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform vec3 lightPosition;

void main()
{
    vec4 worldPosition = transformationMatrix * vec4(aPos, 1.0);
    vec4 positionRelativeToCamera = viewMatrix * worldPosition;
    gl_Position = projectionMatrix * positionRelativeToCamera;
    
    fTexCoords = aTexCoords;

    fSurfaceNormal = normalize((transformationMatrix * vec4(aNormal, 0.0)).xyz);
    fToLightVector = lightPosition - worldPosition.xyz;
    fToCameraVector = (inverse(viewMatrix) * vec4(0.0, 0.0, 0.0, 1.0)).xyz - worldPosition.xyz;
}

#type fragment
#version 330 core

in vec2 fTexCoords;
in vec3 fSurfaceNormal;
in vec3 fToLightVector;
in vec3 fToCameraVector;

uniform sampler2D textureSampler;
uniform vec3 lightColor;
uniform float uShininess;
uniform float uReflectivity;
uniform vec3 uAmbientLight;
uniform float uSelected;

out vec4 color;

void main()
{
    if (uSelected > 0.5 && uSelected < 1.5) {
        color = vec4(0.0, 1.0, 0.0, 1.0); // Selected (Green)
        return;
    }
    
    vec4 textureColor = texture(textureSampler, fTexCoords);
    if (textureColor.a < 0.1) {
        discard;
    }

    if (uSelected > 1.5) {
        color = mix(textureColor, vec4(1.0, 1.0, 0.0, 1.0), 0.2); // Hovered (Yellow Tint)
        return;
    }

    vec3 unitNormal = normalize(fSurfaceNormal);
    if (length(fSurfaceNormal) < 0.001) unitNormal = vec3(0, 1, 0); // Fallback for zero normals
    if (length(fSurfaceNormal) < 0.001) unitNormal = vec3(0, 1, 0); // Fallback for zero normals
    
    vec3 unitLightVector = normalize(fToLightVector);
    vec3 unitVectorToCamera = normalize(fToCameraVector);

    // Diffuse
    float nDot1 = dot(unitNormal, unitLightVector);
    float brightness = max(nDot1, 0.0);
    vec3 diffuse = brightness * lightColor;

    // Specular
    vec3 lightDirection = -unitLightVector;
    vec3 reflectedLightDirection = reflect(lightDirection, unitNormal);
    float specularFactor = dot(reflectedLightDirection, unitVectorToCamera);
    specularFactor = max(specularFactor, 0.0);
    float dampedFactor = pow(specularFactor, uShininess);
    vec3 finalSpecular = dampedFactor * uReflectivity * lightColor;

    color = vec4(uAmbientLight + diffuse, 1.0) * textureColor + vec4(finalSpecular, 0.0);
}
