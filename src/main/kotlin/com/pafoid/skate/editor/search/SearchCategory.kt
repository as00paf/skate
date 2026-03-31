package com.pafoid.skate.editor.search

/**
 * Categories of searchable items in the editor.
 *
 * Each category represents a distinct type of resource that can be searched
 * and navigated to within the editor environment.
 */
enum class SearchCategory(val displayName: String) {
    GAMEOBJECT("GameObjects"),
    ASSET_TEXTURE("Textures"),
    ASSET_MODEL("Models"),
    ASSET_ANIMATION("Animations"),
    ASSET_SOUND("Sounds"),
    ASSET_PREFAB("Prefabs"),
    COMPONENT("Components"),
    ACTION("Actions")
}