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
uniform float uProgress;
uniform float uAlpha;

void main() {
    vec4 texColor = texture(uTexture, fTexCoords);
    
    // Simple progress bar effect at the bottom
    float barHeight = 0.05;
    vec4 finalColor;
    if (fTexCoords.y < barHeight) {
        if (fTexCoords.x < uProgress) {
            finalColor = vec4(0.8, 0.2, 0.2, 1.0); // Red progress
        } else {
            finalColor = vec4(0.2, 0.2, 0.2, 1.0); // Grey background
        }
    } else {
        finalColor = texColor;
    }
    color = finalColor * uAlpha;
}
