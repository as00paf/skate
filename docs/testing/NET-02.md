# NET-02 — Manual verification steps

NET-02 is engine-level (transport + replication). **It has no editor menu, project template, sample or CLI command yet**, so there is nothing to click in the editor; wiring into the game/editor is a follow-up. Verification is therefore via Gradle tests and an optional real-socket smoke test. All commands run from the repo root (Windows: `.\gradlew.bat`; WSL: `cmd.exe /c gradlew.bat`).

## A. Clean state

1. `git status` → clean working tree on branch `agents/online`; `git log --oneline -3` shows the NET-02 commit(s) on top.
2. `.\gradlew.bat clean` → `BUILD SUCCESSFUL`.
3. `.\gradlew.bat compileKotlin` → `BUILD SUCCESSFUL` (first run downloads `io.netty:netty-transport` / `netty-buffer` 4.1.115.Final).

## B. Automated tests (no sockets)

4. `.\gradlew.bat :test --tests com.pafoid.skate.engine.net.*`
   Expected: 22 lines ending in `PASSED`, `BUILD SUCCESSFUL`, none `FAILED`. Groups:
   - `NetCodecTest` (6): binary codec round-trip, stable bytes, truncated data rejected, delta encode/decode, unchanged objects omitted, delta much smaller than full state.
   - `ReliabilityLayerTest` (5): reliable delivery with 50 % loss, ordering/duplicates, no resend after ack, stale sequenced packets dropped, 16-bit sequence wrap.
   - `InterpolationTest` (4): blend/clamp, shortest-path rotation (350° to 10° passes through 360°), out-of-order samples ignored, client clock bounds.
   - `ReplicationTest` (7): steps 5–11.
5. `Replication_TwoClientsSeeTheMovingObjectInterpolated` PASSED: 2 clients both receive the spawn; an object moving 6 m/s appears on each client 0–2 m behind the server and moves about 0.1 m per frame (smooth, not stepping at 20 Hz).
6. `Replication_LateJoinerReceivesExistingObjectsAndDespawnIsReplicated` PASSED: a client joining later gets all existing objects at the right position; a despawn removes the object and later snapshots do not resurrect it.
7. `Replication_SurvivesPacketLossAndStaysConsistent` PASSED: with every 3rd datagram dropped the client stays connected and tracks the server within 1.5 m.
8. `Replication_RejectsProtocolMismatchAndFullServer` PASSED: the 2nd client to a 1-player server is rejected ("server full"); protocol version 99 is rejected ("protocol mismatch").
9. `Replication_ForwardsClientInputToServer` PASSED: input sent by the client arrives at `server.onInput` with the client's id and tick.
10. `Replication_TimesOutSilentClient` PASSED: a client that stops polling is dropped after 10 s (simulated time) and `onClientLeft` fires once.
11. `Replication_SplitsLargeSnapshotsIntoParts` PASSED: 120 objects (more than one packet) all arrive.
12. `.\gradlew.bat :test` → `BUILD SUCCESSFUL` (full suite, no regressions).

## C. Optional: real UDP (Netty) smoke test

13. Create `src/test/kotlin/com/pafoid/skate/engine/net/TmpUdpSmoke.kt`:

```kotlin
package com.pafoid.skate.engine.net
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
class TmpUdpSmoke {
    @Test fun Tmp_Udp() {
        val s = UdpTransport(); val c = UdpTransport()
        s.listen(0); c.connect("127.0.0.1", s.localPort)
        val got = ArrayList<String>(); var connected = 0
        val l = object : TransportListener {
            override fun onConnected(connection: Connection) { connected++; connection.send(Channel.RELIABLE_ORDERED, "hi".toByteArray()) }
            override fun onDisconnected(connection: Connection, reason: String) {}
            override fun onMessage(connection: Connection, channel: Channel, payload: ByteArray) { got += String(payload) }
        }
        repeat(100) { val now = System.nanoTime() / 1_000_000; s.poll(l, now); c.poll(l, now); Thread.sleep(10) }
        s.close(); c.close()
        assertEquals(2, connected); assertEquals(listOf("hi", "hi"), got)
    }
}
```

14. `.\gradlew.bat :test --tests com.pafoid.skate.engine.net.TmpUdpSmoke` → `Tmp_Udp() PASSED` (server and client connected over a real loopback UDP socket and exchanged a reliable message). Allow Windows Firewall if prompted.
15. Delete `TmpUdpSmoke.kt` (unit tests must not open real sockets).

## D. Not verifiable yet

"Two local clients see each other skate" in the running game/editor needs game wiring (a spawner for the skater prefab, registering `NetServerSystem`/`NetClientSystem`, sending input). That is not part of NET-02; the in-memory test in step 5 is the stand-in.
