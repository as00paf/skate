package com.pafoid.skate.editor.systems

interface Command {
    fun execute()
    fun undo()
    fun getDisplayName(): String
    fun getTargetName(): String?
}