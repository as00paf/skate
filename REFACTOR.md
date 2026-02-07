REFRACTOR PLAN — ANIMATION + MODEL ARCHITECTURE

GOALS
- Separate skeleton asset data from runtime animation pose
- Remove matrix palette from Skeleton
- Move skin matrix generation to renderer
- Introduce model abstraction hierarchy:
  Model → TexturedModel → CharacterModel
- Prepare engine for blending, IK, and physics animation


========================================
STEP 1 — CREATE NEW PACKAGE STRUCTURE
========================================

Create folders:

engine/render/model
engine/render/material
engine/animation/skeleton
engine/animation/runtime


========================================
STEP 2 — CREATE MODEL ABSTRACTION
========================================

Create interface Model:

----------------------------------------
interface Model {
val meshData: MeshData
}
----------------------------------------


Create Material abstraction:

----------------------------------------
interface Material {
fun bind()
}
----------------------------------------


Create TexturedModel:

----------------------------------------
class TexturedModel(
override val meshData: MeshData,
val material: Material
) : Model
----------------------------------------


Create CharacterModel:

CharacterModel represents a skinned model with animation capability.

----------------------------------------
class CharacterModel(
override val meshData: MeshData,
val material: Material,
val skeletonAsset: SkeletonAsset
) : Model
----------------------------------------


IMPORTANT:
Remove animation runtime state from CharacterModel.
CharacterModel must ONLY contain static asset data.



========================================
STEP 3 — CREATE SKELETON ASSET (STATIC DATA)
========================================

Create SkeletonAsset:

----------------------------------------
class SkeletonAsset(
val parentIndices: IntArray,
val jointNames: Array<String>,
val bindLocalTransforms: Array<Matrix4f>,
val inverseBindMatrices: Array<Matrix4f>
)
----------------------------------------

Rules:
- Immutable
- Loaded from GLB or model importer
- NEVER modified at runtime



========================================
STEP 4 — CREATE RUNTIME SKELETON POSE
========================================

Create SkeletonPose:

----------------------------------------
class SkeletonPose(
val asset: SkeletonAsset
) {
val localTransforms =
Array(asset.parentIndices.size) { Matrix4f() }

    val globalTransforms =
        Array(asset.parentIndices.size) { Matrix4f() }
}
----------------------------------------

Rules:
- Animator modifies localTransforms
- globalTransforms are computed every frame



========================================
STEP 5 — CREATE SKELETON MATH UTILITIES
========================================

Create SkeletonMath.kt with functions:


----------------------------------------
fun computeGlobalTransforms(pose: SkeletonPose) {
val parents = pose.asset.parentIndices

    for (i in parents.indices) {
        val parent = parents[i]

        if (parent >= 0) {
            pose.globalTransforms[i]
                .set(pose.globalTransforms[parent])
                .mul(pose.localTransforms[i])
        } else {
            pose.globalTransforms[i]
                .set(pose.localTransforms[i])
        }
    }
}
----------------------------------------


----------------------------------------
fun buildSkinMatrices(
pose: SkeletonPose,
palette: Array<Matrix4f>
) {
val inverseBind = pose.asset.inverseBindMatrices

    for (i in palette.indices) {
        palette[i]
            .set(pose.globalTransforms[i])
            .mul(inverseBind[i])
    }
}
----------------------------------------


----------------------------------------
fun resetToBindPose(pose: SkeletonPose) {
val bind = pose.asset.bindLocalTransforms

    for (i in bind.indices) {
        pose.localTransforms[i].set(bind[i])
    }
}
----------------------------------------



========================================
STEP 6 — REMOVE MATRIX PALETTE FROM SKELETON
========================================

Delete:
- matrixPalette from Skeleton class
- Any stored skin matrices inside skeleton or joints

Skin matrices must be GENERATED each frame.



========================================
STEP 7 — REFACTOR ANIMATOR
========================================

Animator MUST:

- Write ONLY to SkeletonPose.localTransforms
- Never store matrices
- Never compute palette

Animator update flow:

----------------------------------------
Animator.update()
computeGlobalTransforms(pose)
----------------------------------------



========================================
STEP 8 — REFACTOR RENDERER
========================================

Renderer MUST:

- Allocate temporary palette array
- Build palette every frame
- Upload palette to shader

Renderer flow:

----------------------------------------
val palette = Array(jointCount) { Matrix4f() }

buildSkinMatrices(pose, palette)

upload palette to GPU
----------------------------------------


Renderer MUST NOT:
- Own skeleton data
- Modify skeleton transforms



========================================
STEP 9 — GAMEOBJECT ARCHITECTURE
========================================

Final expected structure:

GameObject
├ Animator
├ SkeletonPose
└ RenderComponent
└ CharacterModel
└ Material
└ MeshData



========================================
STEP 10 — MIGRATE EXISTING CHARACTERMODEL
========================================

Old CharacterModel currently mixes:

- Mesh
- Textures
- Skeleton
- Runtime animation state

Refactor so:

CharacterModel contains ONLY:
- Mesh
- Material
- SkeletonAsset

Runtime pose must move to GameObject or Animator component.



========================================
STEP 11 — OPTIONAL BUT RECOMMENDED
========================================

Remove Joint object tree to replace hierarchy traversal with:

parentIndices array

Reason:
- Better performance
- Easier blending
- Cleaner math



========================================
STEP 12 — VALIDATION CHECKLIST
========================================

After refactor, verify:

- Animation updates bone transforms
- Mesh deforms correctly
- Reset to bind pose works
- Renderer receives updated palette every frame
- No animation state stored in CharacterModel



========================================
END STATE REQUIREMENTS
========================================

SkeletonAsset:
static rig data

SkeletonPose:
runtime animation state

Animator:
writes pose

Renderer:
builds palette

CharacterModel:
static skinned model asset

Model hierarchy:
Model → TexturedModel → CharacterModel