package com.pafoid.skate.engine.assets

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
        const val TRANSFORMATION_MATRIX = "viewMatrix"

        const val LIGHT_POSITION = "lightPosition"
        const val LIGHT_COLOR = "lightColor"
        const val AMBIENT_LIGHT = "uAmbientLight"
        const val SUN_DIRECTION = "uSunDirection"
        const val SUN_COLOR = "uSunColor"

        const val ALPHA_MODE = "u_AlphaMode"
        const val BASE_COLOR_TEXTURE = "u_BaseColorTexture"
        const val BASE_COLOR_FACTOR = "u_BaseColorFactor"
        const val HAS_NORMAL_MAP = "u_HasNormalMap"
        const val HAS_METALLIC_ROUGHNESS_TEXTURE = "u_HasMetallicRoughnessTexture"
        const val HAS_AO_TEXTURE = "u_HasAOTexture"
        const val HAS_EMISSIVE_TEXTURE = "u_HasEmissiveTexture"
        const val HAS_SKIN = "u_HasSkin"
    }
}