package com.pafoid.skate.editor.data

/**
 * Data class representing an editor action.
 *
 * @property actionId Unique identifier for the action
 * @property displayName Human-readable name shown in search results
 * @property keywords List of keywords for matching search queries
 * @property description Brief description of what the action does
 * @property icon Icon identifier for visual representation
 * @property execute Lambda function that executes the action
 */
data class EditorAction(
    val actionId: String,
    val displayName: String,
    val keywords: List<String>,
    val description: String,
    val icon: String,
    val execute: () -> Unit = { /* Default no-op, should be overridden */ }
)