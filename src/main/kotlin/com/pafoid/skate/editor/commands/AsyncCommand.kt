package com.pafoid.skate.editor.commands

import kotlinx.coroutines.Job

interface AsyncCommand : Command {
    override fun getCategory(): CommandCategory = CommandCategory.ASYNC
    fun getCompletionJob(): Job?
    fun didCompleteSuccessfully(): Boolean
    fun shouldPushToHistoryOnSuccess(): Boolean = true
}