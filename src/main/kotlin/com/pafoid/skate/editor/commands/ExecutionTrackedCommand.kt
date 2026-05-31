package com.pafoid.skate.editor.commands

interface ExecutionTrackedCommand : Command {
    fun wasSuccessful(): Boolean
    fun getFailureReason(): String? = null
}
