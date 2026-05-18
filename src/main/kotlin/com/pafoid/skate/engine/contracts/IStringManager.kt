package com.pafoid.skate.engine.contracts

interface IStringManager {
    fun getString(key: String): String
    fun getString(key: String, vararg formatArgs: Any): String
}
