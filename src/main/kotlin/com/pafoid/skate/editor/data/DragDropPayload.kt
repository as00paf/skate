package com.pafoid.skate.editor.data

import com.pafoid.skate.editor.windows.assetBrowser.PrefabType
import com.pafoid.skate.game.prefabs.MaterialType

/**
 * Centralized registry for all drag and drop payload types.
 * 
 * This sealed class provides type-safe payload handling and prevents
 * string-based payload type errors.
 */
sealed class DragDropPayload {
    
    /**
     * Prefab payload for spawning obstacles and objects in the scene.
     * @param type The type of prefab
     * @param data The prefab data including name, model path, and material
     */
    data class Prefab(
        val type: PrefabType,
        val data: PrefabData
    ) : DragDropPayload()
    
    /**
     * Texture payload for applying textures to objects or creating textured planes.
     * @param filePath The path to the texture file
     */
    data class Texture(
        val filePath: String
    ) : DragDropPayload()
    
    /**
     * Sound payload for adding audio to objects.
     * @param filePath The path to the sound file
     */
    data class Sound(
        val filePath: String
    ) : DragDropPayload()
    
    /**
     * Animation payload for applying animations to objects with Animator.
     * @param filePath The path to the animation file
     */
    data class Animation(
        val filePath: String
    ) : DragDropPayload()
    
    /**
     * GameObject payload for reparenting in hierarchy.
     * @param uid The unique ID of the GameObject
     */
    data class GameObject(
        val uid: Int
    ) : DragDropPayload()
    
    /**
     * Companion object containing all payload type string constants.
     * These constants are used with ImGui's drag and drop system.
     */
    companion object {
        // Prefab payload types
        const val TYPE_PREFAB_RAIL = "PREFAB_RAIL"
        const val TYPE_PREFAB_LEDGE = "PREFAB_LEDGE"
        const val TYPE_PREFAB_KICKER = "PREFAB_KICKER"
        const val TYPE_PREFAB_MANUAL_PAD = "PREFAB_MANUAL_PAD"
        const val TYPE_PREFAB_BANK = "PREFAB_BANK"
        const val TYPE_PREFAB_QUARTER_PIPE = "PREFAB_QUARTER_PIPE"
        const val TYPE_PREFAB_SKATEBOARD = "PREFAB_SKATEBOARD"
        const val TYPE_PREFAB_SKATER = "PREFAB_SKATER"
        
        // Asset payload types
        const val TYPE_TEXTURE = "TEXTURE"
        const val TYPE_SOUND = "SOUND"
        const val TYPE_ANIMATION = "ANIMATION"
        
        // Editor payload types
        const val TYPE_GAMEOBJECT_UID = "GAMEOBJECT_UID"
        
        /**
         * Map of payload type strings to their human-readable names.
         * Useful for debugging and UI display.
         */
        val displayNames = mapOf(
            TYPE_PREFAB_RAIL to "Rail",
            TYPE_PREFAB_LEDGE to "Ledge",
            TYPE_PREFAB_KICKER to "Kicker",
            TYPE_PREFAB_MANUAL_PAD to "Manual Pad",
            TYPE_PREFAB_BANK to "Bank",
            TYPE_PREFAB_QUARTER_PIPE to "Quarter Pipe",
            TYPE_PREFAB_SKATEBOARD to "Skateboard",
            TYPE_PREFAB_SKATER to "Skater",
            TYPE_TEXTURE to "Texture",
            TYPE_SOUND to "Sound",
            TYPE_ANIMATION to "Animation",
            TYPE_GAMEOBJECT_UID to "GameObject"
        )
        
        /**
         * Get the display name for a payload type.
         * @param type The payload type string
         * @return Human-readable name or the type itself if not found
         */
        fun getDisplayName(type: String): String {
            return displayNames[type] ?: type
        }
        
        /**
         * Check if a payload type is a prefab type.
         * @param type The payload type string
         * @return True if the type is a prefab type
         */
        fun isPrefabType(type: String): Boolean {
            return type.startsWith("PREFAB_")
        }
        
        /**
         * Get all prefab payload types.
         * @return List of all prefab payload type strings
         */
        fun getAllPrefabTypes(): List<String> {
            return listOf(
                TYPE_PREFAB_RAIL,
                TYPE_PREFAB_LEDGE,
                TYPE_PREFAB_KICKER,
                TYPE_PREFAB_MANUAL_PAD,
                TYPE_PREFAB_BANK,
                TYPE_PREFAB_QUARTER_PIPE,
                TYPE_PREFAB_SKATEBOARD,
                TYPE_PREFAB_SKATER
            )
        }
        
        /**
         * Get all asset payload types (textures, sounds, animations).
         * @return List of all asset payload type strings
         */
        fun getAllAssetTypes(): List<String> {
            return listOf(TYPE_TEXTURE, TYPE_SOUND, TYPE_ANIMATION)
        }
        
        /**
         * Get all editor payload types (GameObject UID, etc.).
         * @return List of all editor payload type strings
         */
        fun getAllEditorTypes(): List<String> {
            return listOf(TYPE_GAMEOBJECT_UID)
        }
        
        /**
         * Get all registered payload types.
         * @return List of all payload type strings
         */
        fun getAllTypes(): List<String> {
            return getAllPrefabTypes() + getAllAssetTypes() + getAllEditorTypes()
        }
    }
}
