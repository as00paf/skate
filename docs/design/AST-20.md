# AST-20: Classify FBX files as model or animation by content

## Problem
`.fbx` files were always classed as `FileType.ANIMATION`, so FBX models show up in the Animations tab and store packs with FBX models cannot be imported as models.

## Solution
FBX files are now classified by content:
- If a file contains only animation data → `FileType.ANIMATION`
- If a file contains mesh data (or both) → `FileType.MODEL_3D`

## Implementation
- Uses `FbxClassifier` to inspect FBX content
- `AssetDiscovery.kt` implements the correct tab display logic  
- Content inspection via Assimp import or binary scanning
- Falls back to animation classification for unreadable files

## Test Coverage
- `model.fbx` (mesh data) → MODEL
- `animation-only.fbx` (animation data only) → ANIMATION  
- `model-with-clips.fbx` (both) → MODEL