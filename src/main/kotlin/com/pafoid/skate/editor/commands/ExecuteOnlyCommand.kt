package com.pafoid.skate.editor.commands

interface ExecuteOnlyCommand : Command {
    override fun getCategory(): CommandCategory = CommandCategory.EXECUTE_ONLY
}