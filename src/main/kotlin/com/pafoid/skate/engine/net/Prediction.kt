package com.pafoid.skate.engine.net

import com.pafoid.skate.engine.ecs.GameObject
import kotlin.math.exp
import kotlin.math.sqrt

/** One fixed-step input of a client. [seq] increases by one per client fixed step; [viewTick] is the server tick the client was rendering (×16). */
class InputCommand(val seq: Int, val payload: ByteArray, val viewTick16: Int = 0)

/**
 * Deterministic game step for the predicted (local) player, run on both server and client. It must only read
 * state that is replicated by the [ReplicationSchema] (put velocity etc. into [NetState] or a custom replicator),
 * otherwise replays after a correction diverge.
 */
fun interface PredictionStep {
    fun step(go: GameObject, input: ByteArray, dt: Float)
}

/**
 * Client-side prediction and reconciliation for the locally owned object (NET-01 §7).
 *
 * [predict] runs [step] immediately for each local input and remembers it. [reconcile] takes an authoritative
 * snapshot state together with the last input sequence the server processed, drops acknowledged inputs, resets to
 * the server state and replays the rest. The positional difference between the old and new prediction becomes a
 * visual offset that decays over [smoothingTimeS] (or is dropped when larger than [snapDistance]).
 *
 * The object's simulation state is kept quantized ([predictedState]); [render] writes it plus the offset to the
 * [GameObject].
 */
class ClientPredictor(
    val schema: ReplicationSchema,
    private val step: PredictionStep,
    private val fixedDt: Float,
    var smoothingTimeS: Float = 0.1f,
    var snapDistance: Float = 2f,
    private val maxPending: Int = 128,
) {
    private val pending = ArrayDeque<InputCommand>()
    private val posOffset: Int = run {
        var off = 0
        for (r in schema.replicators) { if (r === TransformReplicator) return@run off; off += r.fieldCount }
        -1
    }

    var gameObject: GameObject? = null
        private set
    var predictedState: IntArray? = null
        private set
    var lastAckedSeq = 0
        private set

    /** Visual correction (meters) added to the predicted position. */
    val correction = FloatArray(3)

    /** Distance (meters) of the last misprediction and the number of corrections larger than [VISIBLE_ERROR]. */
    var lastErrorDistance = 0f
        private set
    var correctionCount = 0
        private set

    val pendingCount: Int get() = pending.size

    fun attach(go: GameObject, state: IntArray) {
        gameObject = go
        predictedState = state.copyOf()
        pending.clear()
        correction.fill(0f)
        schema.apply(go, state)
    }

    fun detach() {
        gameObject = null
        predictedState = null
        pending.clear()
    }

    /** Applies [cmd] locally right away. */
    fun predict(cmd: InputCommand) {
        val state = predictedState ?: return
        pending.addLast(cmd)
        while (pending.size > maxPending) pending.removeFirst()
        predictedState = simulate(state, cmd.payload)
    }

    /** Corrects the prediction with the server's [authoritative] state after it processed input [ackedSeq]. */
    fun reconcile(ackedSeq: Int, authoritative: IntArray) {
        val old = predictedState ?: return
        if (ackedSeq < lastAckedSeq) return
        lastAckedSeq = ackedSeq
        while (pending.isNotEmpty() && pending.first().seq <= ackedSeq) pending.removeFirst()

        var state = authoritative.copyOf()
        for (cmd in pending) state = simulate(state, cmd.payload)
        predictedState = state

        if (posOffset < 0) return
        var d2 = 0f
        val err = FloatArray(3)
        for (i in 0 until 3) {
            err[i] = (old[posOffset + i] - state[posOffset + i]) / TransformReplicator.POS_SCALE
            d2 += err[i] * err[i]
        }
        lastErrorDistance = sqrt(d2)
        if (lastErrorDistance > VISIBLE_ERROR) correctionCount++
        if (lastErrorDistance > snapDistance) correction.fill(0f) else for (i in 0 until 3) correction[i] += err[i]
    }

    /** Decays the visual correction and writes the predicted state to the object. */
    fun render(dt: Float) {
        val go = gameObject ?: return
        val state = predictedState ?: return
        val k = if (smoothingTimeS <= 0f) 0f else exp(-dt / smoothingTimeS)
        for (i in 0 until 3) correction[i] = if (k == 0f) 0f else correction[i] * k
        val visual = state.copyOf()
        if (posOffset >= 0) for (i in 0 until 3) visual[posOffset + i] += Quantize.toInt(correction[i], TransformReplicator.POS_SCALE)
        schema.apply(go, visual)
    }

    private fun simulate(state: IntArray, input: ByteArray): IntArray {
        val go = gameObject ?: return state
        schema.apply(go, state)
        step.step(go, input, fixedDt)
        return schema.capture(go)
    }

    companion object {
        /** Errors below this (meters) come from per-step quantization and are not counted as mispredictions. */
        const val VISIBLE_ERROR = 0.01f
    }
}

/**
 * Server-side per-client input queue: drops redundant copies, keeps order, and applies at most one input per
 * server tick. [lastProcessedSeq] is sent back in snapshots so the client knows what to replay.
 */
class ServerInputQueue(private val maxBuffered: Int = 8) {
    private val queue = ArrayDeque<InputCommand>()
    private var newestSeq = 0

    var lastProcessedSeq = 0
        private set

    /** View tick (server ticks) of the last processed input; used for lag compensation. */
    var viewTick = 0.0
        private set

    val size: Int get() = queue.size

    /** Adds commands (ascending seq); duplicates and already seen sequences are ignored. */
    fun receive(cmds: List<InputCommand>) {
        for (c in cmds) {
            if (c.seq <= newestSeq) continue
            newestSeq = c.seq
            queue.addLast(c)
        }
        while (queue.size > maxBuffered) lastProcessedSeq = queue.removeFirst().seq // too far behind: skip oldest
    }

    fun take(): InputCommand? {
        val c = queue.removeFirstOrNull() ?: return null
        lastProcessedSeq = c.seq
        viewTick = c.viewTick16 / 16.0
        return c
    }
}

/** INPUT message body: `viewTick16:varint count:u8 (seq:varint len:varint bytes)*`, oldest first. */
internal object InputCodec {
    const val MAX_INPUT_PAYLOAD = 200

    fun write(w: NetWriter, viewTick16: Int, cmds: List<InputCommand>) {
        require(cmds.size in 1..255) { "1..255 commands per message" }
        w.writeVarInt(viewTick16)
        w.writeByte(cmds.size)
        for (c in cmds) { w.writeVarInt(c.seq); w.writeVarInt(c.payload.size); w.writeBytes(c.payload) }
    }

    fun read(r: NetReader): List<InputCommand> {
        val view = r.readVarInt()
        return List(r.readByte()) {
            val seq = r.readVarInt()
            val len = r.readVarInt()
            require(len in 0..MAX_INPUT_PAYLOAD) { "Input payload too large" }
            InputCommand(seq, r.readBytes(len), view)
        }
    }
}
