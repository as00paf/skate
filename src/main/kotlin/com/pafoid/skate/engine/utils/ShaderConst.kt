package com.pafoid.skate.engine.utils

object ShaderConst {
    const val SPLITTER_REGEX = "(#type)( )+([a-zA-Z]+)"
    const val TYPE_DELIMITER = "#type"
    const val EOL_DELIMITER = "\r\n"
    const val TYPE_DELIMITER_COUNT = 6
    const val FRAGMENT = "fragment"
    const val VERTEX = "vertex"

    object Attribs {
        const val POSITION = "aPos"
        const val TEX_COORDS = "aTexCoords"
        const val NORMAL = "aNormal"

        const val PROJECTION_MATRIX = "projectionMatrix"
        const val VIEW_MATRIX = "viewMatrix"
        const val TRANSFORMATION_MATRIX = "transformationMatrix"
    }

    object Uniforms {
        const val PROGRESS = "uProgress"
        const val ALPHA = "uAlpha"
        const val TEXTURE = "uTexture"
        const val USE_BATCH = "uUseBatchId"

        const val TEXTURES = "uTextures"
        const val TEXTURE_SCALE = "uTextureScale"

        const val LIGHT_POSITION = "lightPosition"
        const val LIGHT_COLOR = "lightColor"
        const val AMBIENT_LIGHT = "uAmbientLight"

        const val SKY_TINT = "u_skyTint"
        const val SKY_EXPOSURE = "u_exposure"
        const val HDRI_TEXTURE= "u_hdriTexture"

        const val SUN_DIRECTION = "uSunDirection"
        const val SUN_COLOR = "uSunColor"

        const val MOON_DIRECTION = "uMoonDirection"
        const val MOON_COLOR = "uMoonColor"

        const val FOG_COLOR = "uFogColor"
        const val FOG_DENSITY = "uFogDensity"
        const val FOG_GRADIENT = "uFogGradient"

        const val BASE_COLOR_TEXTURE = "u_BaseColorTexture"
        const val BASE_COLOR_FACTOR = "u_BaseColorFactor"

        const val METALLIC_ROUGHNESS_TEXTURE = "u_MetallicRoughnessTexture"
        const val HAS_METALLIC_ROUGHNESS_TEXTURE = "u_HasMetallicRoughnessTexture"
        const val METALLIC_FACTOR = "u_MetallicFactor"
        const val ROUGHNESS_FACTOR = "u_RoughnessFactor"

        const val EMISSIVE_TEXTURE = "u_EmissiveTexture"
        const val HAS_EMISSIVE_TEXTURE = "u_HasEmissiveTexture"
        const val EMISSIVE_FACTOR = "u_EmissiveFactor"

        const val AO_TEXTURE = "u_AOTexture"
        const val HAS_AO_TEXTURE = "u_HasAOTexture"

        const val HAS_SKIN = "u_HasSkin"
        const val JOINT_MATRICES = "u_JointMatrices"

        const val VIEW = "uView"
        const val MODEL = "uModel"
        const val OBJECT_ID = "uObjectId"
        const val ENTITY_ID = "uEntityId"
        const val SELECTED = "uSelected"

        const val CAMERA_POSITION = "uCameraPos"
        const val PROJECTION = "uProjection"
        const val PROJECTION_MATRIX = "projectionMatrix"
        const val VIEW_MATRIX = "viewMatrix"
        const val TRANSFORMATION_MATRIX = "transformationMatrix"

        const val ALPHA_MODE = "u_AlphaMode"
        const val ALPHA_CUTOFF = "u_AlphaCutoff"

        const val NORMAL_MAP = "u_NormalMap"
        const val HAS_NORMAL_MAP = "u_HasNormalMap"

        const val LIGHT_SPACE_MATRIX = "uLightSpaceMatrix"
        const val MODEL_MATRIX = "uModelMatrix"
        const val SHADOW_MAP = "uShadowMap"
        const val SHADOW_MAP_TEXEL_SIZE = "uShadowMapTexelSize"
        const val SHADOW_DEPTH_BIAS = "uShadowDepthBias"
        const val SHADOW_SLOPE_SCALED_BIAS = "uShadowSlopeScaledBias"
    }
}