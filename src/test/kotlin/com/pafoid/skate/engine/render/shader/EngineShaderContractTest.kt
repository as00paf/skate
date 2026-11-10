package com.pafoid.skate.engine.render.shader

import com.pafoid.skate.engine.assets.EngineDefaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File

/** AST-26 §5.1: every basic (overridable) engine shader declares its `@skate-contract` version. */
class EngineShaderContractTest {

    @TestFactory
    fun everyBasicShader_DeclaresAContract() = EngineDefaults.Shaders.BASIC.map { ref ->
        DynamicTest.dynamicTest(ref) {
            val file = File("assets/" + EngineDefaults.logicalPath(ref))
            val version = ShaderContract.of(file.readText())
            assertTrue(version != null && version >= 1, "$file needs '// @skate-contract <n>' on its first line")
        }
    }

    @Test
    fun contractParsing() {
        assertEquals(3, ShaderContract.of("// @skate-contract 3\n#type vertex"))
        assertEquals(2, ShaderContract.of("\n  //   @skate-contract 2  \nfoo"))
        assertNull(ShaderContract.of("// skate-contract 2"))
        assertTrue(ShaderContract.compatible("// @skate-contract 2", "// @skate-contract 2"))
        assertTrue(ShaderContract.compatible("// @skate-contract 3", "// @skate-contract 2"))
        assertFalse(ShaderContract.compatible("// @skate-contract 1", "// @skate-contract 2"))
        assertFalse(ShaderContract.compatible("no header", "// @skate-contract 1"))
        assertTrue(ShaderContract.compatible("no header", "engine without contract"))
    }
}
