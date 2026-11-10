package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.animation.graph.AnimParameters
import com.pafoid.skate.engine.animation.graph.AnimationControllerAsset
import com.pafoid.skate.engine.animation.ik.IkRig
import com.pafoid.skate.engine.animation.procedural.HumanoidPoseBuilder
import com.pafoid.skate.engine.animation.graph.AnimationStateMachine
import com.pafoid.skate.engine.animation.graph.AnimatorMaskDef
import com.pafoid.skate.engine.animation.graph.BoneMask
import com.pafoid.skate.engine.animation.graph.ClipMotion
import com.pafoid.skate.engine.animation.graph.Pose
import com.pafoid.skate.engine.animation.graph.FittedMotion
import com.pafoid.skate.engine.animation.graph.Motion
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.BoneNameMapper
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.getComponent
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import com.pafoid.skate.engine.assets.EngineDefaultResolver
import com.pafoid.skate.engine.assets.EngineDefaults
import java.io.File

@Serializable
data class Animator(
    val animations: MutableList<Animation> = mutableListOf(),
    @Transient var currentAnimation: Animation? = null,
    @Transient var previousAnimation: Animation? = null,
    var currentTime: Float = 0f,
    var isPlaying: Boolean = false,
    var blendTime: Float = 0f,
    var blendDuration: Float = 0.2f,
    var previousTime: Float = 0f
) : Component() {

    /** Optional state machine; when set, it drives the pose instead of the single-clip playback. */
    @Transient
    var stateMachine: AnimationStateMachine? = null

    /** Path to an [AnimationControllerAsset] JSON file. Saved with the scene; the state machine is rebuilt from it on load. */
    var controllerPath: String? = null

    /** Bones the controller drives; the rest keep the base pose (see [AnimatorMaskDef]). Saved with the scene. */
    var mask: AnimatorMaskDef = AnimatorMaskDef()

    @Transient
    private var maskCache: Triple<AnimatorMaskDef, Skeleton, BoneMask?>? = null

    /** The resolved [mask] for [skeleton] (cached), or null when the controller drives every bone. */
    fun boneMask(skeleton: Skeleton): BoneMask? {
        maskCache?.let { (def, sk, m) -> if (def == mask && sk === skeleton) return m }
        return mask.build(skeleton).also { maskCache = Triple(mask, skeleton, it) }
    }

    /**
     * Base pose under a partial mask: the selected clip advanced by [dt] while [isPlaying], else the bind pose.
     * Pure apart from advancing [currentTime].
     */
    fun evaluateBasePose(skeleton: Skeleton, dt: Float, out: Pose) {
        val clip = currentAnimation
        if (clip != null && isPlaying) {
            currentTime += dt
            clip.update(currentTime, skeleton)
            for (bone in skeleton.getAllBones()) if (bone.index < out.boneCount) out.setBone(bone.index, bone.localTransform)
        } else {
            for (bone in skeleton.getAllBones()) if (bone.index < out.boneCount) out.setBone(bone.index, bone.bindLocalTransform)
        }
    }

    /** Parameters of the active state machine (gameplay writes floats/bools/triggers here), or null without a controller. */
    val parameters: AnimParameters?
        get() = stateMachine?.parameters

    /** Foot/hand/look-at IK applied after the pose is evaluated. */
    @Transient
    val ik: IkRig = IkRig()

    /** The bool parameter set by the last [play] while a controller is active. */
    @Transient
    var requestedState: String? = null
        private set

    /**
     * Builds a runtime state machine from [asset], resolving motions by name from [animations] (clips),
     * [extraMotions] and the asset's procedural [AnimationControllerAsset.poses] (a real clip with the same name wins).
     */
    fun useController(
        asset: AnimationControllerAsset,
        skeleton: Skeleton,
        extraMotions: Map<String, Motion> = emptyMap(),
    ): AnimationStateMachine {
        val clips = animations.associate { clip ->
            val motion: Motion = ClipMotion.of(clip, skeleton)
            clip.name to (asset.clipFits[clip.name]?.let { FittedMotion.of(motion, it, skeleton) } ?: motion)
        } + extraMotions
        val missingPoses = asset.poses.filterKeys { it !in clips }
        val poses = if (missingPoses.isEmpty()) emptyMap() else HumanoidPoseBuilder(skeleton).let { builder ->
            missingPoses.mapValues { (name, def) -> def.build(name, builder) as Motion }
        }
        val sources = clips + poses
        val trees = asset.motions.mapValues { (_, def) -> def.build { sources[it] } }
        val motions = sources + trees
        requestedState = null
        return AnimationStateMachine(asset, motions, skeleton.boneCount).also { stateMachine = it }
    }

    /**
     * Loads [controllerPath] and builds the state machine from the clips in [animations]. Problems are logged and
     * leave no state machine (so the legacy single-clip playback still works). Returns true when a controller is active.
     * [read] loads project-relative or packed controllers (`AssetsManager.readText`, AST-09); the file system and
     * bundled resources are tried after it.
     */
    fun resolveController(
        logger: LoggerService,
        skeleton: Skeleton? = gameObject?.getComponent<SkeletonComponent>()?.pose?.skeleton,
        read: (String) -> String? = { null },
    ): Boolean {
        val path = controllerPath ?: return false
        stateMachine = null
        skeleton ?: return false.also { logger.log("Controller '$path': no skeleton on this object", LogLevel.ERROR) }
        val text = read(path) ?: readController(path)
        if (text == null) {
            logger.log("Controller file not found: $path", LogLevel.ERROR)
            return false
        }
        return try {
            val asset = AnimationControllerAsset.fromJson(text)
            val problems = asset.issues(animations.map { it.name }.toSet())
            if (problems.isNotEmpty()) {
                problems.forEach { logger.log("Controller '$path': $it", LogLevel.ERROR) }
                false
            } else {
                useController(asset, skeleton)
                true
            }
        } catch (e: Exception) {
            logger.log("Failed to load controller '$path': ${e.message}", LogLevel.ERROR)
            false
        }
    }

    /**
     * A project file at [path], else a bundled classpath resource. An `engine:` reference (GPL-26,
     * `engine:Animations/skater.controller.json`) reads the engine's own file; its project override is read through
     * the `read` of [resolveController] (the scene's asset paths).
     */
    private fun readController(path: String): String? {
        if (EngineDefaults.isReference(path)) return engineDefaults.resolve(path)?.takeIf { it.isFile }?.readText()
        val file = File(path)
        if (file.isFile) return file.readText()
        return Animator::class.java.classLoader.getResourceAsStream(path.replace('\\', '/').trimStart('/'))
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    /** Points this animator at a controller file and builds it now. */
    fun loadController(path: String, logger: LoggerService): Boolean {
        controllerPath = path
        return resolveController(logger)
    }

    /** Drops the controller; single-clip playback takes over again. */
    fun clearController() {
        controllerPath = null
        stateMachine = null
    }

    val normalizedTime: Float
        get() {
            val anim = currentAnimation ?: return 0f
            return (currentTime % anim.duration) / anim.duration
        }

    fun addAnimation(animation: Animation): Boolean {
        val skeleton = gameObject?.getComponent<SkeletonComponent>()?.pose?.skeleton ?: return false
        val isValid = validateSkeletonCompatibility(skeleton, animation)
        val alreadyHasAnimation = animations.any { it.name == animation.name }

        if (!isValid || alreadyHasAnimation) return false
        animations.add(animation)
        return true
    }

    /**
     * Rebuilds the animations list from serialized file paths, read from disk or from the exported game's pack.
     * Called by SceneSerializer after scene deserialization.
     */
    fun resolveAnimationsFromPaths(assetsManager: AssetsManager, logger: LoggerService) {
        val animationPaths = animations.map { it.path }
        animations.clear()
        currentAnimation = null

        for (path in animationPaths) {
            // AssetPaths.localPath covers every form: absolute (editor), project-relative (AST-09) and packed clips of
            // an exported game (extracted from the pack). A clip found nowhere is dropped with a warning.
            if (!File(path).isFile && !File(assetsManager.paths.localPath(path)).isFile) {
                logger.log("Animation clip not found, dropped: $path", LogLevel.WARN)
                continue
            }
            try {
                animations.add(assetsManager.loadAnimationSync(path))
            } catch (e: Exception) {
                logger.log("Failed to load animation from path: $path - ${e.message}", LogLevel.ERROR)
            }
        }

        if (animations.isNotEmpty()) {
            currentAnimation = animations.first()
        }
    }

    /**
     * Starts playing a new animation with an optional [blend] time for smooth transitions.
     *
     * @param animation The animation to play.
     * @param blend Transition duration in seconds.
     */
    fun play(animation: Animation, blend: Float = 0.2f) {
        if (currentAnimation == animation) return

        previousAnimation = currentAnimation
        previousTime = currentTime
        blendTime = blend
        blendDuration = blend

        currentAnimation = animation
        currentTime = 0f
        isPlaying = true
    }

    /**
     * Plays the clip whose name contains [name]. With a controller active, [name] is a request instead: its bool
     * parameter is set (and the previous request's cleared) so the controller's transitions decide how to get there.
     */
    fun play(name: String, blend: Float = 1f) {
        val machine = stateMachine
        if (machine != null) {
            requestedState?.let { machine.parameters.setBool(it, false) }
            machine.parameters.setBool(name, true)
            requestedState = name
            return
        }
        val anim = animations.find { it.name.contains(name, ignoreCase = true) } ?: return
        play(anim, blend)
    }

    override fun update(dt: Float) {

    }

    fun validateSkeletonCompatibility(skeleton: Skeleton?, animation: Animation): Boolean {
        skeleton ?: return false
        // Check if all animation channels target bones that exist in the skeleton
        val missingBones = mutableListOf<String>()
        for (channel in animation.channels) {
            val boneName = BoneNameMapper.map(channel.targetNodeName)
            val bone = skeleton.getBoneByName(boneName)
            if (bone == null) {
                missingBones.add(boneName)
            }
        }

        if (missingBones.isNotEmpty()) {
            // Check if the missing bones are critical or just extra detail bones
            val criticalBones = missingBones.filter { isCriticalBone(it) }

            if (criticalBones.isNotEmpty()) {
                return false
            }
        }

        // Check if the skeleton has significantly more bones than the animation targets
        //val skeletonBoneNames = skeleton.getAllBones().map { it.name }.toSet()
        //val animationBoneNames = animation.channels.map { BoneNameMapper.map(it.targetNodeName) }.toSet()
        //val extraSkeletonBones = skeletonBoneNames - animationBoneNames

        // Only warn if there are many extra bones (indicating a major topology mismatch)
        //if (extraSkeletonBones.size > skeletonBoneNames.size * 0.5) { // More than 50% of bones unused
        //Skeleton has many bones not targeted by animation. This may indicate a topology mismatch.
        //}

        return true
    }

    private fun isCriticalBone(boneName: String): Boolean {
        // Define critical bones that are essential for basic animation
        val criticalBonePatterns = listOf(
            "hip", "hips", "pelvis", "spine", "chest", "neck", "head",
            "leftupperarm", "rightupperarm", "leftlowerarm", "rightlowerarm",
            "lefthand", "righthand", "leftthigh", "rightthigh",
            "leftcalf", "rightcalf", "leftfoot", "rightfoot"
        )

        val lowerName = boneName.lowercase()
        return criticalBonePatterns.any { lowerName.contains(it) }
    }
}

/** Resolves `engine:` controller references outside a project (GPL-26). */
private val engineDefaults by lazy { EngineDefaultResolver() }
