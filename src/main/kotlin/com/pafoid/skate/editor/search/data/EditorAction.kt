package com.pafoid.skate.editor.search.data

data class EditorAction(
    val actionId: String,
    /** String key of the shown name; resolved when searching so it follows the editor language. */
    val displayNameKey: String,
    val keywords: List<String>,
    /** String key of the one-line description. */
    val descriptionKey: String,
    val icon: String,
    val execute: () -> Unit = { /* Default no-op, should be overridden */ }
)