package com.pafoid.skate.engine.utils

object ShaderConst {
    const val SPLITTER_REGEX = "(#type)( )+([a-zA-Z]+)"
    const val TYPE_DELIMITER = "#type"
    const val EOL_DELIMITER = "\r\n"
    const val TYPE_DELIMITER_COUNT = 6
    const val FRAGMENT = "fragment"
    const val VERTEX = "vertex"

    enum class Attribs(name: String) {
        POSITION("aPos"),           //0
        TEX_COORDS("aTexCoords"),   //1
        NORMAL("aNormal"),          //2
        TANGENT("aTangent"),        //3
        COLOR("aColor"),            //4
        TEX_COORDS_1("aTexCoords1"),//5
        JOINTS("aJoints"),          //6
        WEIGHTS("aWeights"),        //7
        TEX_ID("aTexId"),           //8
        ENTITY_ID("aEntityId"),     //9
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

        const val LIGHT_COUNT = "uLightCount"
        const val OBJECT_LIGHT_COUNT = "uObjLightCount"
        const val OBJECT_LIGHTS = "uObjLights"

        const val SKY_TINT = "u_skyTint"
        const val SKY_EXPOSURE = "u_exposure"
        const val HDRI_TEXTURE= "u_hdriTexture"

        const val SUN_DIRECTION = "uSunDirection"
        const val SUN_COLOR = "uSunColor"

        const val FOG_COLOR = "uFogColor"
        const val FOG_DENSITY = "uFogDensity"
        const val FOG_MODE = "uFogMode"
        const val FOG_START = "uFogStart"
        const val FOG_END = "uFogEnd"
        const val FOG_HEIGHT = "uFogHeight"
        const val FOG_HEIGHT_FALLOFF = "uFogHeightFalloff"
        const val FOG_SKY_DISTANCE = "uFogSkyDistance"

        const val BASE_COLOR = "uBaseColor"
        const val BASE_COLOR_TEXTURE = "u_BaseColorTexture"
        const val BASE_COLOR_FACTOR = "u_BaseColorFactor"

        const val HAS_BASE_COLOR_TEXTURE = "uHasBaseColorTexture"

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

        /** Cascaded / local shadow uniforms (see `shader_3d_default.glsl`). */
        const val CASCADE_SHADOW_MAP = "uCascadeShadowMap"
        const val LOCAL_SHADOW_MAP = "uLocalShadowMap"
        const val CASCADE_COUNT = "uCascadeCount"
        const val CASCADE_MATRICES = "uCascadeMatrices"
        const val CASCADE_SPLITS = "uCascadeSplits"
        const val CASCADE_NORMAL_OFFSET = "uCascadeNormalOffset"
        const val CASCADE_DEPTH_BIAS = "uCascadeDepthBias"
        const val SHADOW_TEXEL_SIZE = "uShadowTexelSize"
        const val SHADOW_FADE_START = "uShadowFadeStart"
        const val PCF_RADIUS = "uPcfRadius"
        const val SHADOWED_LIGHT_COUNT = "uShadowedLightCount"
        const val SHADOW_LIGHT_INDEX = "uShadowLightIndex"
        const val SHADOW_LIGHT_BASE = "uShadowLightBase"
        const val SHADOW_LIGHT_TEXEL = "uShadowLightTexel"
        const val LOCAL_SHADOW_MATRICES = "uLocalShadowMatrices"
        const val LOCAL_SHADOW_TEXEL_SIZE = "uLocalShadowTexelSize"

        /**
         * Bloom effect uniforms.
         */
        const val BLOOM_INTENSITY = "uBloomIntensity"
        const val BLOOM_THRESHOLD = "uBloomThreshold"

        /**
         * ACES tonemapping exposure uniform.
         */
        const val ACES_EXPOSURE = "uAcesExposure"

        /**
         * Gamma correction exponent uniform (linear -> sRGB).
         */
        const val GAMMA_VALUE = "uGamma"

        /**
         * FXAA anti-aliasing uniforms (mix factor and sampling texel size).
         */
        const val FXAA_TEXEL_SIZE = "uTexelSize"
        const val FXAA_STRENGTH = "uFxaaStrength"
        const val FXAA_EDGE_THRESHOLD = "uFxaaEdgeThreshold"
        const val FXAA_EDGE_THRESHOLD_MIN = "uFxaaEdgeThresholdMin"

        /**
         * SSAO effect uniforms (radius in UV units, strength, depth sampler).
         */
        const val SSAO_RADIUS = "uSsaORadius"
        const val SSAO_INTENSITY = "uSsaOIntensity"
        const val SSAO_DEPTH_TEXTURE = "uDepthTexture"

        /**
         * Shadow map texture unit for shadow mapping.
         * Must match the texture unit used when binding the shadow map texture.
         */
        const val SHADOW_TEXTURE_UNIT = 5

        /** Texture unit for the spot/point light shadow map array. */
        const val LOCAL_SHADOW_TEXTURE_UNIT = 6

        /** Image-based lighting (REN-27, EnvironmentProbe): enable flag, SH irradiance, sky rotation and strengths. */
        const val ENV_ENABLED = "uEnvEnabled"
        const val ENV_SH = "uEnvSH"
        const val ENV_ROTATION = "uEnvRotation"
        const val ENV_MAX_LOD = "uEnvMaxLod"
        const val ENV_REFLECTION = "uEnvReflection"
        const val ENV_SKY_LIGHT = "uEnvSkyLight"
        const val ENV_SKY_SCALE = "uEnvSkyScale"
        const val ENV_PREFILTERED = "uEnvPrefiltered"
        const val ENV_SKY = "uEnvSky"
        const val ENV_BRDF_LUT = "uEnvBrdfLut"
        const val ENV_PREFILTERED_UNIT = 7
        const val ENV_SKY_UNIT = 8
        const val ENV_BRDF_LUT_UNIT = 9

        /** Wind (REN-29 §5.2, `wind.glsl`): direction (unit XZ), speed, gust (strength, scale), turbulence, clock. */
        const val WIND_DIR = "uWindDir"
        const val WIND_SPEED = "uWindSpeed"
        const val WIND_GUST = "uWindGust"
        const val WIND_TURBULENCE = "uWindTurbulence"
        const val WIND_TIME = "uWindTime"

        /** Per-draw wind response: profile (0 none, 1 grass, 2 tree), (stiffness, flutter), model height range. */
        const val WIND_PROFILE = "uWindProfile"
        const val WIND_BEND = "uWindBend"
        const val WIND_HEIGHT = "uWindHeight"

        /**
         * Water (REN-29 §6, `water.glsl`/`water_waves.glsl`): up to 4 Gerstner waves as separate uniforms (no arrays,
         * so every backend maps them the same): `uWaveA<i>` = (dirX, dirZ, k, amplitude), `uWaveB<i>` = (Q, ω, 0, 0).
         */
        const val WAVE_COUNT = "uWaveCount"
        val WAVE_A = Array(4) { "uWaveA$it" }
        val WAVE_B = Array(4) { "uWaveB$it" }
        const val WATER_MODEL = "uWaterModel"
        const val WATER_TIME = "uWaveTime"
        const val WATER_SKIRT_DEPTH = "uSkirtDepth"
        const val WATER_RECEIVE_SHADOWS = "uReceiveShadows"
        const val WATER_FLOW = "uWaterFlow"
        const val WATER_DEPTH = "uWaterDepth"
        const val WATER_SCREEN = "uScreenSize"
        const val WATER_NEAR_FAR = "uNearFar"
        const val WATER_SSR_STEPS = "uSsrSteps"
        const val WATER_SCENE_COLOR = "uSceneColor"
        const val WATER_SCENE_DEPTH = "uSceneDepth"
        const val WATER_NORMAL_A = "normalA"
        const val WATER_NORMAL_B = "normalB"
        const val WATER_FOAM = "foam"

        /** Terrain chunks (REN-28 §4, `terrain.glsl`): chunk corner (world), sample spacing, skirt drop, base height. */
        const val TERRAIN_CORNER = "uTerrainCorner"
        const val TERRAIN_SPACING = "uTerrainSpacing"
        const val TERRAIN_SKIRT_DROP = "uSkirtDrop"
        const val TERRAIN_BASE = "uTerrainBase"

        /** Texture units of the water pass (after the IBL units). */
        const val WATER_SCENE_COLOR_UNIT = 10
        const val WATER_SCENE_DEPTH_UNIT = 11
        const val WATER_NORMAL_A_UNIT = 12
        const val WATER_NORMAL_B_UNIT = 13
        const val WATER_FOAM_UNIT = 14
    }
}
