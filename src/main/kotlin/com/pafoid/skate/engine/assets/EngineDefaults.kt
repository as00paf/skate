package com.pafoid.skate.engine.assets

import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * The engine default assets (AST-26 §4.2), referenced as `engine:<logical path>` (`engine:Models/cube.obj`). The
 * logical path is the file's path under the engine repo's `assets/` folder, which uses the project's typed layout, so
 * a copied default lands at `Assets/<logical path>` in a project. There is no `EngineDefaults` folder anywhere.
 *
 * A reference resolves (see [EngineDefaultResolver]) to a project copy whose `.meta` names it in [META_SOURCE] (an
 * override), else to `<engine root>/assets/<logical path>`, else to the classpath copy ([classpathResource]). In an
 * exported game the pack holds the entry under the reference itself.
 */
object EngineDefaults {
    const val PREFIX = "engine:"

    /** The engine repo folder holding the defaults, relative to the engine root (the editor's working directory). */
    const val REPO_ASSETS_DIR = "assets"

    /** `.meta` settings keys of a project copy (AST-26 §4.5): its logical path and the engine file's hash at copy time. */
    const val META_SOURCE = "engine.source"
    const val META_SHA256 = "engine.sha256"

    object Models {
        const val CUBE = "engine:Models/cube.obj"
        const val SPHERE = "engine:Models/sphere.obj"
        const val CYLINDER = "engine:Models/cylinder.obj"
        const val RAIL = "engine:Models/rail.obj"
        const val LEDGE = "engine:Models/ledge.obj"
        const val KICKER = "engine:Models/kicker.obj"
        const val MANUAL_PAD = "engine:Models/manual_pad.obj"
        const val BANK = "engine:Models/bank.obj"
        const val QUARTER_PIPE = "engine:Models/quarter_pipe.obj"
        const val HUBBA = "engine:Models/hubba.obj"
        const val GRIND_BOX = "engine:Models/grind_box.obj"
        const val FUNBOX = "engine:Models/funbox.obj"
        const val HANDRAIL = "engine:Models/handrail.obj"
        const val KINKED_RAIL = "engine:Models/kinked_rail.obj"
        const val STAIRS_3 = "engine:Models/stairs_3.obj"
        const val STAIRS_5 = "engine:Models/stairs_5.obj"
        const val EURO_GAP = "engine:Models/euro_gap.obj"
        const val HALF_PIPE = "engine:Models/half_pipe.obj"
        const val BOWL = "engine:Models/bowl.obj"
        const val BENCH = "engine:Models/bench.obj"
        const val SPAWN_MARKER = "engine:Models/spawn_marker.obj"
        const val SKATEBOARD = "engine:Models/skateboard.obj"
        const val JAMES = "engine:Models/james.glb"

        val ALL = listOf(
            CUBE, SPHERE, CYLINDER, RAIL, LEDGE, KICKER, MANUAL_PAD, BANK, QUARTER_PIPE, HUBBA, GRIND_BOX, FUNBOX,
            HANDRAIL, KINKED_RAIL, STAIRS_3, STAIRS_5, EURO_GAP, HALF_PIPE, BOWL, BENCH, SPAWN_MARKER, SKATEBOARD, JAMES,
        )
    }

    /** The skater's clips (Mixamo, for [Models.JAMES]). */
    object Animations {
        const val IDLE_0 = "engine:Animations/idle_0.fbx"
        const val IDLE_1 = "engine:Animations/idle_1.fbx"
        const val IDLE_PHONE = "engine:Animations/idle phone.fbx"
        const val JUMP = "engine:Animations/jump.fbx"
        const val FALLING = "engine:Animations/falling to roll.fbx"
        const val FALLING_IDLE = "engine:Animations/falling idle.fbx"
        const val LANDING = "engine:Animations/hard landing.fbx"
        const val WALKING = "engine:Animations/walking.fbx"
        const val RUNNING = "engine:Animations/running.fbx"
        const val LEFT_TURN = "engine:Animations/left turn.fbx"
        const val LEFT_TURN_90 = "engine:Animations/left turn 90.fbx"
        const val LEFT_STRAFE = "engine:Animations/left strafe.fbx"
        const val LEFT_STRAFE_WALKING = "engine:Animations/left strafe walking.fbx"
        const val RIGHT_TURN = "engine:Animations/right turn.fbx"
        const val RIGHT_TURN_90 = "engine:Animations/right turn 90.fbx"
        const val RIGHT_STRAFE = "engine:Animations/right strafe.fbx"
        const val RIGHT_STRAFE_WALKING = "engine:Animations/right strafe walking.fbx"
        const val SKATE_RIDING = "engine:Animations/skate riding.fbx"

        val ALL = listOf(
            IDLE_0, IDLE_1, IDLE_PHONE, JUMP, FALLING, FALLING_IDLE, LANDING, WALKING, RUNNING, LEFT_TURN, LEFT_TURN_90,
            LEFT_STRAFE, LEFT_STRAFE_WALKING, RIGHT_TURN, RIGHT_TURN_90, RIGHT_STRAFE, RIGHT_STRAFE_WALKING, SKATE_RIDING,
        )
    }

    object Textures {
        const val ASPHALT = "engine:Textures/asphalt.png"
        const val CONCRETE_SIMPLE = "engine:Textures/concrete_simple.png"
        const val METAL = "engine:Textures/metal.png"
        const val WOOD_BROWN = "engine:Textures/wood_brown.png"
        const val WOOD_LIGHT = "engine:Textures/wood_light.png"
        const val WOOD_TAN = "engine:Textures/wood_tan.png"
        const val WOOD_DARK = "engine:Textures/wood_dark.png"
        const val SKY_HDRI = "engine:Textures/sky_hdri.png"
        /** The default project and window icon; also on the classpath (`/app_icon.png`). */
        const val APP_ICON = "engine:Textures/app_icon.png"

        val ALL = listOf(ASPHALT, CONCRETE_SIMPLE, METAL, WOOD_BROWN, WOOD_LIGHT, WOOD_TAN, WOOD_DARK, SKY_HDRI, APP_ICON)
    }

    object Sounds {
        /** Background music for the default MainScene (by the project author). */
        const val MUSIC = "engine:Sounds/Music/aquarela do brasil.mp3"

        val ALL = listOf(MUSIC)
    }

    /**
     * Engine shaders (AST-26 §5.1). The **basic** ones are copied into every project's `Assets/Shaders/` in both copy
     * modes and can be overridden; the **internal** ones only live in the jar and are not in the catalog.
     */
    object Shaders {
        val BASIC = listOf(
            "shader_3d_default", "shader_3d_texture", "shader_2d_batch", "shadow", "skybox", "skydome", "particles",
            "fog", "post_bloom", "post_tonemap", "post_gamma", "post_fxaa", "post_ssao",
        ).map { "${PREFIX}Shaders/$it.glsl" }

        val INTERNAL = listOf("editor_grid", "picking", "shader_3d_picking", "debugLine2D", "splash", "ibl_capture")
            .map { "${PREFIX}Shaders/$it.glsl" }
    }

    /** Every engine default a project can copy or reference, in copy order. */
    val CATALOG: List<String> = Models.ALL + Animations.ALL + Textures.ALL + Sounds.ALL + Shaders.BASIC

    fun isReference(path: String?): Boolean = path != null && path.startsWith(PREFIX)

    /**
     * The logical path of [reference] (`Models/cube.obj`), or null when it is not a well-formed engine reference:
     * the `engine:` prefix, then a relative `/`-separated path with no `..`, no backslash and no drive.
     */
    fun logicalPath(reference: String): String? {
        if (!isReference(reference)) return null
        val logical = reference.removePrefix(PREFIX)
        if (logical.isBlank() || logical.startsWith("/") || '\\' in logical || ':' in logical) return null
        if (logical.split('/').any { it.isEmpty() || it == "." || it == ".." }) return null
        return logical
    }

    fun reference(logicalPath: String): String = PREFIX + logicalPath.replace('\\', '/').trimStart('/')

    fun isBasicShader(reference: String): Boolean = reference in Shaders.BASIC

    /** The jar resource holding a copy of [logicalPath] (shaders and the app icon), or null. */
    fun classpathResource(logicalPath: String): String? = when {
        logicalPath.startsWith("Shaders/") -> "/shaders/" + logicalPath.removePrefix("Shaders/")
        logicalPath == Textures.APP_ICON.removePrefix(PREFIX) -> "/app_icon.png"
        else -> null
    }

    /**
     * A GUID derived from the reference (name-based UUID), so GUID-based APIs accept engine defaults whether they are
     * copied or not (AST-26 §4.2). Stable across runs and machines.
     */
    fun idOf(reference: String): AssetId =
        AssetId(UUID.nameUUIDFromBytes(reference.toByteArray(StandardCharsets.UTF_8)).toString().replace("-", ""))

    private val byId: Map<AssetId, String> by lazy { (CATALOG + Shaders.INTERNAL).associateBy(::idOf) }

    /** The catalog reference with the derived GUID [id], or null. */
    fun referenceOf(id: AssetId): String? = byId[id]
}
