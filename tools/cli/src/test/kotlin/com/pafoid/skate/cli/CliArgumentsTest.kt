package com.pafoid.skate.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CliArgumentsTest {

    @Test
    fun CliArguments_NoArgsAndHelpFlagsShowHelp() {
        assertEquals(CliCommand.Help, CliArguments.parse(emptyList()))
        assertEquals(CliCommand.Help, CliArguments.parse(listOf("--help")))
        assertEquals(CliCommand.Help, CliArguments.parse(listOf("-h")))
    }

    @Test
    fun CliArguments_UsageListsAllCommands() {
        listOf("create", "validate", "pack", "export", "run").forEach {
            assertTrue(CliArguments.usage.contains("  $it "), "usage should list $it")
        }
    }

    @Test
    fun CliArguments_CreateUsesDefaultAndExplicitDirectory() {
        assertEquals(CliCommand.Create("Game", "."), CliArguments.parse(listOf("create", "Game")))
        assertEquals(CliCommand.Create("Game", "C:\\x"), CliArguments.parse(listOf("create", "Game", "--dir", "C:\\x")))
    }

    @Test
    fun CliArguments_ExportRequiresOutput() {
        assertEquals(CliCommand.Export("p", "o"), CliArguments.parse(listOf("export", "p", "--out", "o")))
        assertFailsWith<CliParseException> { CliArguments.parse(listOf("export", "p")) }
    }

    @Test
    fun CliArguments_RunParsesOptionsWithDefaults() {
        assertEquals(
            CliCommand.Run("p", null, CliArguments.DEFAULT_FRAMES, CliArguments.DEFAULT_DT),
            CliArguments.parse(listOf("run", "p"))
        )
        assertEquals(
            CliCommand.Run("p", "Main", 10, 0.5f),
            CliArguments.parse(listOf("run", "p", "--scene", "Main", "--frames", "10", "--dt", "0.5"))
        )
    }

    @Test
    fun CliArguments_RejectsBadInput() {
        assertFailsWith<CliParseException> { CliArguments.parse(listOf("bogus")) }
        assertFailsWith<CliParseException> { CliArguments.parse(listOf("validate")) }
        assertFailsWith<CliParseException> { CliArguments.parse(listOf("validate", "a", "b")) }
        assertFailsWith<CliParseException> { CliArguments.parse(listOf("pack", "p", "--nope", "x")) }
        assertFailsWith<CliParseException> { CliArguments.parse(listOf("run", "p", "--frames")) }
        assertFailsWith<CliParseException> { CliArguments.parse(listOf("run", "p", "--frames", "0")) }
        assertFailsWith<CliParseException> { CliArguments.parse(listOf("run", "p", "--dt", "abc")) }
    }
}
