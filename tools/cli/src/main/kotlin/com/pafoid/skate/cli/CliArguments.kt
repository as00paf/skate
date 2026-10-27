package com.pafoid.skate.cli

sealed interface CliCommand {
    data object Help : CliCommand
    data class Create(val name: String, val directory: String) : CliCommand
    data class Validate(val project: String) : CliCommand
    data class Pack(val project: String) : CliCommand
    data class Export(val project: String, val output: String) : CliCommand
    data class Sample(val dir: String, val name: String = CliArguments.DEFAULT_SAMPLE) : CliCommand
    data class Run(val project: String, val scene: String?, val frames: Int, val dt: Float) : CliCommand
}

class CliParseException(message: String) : Exception(message)

object CliArguments {
    const val DEFAULT_SAMPLE = "feature-showcase"
    val SAMPLES = listOf(DEFAULT_SAMPLE, "marble-run", "tetris")
    const val DEFAULT_FRAMES = 60
    const val DEFAULT_DT = 1f / 60f

    val usage: String = """
        Usage: skate <command> [options]

        Commands:
          create <name> [--dir <folder>]            Create a new project (default folder: current directory)
          validate <project>                        Validate a project file and the assets it references
          pack <project>                            Pack project assets into Builds/game.bin
          export <project> --out <folder>           Pack and copy the build output into <folder>
          run <project> [--scene <name>] [--frames <n>] [--dt <seconds>]
                                                    Run a scene headless and print engine stats
          sample [feature-showcase|marble-run|tetris] [--dir <folder>]
                                                    Generate a sample project (see docs/samples.md)
          help, --help, -h                          Show this help

        <project> is a .skateproject file or the folder containing one.
    """.trimIndent()

    fun parse(args: List<String>): CliCommand {
        if (args.isEmpty()) return CliCommand.Help
        val name = args.first()
        val rest = args.drop(1)
        return when (name) {
            "help", "--help", "-h" -> CliCommand.Help
            "create" -> parseCreate(rest)
            "validate" -> CliCommand.Validate(parseArgs(name, rest, emptySet()).positional())
            "pack" -> CliCommand.Pack(parseArgs(name, rest, emptySet()).positional())
            "export" -> parseArgs(name, rest, setOf("--out")).let {
                CliCommand.Export(it.positional(), it.required("--out"))
            }
            "run" -> parseRun(rest)
            "sample" -> parseSample(rest)
            else -> throw CliParseException("Unknown command '$name'")
        }
    }

    private fun parseSample(rest: List<String>): CliCommand.Sample {
        val parsed = parseArgs("sample", rest, setOf("--dir"))
        val name = if (parsed.positionals.isEmpty()) DEFAULT_SAMPLE else parsed.positional("sample")
        if (name !in SAMPLES) throw CliParseException("Unknown sample '$name' (available: ${SAMPLES.joinToString()})")
        return CliCommand.Sample(parsed.options["--dir"] ?: ".", name)
    }

    private fun parseCreate(rest: List<String>): CliCommand.Create {
        val parsed = parseArgs("create", rest, setOf("--dir"))
        return CliCommand.Create(parsed.positional("name"), parsed.options["--dir"] ?: ".")
    }

    private fun parseRun(rest: List<String>): CliCommand.Run {
        val parsed = parseArgs("run", rest, setOf("--scene", "--frames", "--dt"))
        val frames = parsed.options["--frames"]?.let {
            it.toIntOrNull()?.takeIf { n -> n > 0 } ?: throw CliParseException("--frames must be a positive integer")
        } ?: DEFAULT_FRAMES
        val dt = parsed.options["--dt"]?.let {
            it.toFloatOrNull()?.takeIf { v -> v > 0f } ?: throw CliParseException("--dt must be a positive number")
        } ?: DEFAULT_DT
        return CliCommand.Run(parsed.positional(), parsed.options["--scene"], frames, dt)
    }

    private class Parsed(val command: String, val positionals: List<String>, val options: Map<String, String>) {
        fun positional(label: String = "project"): String {
            if (positionals.size != 1) throw CliParseException("'$command' expects exactly one <$label> argument")
            return positionals[0]
        }


        fun required(option: String): String =
            options[option] ?: throw CliParseException("'$command' requires $option <value>")
    }

    private fun parseArgs(command: String, args: List<String>, valueOptions: Set<String>): Parsed {
        val positionals = mutableListOf<String>()
        val options = linkedMapOf<String, String>()
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            if (arg.startsWith("--")) {
                if (arg !in valueOptions) throw CliParseException("Unknown option '$arg' for '$command'")
                val value = args.getOrNull(i + 1)?.takeUnless { it.startsWith("--") }
                    ?: throw CliParseException("Option $arg requires a value")
                options[arg] = value
                i += 2
            } else {
                positionals += arg
                i++
            }
        }
        return Parsed(command, positionals, options)
    }
}
