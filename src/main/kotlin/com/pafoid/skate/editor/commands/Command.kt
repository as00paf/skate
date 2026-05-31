package com.pafoid.skate.editor.commands

interface Command {
    fun execute()
    fun undo()
    fun getDisplayName(): String
    fun getTargetName(): String?
    fun getCategory(): CommandCategory = CommandCategory.UNDOABLE
}

