package com.pafoid.skate.engine.assets

object Assets {
    object Shaders {
        const val SPLASH = "/shaders/splash.glsl"
        const val SHADER_3D_DEFAULT = "/shaders/shader_3d_default.glsl"
        const val SHADER_2D_BATCH = "/shaders/shader_2d_batch.glsl"
        const val PICKING = "/shaders/picking.glsl"
        const val PICKING_3D = "/shaders/shader_3d_picking.glsl"
        const val SKYBOX = "/shaders/skybox.glsl"
        const val DEBUG = "/shaders/debugLine2D.glsl"
        const val SKY_DOME = "/shaders/skydome.glsl"
        const val SHADOW = "/shaders/shadow.glsl"
    }
    object Textures {
        const val APP_ICON = "assets/textures/app_icon.png"
        const val SPLASH = "assets/textures/splash_screen.png"

        const val XBOX_CONTROLLER = "assets/textures/xbox_controller.png"

        const val DEFAULT = "assets/textures/default.png"
        const val WHITE = "assets/textures/white.png"
        const val ASPHALT = "assets/textures/asphalt.png"
        const val CONCRETE_SIMPLE = "assets/textures/concrete_simple.png"

        const val WOOD_BROWN = "assets/textures/skatelite_brown.png"
        const val WOOD_LIGHT = "assets/textures/skatelite_light.png"
        const val WOOD_TAN = "assets/textures/skatelite_tan.png"
        const val WOOD_DARK = "assets/textures/skatelite_dark.png"
        const val METAL = "assets/textures/metal.png"

        const val SKY_HDRI = "assets/textures/sky_hdri.png"
    }
    object Models {
        const val CUBE = "assets/obj/cube.obj"
        const val RAIL = "assets/obj/rail.obj"
        const val LEDGE = "assets/obj/ledge.obj"
        const val KICKER = "assets/obj/kicker.obj"
        const val MANUAL_PAD = "assets/obj/manual_pad.obj"
        const val BANK = "assets/obj/bank.obj"
        const val QUARTER_PIPE = "assets/obj/quarter_pipe.obj"
        const val SKATEBOARD_GLB = "assets/obj/skateboard_free_model.glb"
        const val JAMES = "assets/characters/james.glb"
    }
    object Fonts {
        const val FONTS_FILE = "/fonts/Font Awesome 7 Free-Solid-900.otf"
    }
    object Files {
        const val IMGUI = "imgui.ini"
    }
    object Folders {
        const val ANIMATIONS = "assets/characters/animations"
        const val CHARACTERS = "assets/characters"
        const val TEXTURES = "assets/textures"
    }
    object Animations {
        const val IDLE_0 = "assets/characters/animations/idle_0.fbx"
        const val IDLE_1 = "assets/characters/animations/idle_1.fbx"
        const val IDLE_PHONE = "assets/characters/animations/idle phone.fbx"

        const val JUMP = "assets/characters/animations/jump.fbx"
        const val FALLING = "assets/characters/animations/falling to roll.fbx"
        const val FALLING_IDLE = "assets/characters/animations/falling idle.fbx"
        const val LANDING = "assets/characters/animations/hard landing.fbx"
        const val WALKING = "assets/characters/animations/walking.fbx"
        const val RUNNING = "assets/characters/animations/running.fbx"

        const val LEFT_TURN = "assets/characters/animations/left turn.fbx"
        const val LEFT_TURN_90 = "assets/characters/animations/left turn 90.fbx"
        const val LEFT_STRAFE = "assets/characters/animations/left strafe.fbx"
        const val LEFT_STRAFE_WALKING = "assets/characters/animations/left strafe walking.fbx"

        const val RIGHT_TURN = "assets/characters/animations/right turn.fbx"
        const val RIGHT_TURN_90 = "assets/characters/animations/right turn 90.fbx"
        const val RIGHT_STRAFE = "assets/characters/animations/right strafe.fbx"
        const val RIGHT_STRAFE_WALKING = "assets/characters/animations/right strafe walking.fbx"
    }
    object Strings {
        const val EDITOR_STRINGS_EN = "assets/strings/editor_strings.properties"
        const val EDITOR_STRINGS_FR = "assets/strings/editor_strings_fr.properties"
    }

    object Bundled {
        const val JAMES = "EngineDefaults/Characters/james.glb"

        const val SKATEBOARD_GLB = "EngineDefaults/Models/skateboard_free_model.glb"

        const val CUBE = "EngineDefaults/Models/cube.obj"
        const val RAIL = "EngineDefaults/Models/rail.obj"
        const val LEDGE = "EngineDefaults/Models/ledge.obj"
        const val KICKER = "EngineDefaults/Models/kicker.obj"
        const val MANUAL_PAD = "EngineDefaults/Models/manual_pad.obj"
        const val BANK = "EngineDefaults/Models/bank.obj"
        const val QUARTER_PIPE = "EngineDefaults/Models/quarter_pipe.obj"

        const val IDLE_0 = "EngineDefaults/Characters/animations/idle_0.fbx"
        const val IDLE_1 = "EngineDefaults/Characters/animations/idle_1.fbx"
        const val IDLE_PHONE = "EngineDefaults/Characters/animations/idle phone.fbx"
        const val JUMP = "EngineDefaults/Characters/animations/jump.fbx"
        const val FALLING = "EngineDefaults/Characters/animations/falling to roll.fbx"
        const val FALLING_IDLE = "EngineDefaults/Characters/animations/falling idle.fbx"
        const val LANDING = "EngineDefaults/Characters/animations/hard landing.fbx"
        const val WALKING = "EngineDefaults/Characters/animations/walking.fbx"
        const val RUNNING = "EngineDefaults/Characters/animations/running.fbx"
        const val LEFT_TURN = "EngineDefaults/Characters/animations/left turn.fbx"
        const val LEFT_TURN_90 = "EngineDefaults/Characters/animations/left turn 90.fbx"
        const val LEFT_STRAFE = "EngineDefaults/Characters/animations/left strafe.fbx"
        const val LEFT_STRAFE_WALKING = "EngineDefaults/Characters/animations/left strafe walking.fbx"
        const val RIGHT_TURN = "EngineDefaults/Characters/animations/right turn.fbx"
        const val RIGHT_TURN_90 = "EngineDefaults/Characters/animations/right turn 90.fbx"
        const val RIGHT_STRAFE = "EngineDefaults/Characters/animations/right strafe.fbx"
        const val RIGHT_STRAFE_WALKING = "EngineDefaults/Characters/animations/right strafe walking.fbx"

        const val ASPHALT = "EngineDefaults/Textures/asphalt.png"
        const val CONCRETE_SIMPLE = "EngineDefaults/Textures/concrete_simple.png"
        const val METAL = "EngineDefaults/Textures/metal.png"
        const val WOOD_BROWN = "EngineDefaults/Textures/wood_brown.png"
        const val WOOD_LIGHT = "EngineDefaults/Textures/wood_light.png"
        const val WOOD_TAN = "EngineDefaults/Textures/wood_tan.png"
        const val WOOD_DARK = "EngineDefaults/Textures/wood_dark.png"

        val bundledAssets = listOf(
            // Characters
            JAMES to Models.JAMES,
            // Character animations
            IDLE_0 to Animations.IDLE_0,
            IDLE_1 to Animations.IDLE_1,
            IDLE_PHONE to Animations.IDLE_PHONE,
            JUMP to Animations.JUMP,
            FALLING to Animations.FALLING,
            FALLING_IDLE to Animations.FALLING_IDLE,
            LANDING to Animations.LANDING,
            WALKING to Animations.WALKING,
            RUNNING to Animations.RUNNING,
            LEFT_TURN to Animations.LEFT_TURN,
            LEFT_TURN_90 to Animations.LEFT_TURN_90,
            LEFT_STRAFE to Animations.LEFT_STRAFE,
            LEFT_STRAFE_WALKING to Animations.LEFT_STRAFE_WALKING,
            RIGHT_TURN to Animations.RIGHT_TURN,
            RIGHT_TURN_90 to Animations.RIGHT_TURN_90,
            RIGHT_STRAFE to Animations.RIGHT_STRAFE,
            RIGHT_STRAFE_WALKING to Animations.RIGHT_STRAFE_WALKING,
            // Models
            SKATEBOARD_GLB to Models.SKATEBOARD_GLB,
            CUBE to Models.CUBE,
            RAIL to Models.RAIL,
            LEDGE to Models.LEDGE,
            KICKER to Models.KICKER,
            MANUAL_PAD to Models.MANUAL_PAD,
            BANK to Models.BANK,
            QUARTER_PIPE to Models.QUARTER_PIPE,
            // Textures
            ASPHALT to Textures.ASPHALT,
            CONCRETE_SIMPLE to Textures.CONCRETE_SIMPLE,
            METAL to Textures.METAL,
            WOOD_BROWN to Textures.WOOD_BROWN,
            WOOD_LIGHT to Textures.WOOD_LIGHT,
            WOOD_TAN to Textures.WOOD_TAN,
            WOOD_DARK to Textures.WOOD_DARK,
            // Game
            "..\\builds\\skate-game.jar" to "build\\libs\\skate-game.jar",
            "\\Textures\\app_icon.png" to Textures.APP_ICON,
        )
    }
}
