// Game over screen: R returns to the open scene "Game", which restarts the game script.
val KEY_R = 82

onUpdate { ctx, _ ->
    if (ctx.input.isKeyPressed(KEY_R)) ctx.scene.load("Game")
}
