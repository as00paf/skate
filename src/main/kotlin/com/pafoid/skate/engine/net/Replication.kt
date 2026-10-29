package com.pafoid.skate.engine.net

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import kotlinx.serialization.Serializable

/** Marks a [GameObject] as network replicated. [netId] is allocated by the server and never reused. */
@Serializable
class NetworkIdentity(
    var netId: Int = 0,
    /** Owning client id, [SERVER_OWNER] for server-owned objects. */
    var ownerClientId: Int = SERVER_OWNER,
    /** Key the client's spawner uses to instantiate the matching prefab. */
    var prefabId: String = "",
) : Component() {
    companion object { const val SERVER_OWNER = 0 }
}

/** Game-defined integer state replicated without interpolation (e.g. skate state enum, flags, score). */
@Serializable
class NetState(val values: IntArray = IntArray(SIZE)) : Component() {
    companion object { const val SIZE = 8 }
}

/** Converts a component's replicated fields to/from quantized ints so snapshots can be delta compressed generically. */
interface ComponentReplicator {
    val fieldCount: Int

    /** Writes the fields into [out] at [offset]; leaves them untouched if the object lacks the component. */
    fun capture(go: GameObject, out: IntArray, offset: Int)

    fun apply(go: GameObject, values: IntArray, offset: Int)

    /** Blends between two states; the default holds [a] until [t] reaches 1. */
    fun blend(a: IntArray, b: IntArray, t: Float, out: IntArray, offset: Int) {
        for (i in 0 until fieldCount) out[offset + i] = if (t >= 1f) b[offset + i] else a[offset + i]
    }
}

/** Position 1/1024 m, rotation 1/128 degree (shortest-path blend), scale 1/1024. */
object TransformReplicator : ComponentReplicator {
    const val POS_SCALE = 1024f
    const val ROT_SCALE = 128f
    private const val ROT_PERIOD = 360 * 128

    override val fieldCount = 9

    override fun capture(go: GameObject, out: IntArray, offset: Int) {
        val t = go.getComponent<Transform>() ?: return
        out[offset] = Quantize.toInt(t.translation.x, POS_SCALE)
        out[offset + 1] = Quantize.toInt(t.translation.y, POS_SCALE)
        out[offset + 2] = Quantize.toInt(t.translation.z, POS_SCALE)
        out[offset + 3] = Quantize.toInt(t.rotation.x, ROT_SCALE)
        out[offset + 4] = Quantize.toInt(t.rotation.y, ROT_SCALE)
        out[offset + 5] = Quantize.toInt(t.rotation.z, ROT_SCALE)
        out[offset + 6] = Quantize.toInt(t.scale.x, POS_SCALE)
        out[offset + 7] = Quantize.toInt(t.scale.y, POS_SCALE)
        out[offset + 8] = Quantize.toInt(t.scale.z, POS_SCALE)
    }

    override fun apply(go: GameObject, values: IntArray, offset: Int) {
        val t = go.getComponent<Transform>() ?: return
        t.translation.set(Quantize.toFloat(values[offset], POS_SCALE), Quantize.toFloat(values[offset + 1], POS_SCALE), Quantize.toFloat(values[offset + 2], POS_SCALE))
        t.rotation.set(Quantize.toFloat(values[offset + 3], ROT_SCALE), Quantize.toFloat(values[offset + 4], ROT_SCALE), Quantize.toFloat(values[offset + 5], ROT_SCALE))
        t.scale.set(Quantize.toFloat(values[offset + 6], POS_SCALE), Quantize.toFloat(values[offset + 7], POS_SCALE), Quantize.toFloat(values[offset + 8], POS_SCALE))
    }

    override fun blend(a: IntArray, b: IntArray, t: Float, out: IntArray, offset: Int) {
        for (i in 0 until 9) {
            val av = a[offset + i]
            var d = b[offset + i] - av
            if (i in 3..5) { // angles: take the short way around
                d = Math.floorMod(d + ROT_PERIOD / 2, ROT_PERIOD) - ROT_PERIOD / 2
            }
            out[offset + i] = av + Math.round(d * t)
        }
    }
}

object NetStateReplicator : ComponentReplicator {
    override val fieldCount = NetState.SIZE

    override fun capture(go: GameObject, out: IntArray, offset: Int) {
        val s = go.getComponent<NetState>() ?: return
        System.arraycopy(s.values, 0, out, offset, fieldCount)
    }

    override fun apply(go: GameObject, values: IntArray, offset: Int) {
        val s = go.getComponent<NetState>() ?: return
        System.arraycopy(values, offset, s.values, 0, fieldCount)
    }
}

/** Ordered list of replicated components; every replicated object has the same flat field layout. */
class ReplicationSchema(val replicators: List<ComponentReplicator> = listOf(TransformReplicator, NetStateReplicator)) {
    val fieldCount: Int = replicators.sumOf { it.fieldCount }

    init { require(fieldCount in 1..63) { "Schema needs 1..63 fields (change mask is a Long), got $fieldCount" } }

    fun capture(go: GameObject): IntArray {
        val out = IntArray(fieldCount)
        var off = 0
        for (r in replicators) { r.capture(go, out, off); off += r.fieldCount }
        return out
    }

    fun apply(go: GameObject, values: IntArray) {
        var off = 0
        for (r in replicators) { r.apply(go, values, off); off += r.fieldCount }
    }

    fun blend(a: IntArray, b: IntArray, t: Float): IntArray {
        val out = IntArray(fieldCount)
        var off = 0
        for (r in replicators) { r.blend(a, b, t, out, off); off += r.fieldCount }
        return out
    }
}
