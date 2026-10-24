package com.pafoid.skate.cli

import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(runCli(args.toList()))
}

fun runCli(args: List<String>): Int {
    val command = try {
        CliArguments.parse(args)
    } catch (e: CliParseException) {
        System.err.println("skate: ${e.message}\n\n${CliArguments.usage}")
        return 2
    }
    if (command is CliCommand.Help) {
        println(CliArguments.usage)
        return 0
    }
    return try {
        val service = SkateCliService()
        when (command) {
            is CliCommand.Create -> service.createProject(command.name, File(command.directory).absoluteFile)
            is CliCommand.Validate -> return validate(service, command)
            is CliCommand.Sample -> {
                val project = ShowcaseProjectBuilder().build(File(command.dir).absoluteFile)
                println("Generated sample project at ${project.path}")
            }
            is CliCommand.Pack -> service.pack(command.project)
            is CliCommand.Export -> service.export(command.project, File(command.output))
            is CliCommand.Run -> service.run(command.project, command.scene, command.frames, command.dt)
            CliCommand.Help -> Unit
        }
        0
    } catch (e: CliException) {
        System.err.println("skate: ${e.message}")
        1
    }
}

private fun validate(service: SkateCliService, command: CliCommand.Validate): Int {
    val report = service.validate(command.project)
    report.warnings.forEach { println("warning: $it") }
    report.errors.forEach { System.err.println("error: $it") }
    println(if (report.isValid) "Project is valid (${report.sceneCount} scenes)" else "Project is invalid")
    return if (report.isValid) 0 else 1
}
