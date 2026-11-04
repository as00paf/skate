#type vertex
#version 330 core
layout (location=0) in vec2 aPos;
layout (location=1) in vec4 aColor;
layout (location=2) in vec2 aTexCoords;
layout (location = 8) in float aTexId;
layout (location = 9) in float aEntityId;

uniform mat4 uProjection;
uniform mat4 uView;

out vec4 fColor;
out vec2 fTexCoords;
flat out float fTexId;
flat out float fEntityId;

void main()
{
    fColor = aColor;
    fTexCoords = aTexCoords;
    fTexId = aTexId;
    fEntityId = aEntityId;

    gl_Position = uProjection * uView * vec4(aPos, 0.0, 1.0);
}

#type fragment
#version 330 core

in vec4 fColor;
in vec2 fTexCoords;
flat in float fTexId;
flat in float fEntityId;

uniform sampler2D uTextures[8];

out vec4 color;

void main()
{
    // fTexId == -1.0 indicates no texture; valid slots are 0..(N-1)
    if (fTexId >= 0.0) {
        int id = int(fTexId + 0.5);
        vec4 texel = texture(uTextures[id], fTexCoords);
        // World sprites carry encoded entity ids (>= 1) and UI quads 0; only the negative SDF marker selects this path
        if (fEntityId < -0.5) {
            // SDF glyph: the atlas alpha is a distance field with the edge at 0.5; antialias over one screen pixel
            float w = max(fwidth(texel.a) * 0.75, 0.0001);
            color = vec4(fColor.rgb, fColor.a * smoothstep(0.5 - w, 0.5 + w, texel.a));
        } else {
            color = fColor * texel;
        }
    } else {
        color = fColor;
    }
}