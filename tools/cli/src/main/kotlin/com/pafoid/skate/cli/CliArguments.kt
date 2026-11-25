package com.pafoid.skate.cli

import com.pafoid.skate.engine.physics3d.PhysicsBackend
import com.pafoid.skate.engine.render.rhi.GraphicsBackend
import com.pafoid.skate.templates.EmptyTemplate
import com.pafoid.skate.templates.NewProjectSettings
import com.pafoid.skate.templates.TemplateRegistry
import java.io.File

sealed interface CliCommand {
    data object Help : CliCommand
    data class Create(
        val name: String,
        val directory: String,
        val settings: NewProjectSettings = NewProjectSettings(EmptyTemplate.ID),
    ) : CliCommand
    data class Validate(val project: String) : CliCommand
    data class Pack(val project: String) : CliCommand
    data class Export(val project: String, val output: String) : CliCommand
    data class Sample(val dir: String, val name: String = CliArguments.DEFAULT_SAMPLE) : CliCommand
    data class Run(val project: String, val scene: String?, val frames: Int, val dt: Float) : CliCommand
}

class CliParseException(message: String) : Exception(message)

object CliArguments {
    const val DEFAULT_SAMPLE = "feature-showcase"
    val SAMPLES: List<String> = TemplateRegistry.sampleNames.keys.toList()
    const val DEFAULT_FRAMES = 60
    const val DEFAULT_DT = 1f / 60f

    val usage: String = """
        Usage: skate <command> [options]

        Commands:
          create <name> [--dir <folder>] [--template <id>] [--physics bullet|simple|jolt]
                 [--graphics opengl|vulkan] [--fps <n>|uncapped] [--engine-defaults copy|reference]
                 [--splash <png/jpg>] [--ai-files] [--vcs]
                                                    Create a new project (default folder: current directory,
                                                    default template: empty). Templates: ${TemplateRegistry.ids().joinToString()}
          validate <project>                        Validate a project file and the assets it references
          pack <project>                            Pack project assets into Builds/game.bin
          export <project> --out <folder>           Pack and copy the build output into <folder>
          run <project> [--scene <name>] [--frames <n>] [--dt <seconds>]
                                                    Run a scene headless and print engine stats
          sample [${SAMPLES.joinToString("|")}] [--dir <folder>]
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
        val parsed = parseArgs(
            "create", rest,
            valueOptions = setOf("--dir", "--template", "--physics", "--graphics", "--fps", "--engine-defaults", "--splash"),
            flags = setOf("--ai-files", "--vcs"),
        )
        val o = parsed.options
        val template = o["--template"] ?: EmptyTemplate.ID
        if (TemplateRegistry.get(template) == null) {
            throw CliParseException("Unknown template '$template' (available: ${TemplateRegistry.ids().joinToString()})")
        }
        val settings = NewProjectSettings(
            template = template,
            targetFps = o["--fps"]?.let(::parseFps) ?: 0,
            physicsBackend = o["--physics"]?.let { enumOption<PhysicsBackend>("--physics", it) } ?: PhysicsBackend.BULLET,
            graphicsBackend = o["--graphics"]?.let { enumOption<GraphicsBackend>("--graphics", it) } ?: GraphicsBackend.DEFAULT,
            includeAiFiles = "--ai-files" in parsed.flags,
            splashImage = o["--splash"]?.let { File(it) },
            copyEngineDefaults = when (val mode = o["--engine-defaults"] ?: "copy") {
                "copy" -> true
                "reference" -> false
                else -> throw CliParseException("--engine-defaults must be copy or reference, not '$mode'")
            },
            versionControl = "--vcs" in parsed.flags,
        )
        return CliCommand.Create(parsed.positional("name"), o["--dir"] ?: ".", settings)
    }

    private fun parseFps(value: String): Int {
        if (value.equals("uncapped", ignoreCase = true)) return 0
        val fps = value.toIntOrNull()
        if (fps == null || fps == 0 || !NewProjectSettings.isValidTargetFps(fps)) {
            throw CliParseException("--fps must be 'uncapped' or a number from 15 to 240, not '$value'")
        }
        return fps
    }

    private inline fun <reified E : Enum<E>> enumOption(option: String, value: String): E =
        enumValues<E>().firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: throw CliParseException("$option must be one of ${enumValues<E>().joinToString("|") { it.name.lowercase() }}, not '$value'")

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

    private class Parsed(
        val command: String,
        val positionals: List<String>,
        val options: Map<String, String>,
        val flags: Set<String> = emptySet(),
    ) {
        fun positional(label: String = "project"): String {
            if (positionals.size != 1) throw CliParseException("'$command' expects exactly one <$label> argument")
            return positionals[0]
        }


        fun required(option: String): String =
            options[option] ?: throw CliParseException("'$command' requires $option <value>")
    }

    private fun parseArgs(command: String, args: List<String>, valueOptions: Set<String>, flags: Set<String> = emptySet()): Parsed {
        val positionals = mutableListOf<String>()
        val options = linkedMapOf<String, String>()
        val setFlags = linkedSetOf<String>()
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            if (arg in flags) {
                setFlags += arg
                i++
            } else if (arg.startsWith("--")) {
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
        return Parsed(command, positionals, options, setFlags)
    }
}
