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
    }
}