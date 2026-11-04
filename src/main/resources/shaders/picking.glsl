#type vertex
#version 330 core
layout (location=0) in vec2 aPos;
layout (location=1) in vec4 aColor;
layout (location=2) in vec2 aTexCoords;
// Must match RenderConsts2D.*_LOCATION (RenderBatch2D feeds texId at 3 and entityId at 4)
layout (location = 3) in float aTexId;
layout (location = 4) in float aEntityId;

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

out vec3 color;

void main()
{
    // Same texture convention as shader_2d_batch: -1 means no texture, 0..N-1 are sampler slots
    vec4 texColor = fColor;
    if (fTexId >= 0.0) {
        int id = int(fTexId + 0.5);
        texColor = fColor * texture(uTextures[id], fTexCoords);
    }

    // Transparent texels are not part of the sprite's clickable shape
    if (texColor.a < 0.1) {
        discard;
    }

    color = vec3(fEntityId, 0.0, 0.0);
}


