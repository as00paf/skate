// Feature Showcase (ECS-14): shows the project's random seed and the first rolls of the "showcase.dice" stream.
// The same seed gives the same rolls on every Play. Change it in Project Settings > Random.
onCreate { ctx ->
    val dice = ctx.random.stream("showcase.dice")
    val rolls = List(5) { dice.nextInt(1, 7) }.joinToString(" ")
    ctx.scene.find("SeedLabel")?.getComponent(ScriptUiText::class)?.args = listOf(ctx.random.seed.toString(), rolls)
    ctx.log.info("Random seed ${ctx.random.seed}, dice $rolls")
}
