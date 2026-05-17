package com.pafoid.skate.engine.contracts

import com.pafoid.skate.engine.input.InputMappings

interface InputMappingsProvider {
    fun loadInputMappings(): InputMappings?
}
