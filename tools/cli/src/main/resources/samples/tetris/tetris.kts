// Tetris game logic as a Skate Kotlin script. Attach to one entity; the scene must contain
// the pooled cell entities Cell_0 .. Cell_203 (10x20 board + 4 active piece cells).
// Optional entities: ScoreText (UiText, key tetris.hud) and SfxLock / SfxClear / SfxOver (AudioSource).
// Game over switches to the open scene "GameOver".
// Controls: Left/Right move, Up rotate, Down soft drop, Space hard drop, R restart.

import org.joml.Vector4f

val COLS = 10
val ROWS = 20
val POOL = COLS * ROWS + 4
val HIDDEN_Y = -1000f
val KEY_LEFT = 263
val KEY_RIGHT = 262
val KEY_DOWN = 264
val KEY_UP = 265
val KEY_SPACE = 32
val KEY_R = 82

// Cells relative to the piece pivot, y grows downwards on the board
val SHAPES = listOf(
    listOf(-1 to 0, 0 to 0, 1 to 0, 2 to 0), // I
    listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),  // O (does not rotate)
    listOf(-1 to 0, 0 to 0, 1 to 0, 0 to 1), // T
    listOf(0 to 0, 1 to 0, -1 to 1, 0 to 1), // S
    listOf(-1 to 0, 0 to 0, 0 to 1, 1 to 1), // Z
    listOf(-1 to 0, 0 to 0, 1 to 0, -1 to 1), // J
    listOf(-1 to 0, 0 to 0, 1 to 0, 1 to 1), // L
)
val LINE_SCORES = intArrayOf(0, 100, 300, 500, 800)

val COLORS = listOf(
    Vector4f(0.2f, 0.9f, 0.9f, 1f), Vector4f(0.95f, 0.9f, 0.2f, 1f), Vector4f(0.7f, 0.3f, 0.9f, 1f),
    Vector4f(0.3f, 0.85f, 0.3f, 1f), Vector4f(0.9f, 0.25f, 0.25f, 1f), Vector4f(0.25f, 0.4f, 0.95f, 1f),
    Vector4f(0.95f, 0.6f, 0.2f, 1f),
)

// 0 = empty, otherwise shape index + 1 (the color of the locked cell)
val board = Array(ROWS) { IntArray(COLS) }
var rng = java.util.Random(1)
var cells: List<Pair<Int, Int>> = emptyList()
var shape = 0
var px = 0
var py = 0
var fallTimer = 0f
var score = 0
var lines = 0
var gameOver = false
lateinit var pool: List<ScriptEntity>

fun level() = lines / 10
fun fallInterval() = maxOf(0.1f, 0.5f - level() * 0.04f)

fun fits(c: List<Pair<Int, Int>>, x: Int, y: Int) = c.all { (cx, cy) ->
    val bx = x + cx
    val by = y + cy
    bx in 0 until COLS && by < ROWS && (by < 0 || board[by][bx] == 0)
}

fun rotated(c: List<Pair<Int, Int>>) = if (shape == 1) c else c.map { (x, y) -> -y to x }

fun sfx(ctx: ScriptContext, name: String) {
    ctx.scene.find(name)?.getComponent(ScriptAudioSource::class)?.play()
}

fun hud(ctx: ScriptContext) {
    ctx.scene.find("ScoreText")?.getComponent(ScriptUiText::class)?.args = listOf("$score", "$lines", "${level()}")
}

fun spawn(ctx: ScriptContext) {
    shape = rng.nextInt(SHAPES.size)
    cells = SHAPES[shape]
    px = COLS / 2 - 1
    py = 0
    fallTimer = 0f
    if (!fits(cells, px, py)) {
        gameOver = true
        ctx.log.info("gameover score=$score lines=$lines")
        ctx.events.emit("tetris.gameover", score)
        sfx(ctx, "SfxOver")
        ctx.scene.load("GameOver")
    }
}

fun reset(ctx: ScriptContext) {
    board.forEach { it.fill(0) }
    score = 0
    lines = 0
    gameOver = false
    ctx.log.info("start score=0")
    hud(ctx)
    spawn(ctx)
}

fun lock(ctx: ScriptContext) {
    cells.forEach { (cx, cy) -> board.getOrNull(py + cy)?.set(px + cx, shape + 1) }
    var cleared = 0
    var row = ROWS - 1
    while (row >= 0) {
        if (board[row].all { it != 0 }) {
            for (r in row downTo 1) board[r] = board[r - 1]
            board[0] = IntArray(COLS)
            cleared++
        } else {
            row--
        }
    }
    if (cleared > 0) {
        lines += cleared
        score += LINE_SCORES[cleared] * (level() + 1)
        ctx.log.info("score=$score lines=$lines level=${level()}")
        ctx.events.emit("tetris.score", score)
        sfx(ctx, "SfxClear")
    } else {
        sfx(ctx, "SfxLock")
    }
    hud(ctx)
    spawn(ctx)
}

fun step(ctx: ScriptContext) {
    if (fits(cells, px, py + 1)) py++ else lock(ctx)
}

fun render() {
    var i = 0
    fun put(col: Int, row: Int, color: Int) {
        val cell = pool[i++]
        cell.transform.position.set(col - (COLS - 1) / 2f, (ROWS - 1 - row).toFloat(), 0f)
        cell.getComponent(ScriptMaterial::class)?.color = COLORS[color - 1]
    }
    for (r in 0 until ROWS) for (c in 0 until COLS) if (board[r][c] != 0) put(c, r, board[r][c])
    if (!gameOver) cells.forEach { (cx, cy) -> put(px + cx, py + cy, shape + 1) }
    while (i < POOL) pool[i++].transform.position.set(0f, HIDDEN_Y, 0f)
}

onCreate { ctx ->
    rng = java.util.Random(ctx.properties["seed"]?.toLongOrNull() ?: System.nanoTime())
    pool = (0 until POOL).map { ctx.scene.find("Cell_$it") ?: error("Missing Cell_$it") }
    reset(ctx)
    render()
}

onUpdate { ctx, dt ->
    val input = ctx.input
    if (input.isKeyPressed(KEY_R)) reset(ctx)
    if (!gameOver) {
        if (input.isKeyPressed(KEY_LEFT) && fits(cells, px - 1, py)) px--
        if (input.isKeyPressed(KEY_RIGHT) && fits(cells, px + 1, py)) px++
        if (input.isKeyPressed(KEY_UP)) rotated(cells).let { if (fits(it, px, py)) cells = it }
        if (input.isKeyPressed(KEY_SPACE)) {
            var dropped = 0
            while (fits(cells, px, py + 1)) { py++; dropped++ }
            score += dropped * 2
            lock(ctx)
        } else {
            fallTimer += dt * if (input.isKeyDown(KEY_DOWN)) 10f else 1f
            while (!gameOver && fallTimer >= fallInterval()) {
                fallTimer -= fallInterval()
                step(ctx)
            }
        }
    }
    render()
}
