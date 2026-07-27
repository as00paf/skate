package com.pafoid.skate.engine.assets

object Assets {
    object Shaders {
        const val SPLASH = "assets/shaders/splash.glsl"
        const val SHADER_3D_DEFAULT = "assets/shaders/shader_3d_default.glsl"
        const val SHADER_2D_BATCH = "assets/shaders/shader_2d_batch.glsl"
        const val PICKING = "assets/shaders/picking.glsl"
        const val PICKING_3D = "assets/shaders/shader_3d_picking.glsl"
        const val SKYBOX = "assets/shaders/skybox.glsl"
        const val DEBUG = "assets/shaders/debugLine2D.glsl"
        const val SKY_DOME = "assets/shaders/skydome.glsl"
        const val SHADOW = "assets/shaders/shadow.glsl"
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
        const val FONTS_FILE = "assets/fonts/Font Awesome 7 Free-Solid-900.otf"
    }
    object Files {
        const val IMGUI = "imgui.ini"
        const val ENGINE_SETTINGS_FILE = "engine_settings.json"
        const val PROJECT_SETTINGS_FILE = "project_settings.json"
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
}
