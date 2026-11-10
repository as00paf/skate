package com.pafoid.skate.editor.animation.mocap

import com.pafoid.skate.engine.animation.edit.Reduction
import com.pafoid.skate.engine.animation.edit.ReductionTolerance
import com.pafoid.skate.engine.animation.edit.SkAnimFormat
import com.pafoid.skate.engine.animation.edit.SkClip
import com.pafoid.skate.engine.animation.mocap.Bvh
import com.pafoid.skate.engine.animation.mocap.LandmarkSequence
import com.pafoid.skate.engine.animation.mocap.PoseSolver
import com.pafoid.skate.engine.animation.mocap.SolveOptions
import com.pafoid.skate.engine.animation.retarget.Retargeter
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton

/** A clip ready to save, plus what the UI reports about it. */
class MocapClip(val clip: SkClip, val coverage: Float)

/**
 * Landmarks or a mocap file -> a clip on the project's rig (ANI-17): solve onto the reference rig, retarget with
 * rest-pose compensation (ANI-16), reduce keys. No files are written here.
 */
object MocapImport {
    private val tolerance = ReductionTolerance(translationMetres = 0.002f, rotationDeg = 0.5f, scale = 0.001f)

    fun fromLandmarks(landmarks: LandmarkSequence, name: String, target: Skeleton, options: SolveOptions): MocapClip {
        val solved = PoseSolver.solve(landmarks, name, options)
        val source = SkAnimFormat.toAnimation(solved.clip, "$name.${SkClip.EXTENSION}")
        return finish(Retargeter.retarget(source, solved.skeleton, target), source, target, solved.clip.fps)
    }

    /** BVH from any tool: read, retarget onto [target]. */
    fun fromBvh(text: String, name: String, target: Skeleton): MocapClip {
        val motion = Bvh.read(text, name)
        val source = SkAnimFormat.toAnimation(motion.clip, "$name.bvh")
        return finish(Retargeter.retarget(source, motion.skeleton, target), source, target, motion.clip.fps)
    }

    /**
     * FBX/glTF from any tool. With the file's own skeleton the retarget compensates rest poses; animation-only files
     * (no skin, so no skeleton) fall back to matching bone names.
     */
    fun fromAnimationFile(animation: Animation, sourceSkeleton: Skeleton?, target: Skeleton, fps: Float = 30f): MocapClip {
        val retargeted = if (sourceSkeleton != null) Retargeter.retarget(animation, sourceSkeleton, target) else Retargeter.retarget(animation, target)
        return finish(retargeted, animation, target, fps)
    }

    private fun finish(retargeted: Animation, source: Animation, target: Skeleton, fps: Float): MocapClip {
        val clip = Reduction.reduce(SkAnimFormat.fromAnimation(retargeted, fps = fps, loop = false), tolerance)
            .copy(humanoid = true)
        return MocapClip(clip, Retargeter.coverage(source, target))
    }
}
