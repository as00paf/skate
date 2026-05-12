package com.pafoid.skate.editor.search.data

/**
 * Categories of searchable items in the editor.
 *
 * Each category represents a distinct type of resource that can be searched
 * and navigated to within the editor environment.
 */
enum class SearchCategory(val displayNameKey: String) {
    GAMEOBJECT("search.everywhere.category.gameobject"),
    ASSET_TEXTURE("search.everywhere.category.asset_texture"),
    ASSET_MODEL("search.everywhere.category.asset_model"),
    ASSET_ANIMATION("search.everywhere.category.asset_animation"),
    ASSET_SOUND("search.everywhere.category.asset_sound"),
    ASSET_PREFAB("search.everywhere.category.asset_prefab"),
    COMPONENT("search.everywhere.category.component"),
    ACTION("search.everywhere.category.action")
}