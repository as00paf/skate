package com.pafoid.skate.editor.search.data

data class EditorAction(
    val actionId: String,
    val displayName: String,
    val keywords: List<String>,
    val description: String,
    val icon: String,
    val execute: () -> Unit = { /* Default no-op, should be overridden */ }
)