package com.pafoid.skate.engine.assets

object Assets {
    object Shaders {
        const val SPLASH = "/shaders/splash.glsl"
        const val SHADER_3D_DEFAULT = "/shaders/shader_3d_default.glsl"
        const val SHADER_3D_TEXTURE = "/shaders/shader_3d_texture.glsl"
        const val SHADER_2D_BATCH = "/shaders/shader_2d_batch.glsl"
        const val PICKING = "/shaders/picking.glsl"
        const val PICKING_3D = "/shaders/shader_3d_picking.glsl"
        const val SKYBOX = "/shaders/skybox.glsl"
        const val DEBUG = "/shaders/debugLine2D.glsl"
        const val SKY_DOME = "/shaders/skydome.glsl"
        const val SHADOW = "/shaders/shadow.glsl"
    }
    object Textures {
        const val SPLASH = "assets/Textures/splash_screen.png"

        const val CAMERA = "assets/Textures/camera.png"

        const val XBOX_CONTROLLER = "assets/Textures/xbox_controller.png"

        const val WHITE = "assets/Textures/white.png"
        const val ASPHALT = "assets/Textures/asphalt.png"
        const val CONCRETE_SIMPLE = "assets/Textures/concrete_simple.png"

        const val WOOD_BROWN = "assets/Textures/wood_brown.png"
        const val WOOD_LIGHT = "assets/Textures/wood_light.png"
        const val WOOD_TAN = "assets/Textures/wood_tan.png"
        const val WOOD_DARK = "assets/Textures/wood_dark.png"
        const val METAL = "assets/Textures/metal.png"

        const val SKY_HDRI = "assets/Textures/sky_hdri.png"
    }
    object Models {
        const val CUBE = "assets/Models/cube.obj"
        const val SPHERE = "assets/Models/sphere.obj"
        const val CYLINDER = "assets/Models/cylinder.obj"
        const val RAIL = "assets/Models/rail.obj"
        const val LEDGE = "assets/Models/ledge.obj"
        const val KICKER = "assets/Models/kicker.obj"
        const val MANUAL_PAD = "assets/Models/manual_pad.obj"
        const val BANK = "assets/Models/bank.obj"
        const val QUARTER_PIPE = "assets/Models/quarter_pipe.obj"
        const val HUBBA = "assets/Models/hubba.obj"
        const val GRIND_BOX = "assets/Models/grind_box.obj"
        const val FUNBOX = "assets/Models/funbox.obj"
        const val HANDRAIL = "assets/Models/handrail.obj"
        const val KINKED_RAIL = "assets/Models/kinked_rail.obj"
        const val STAIRS_3 = "assets/Models/stairs_3.obj"
        const val STAIRS_5 = "assets/Models/stairs_5.obj"
        const val EURO_GAP = "assets/Models/euro_gap.obj"
        const val HALF_PIPE = "assets/Models/half_pipe.obj"
        const val BOWL = "assets/Models/bowl.obj"
        const val BENCH = "assets/Models/bench.obj"
        const val SPAWN_MARKER = "assets/Models/spawn_marker.obj"
        const val SKATEBOARD = "assets/Models/skateboard.obj"
        const val JAMES = "assets/Models/james.glb"
    }
    object Sounds {
        /** Background music for the default MainScene (by the project author), copied into new projects. */
        const val MUSIC = "assets/Sounds/Music/aquarela do brasil.mp3"
    }
    object Fonts {
        const val FONTS_FILE = "/fonts/Font Awesome 7 Free-Solid-900.otf"
    }
    object Files {
        const val IMGUI = "imgui.ini"
    }
    object Folders {
        const val ANIMATIONS = "assets/Animations"
        const val CHARACTERS = "assets/Models"
        const val TEXTURES = "assets/Textures"
    }
    object Animations {
        const val IDLE_0 = "assets/Animations/idle_0.fbx"
        const val IDLE_1 = "assets/Animations/idle_1.fbx"
        const val IDLE_PHONE = "assets/Animations/idle phone.fbx"

        const val JUMP = "assets/Animations/jump.fbx"
        const val FALLING = "assets/Animations/falling to roll.fbx"
        const val FALLING_IDLE = "assets/Animations/falling idle.fbx"
        const val LANDING = "assets/Animations/hard landing.fbx"
        const val WALKING = "assets/Animations/walking.fbx"
        const val RUNNING = "assets/Animations/running.fbx"

        const val LEFT_TURN = "assets/Animations/left turn.fbx"
        const val LEFT_TURN_90 = "assets/Animations/left turn 90.fbx"
        const val LEFT_STRAFE = "assets/Animations/left strafe.fbx"
        const val LEFT_STRAFE_WALKING = "assets/Animations/left strafe walking.fbx"

        const val RIGHT_TURN = "assets/Animations/right turn.fbx"
        const val RIGHT_TURN_90 = "assets/Animations/right turn 90.fbx"
        const val RIGHT_STRAFE = "assets/Animations/right strafe.fbx"
        const val RIGHT_STRAFE_WALKING = "assets/Animations/right strafe walking.fbx"

        const val SKATE_RIDING = "assets/Animations/skate riding.fbx"
    }
    object Themes {
        /**
         * Editor themes (EDT-07): one `<id>.theme.json` per theme. In the jar under `/editor/themes` (AST-26 NQ1); the
         * editor reads them through `EditorResources`. This is the source folder (dev runs, tests).
         */
        const val DIR = "src/main/resources/editor/themes"
    }

    object Strings {
        /**
         * Editor string tables, one file per area (`<area>.properties` + `<area>_fr.properties`). In the jar under
         * `/editor/strings` (AST-26 NQ1); the editor reads them through `EditorResources`. This is the source folder.
         */
        const val EDITOR_STRINGS = "src/main/resources/editor/strings"
        /** Runtime/game string tables (HUD, showcase); the editor merges them too so the viewport shows game UI text. */
        const val GAME_STRINGS = "assets/Data/strings/game"
    }

    /** Bundled config resources (classpath `configs/`); a file at the same path under the working directory overrides them. */
    /** Engine-default configs (GPL-26): a project copy in `Assets/Data` or `Assets/Animations` overrides them. */
    object Configs {
        const val SKATER_CHARACTER = EngineDefaults.Data.SKATER_CHARACTER
        const val SKATER_CONTROLLER = EngineDefaults.Animations.SKATER_CONTROLLER
    }

    /** Jar resources the editor and the game use directly (the app icon also exists as [EngineDefaults.Textures.APP_ICON]). */
    object Classpath {
        const val APP_ICON = "/app_icon.png"
        const val DEFAULT_TEXTURE = "/textures/default.png"
    }
}
