# NET-03 — Manual test steps (client-side prediction and lag compensation)

Prediction is wired into the game through the Feature Showcase **Netcode** demo (GPL-17). `[Netcode] Session` (`LoopbackPredictionComponent`) runs a server and two clients in-process over a simulated link, with the kinematic `SkaterPredictionStep`. Real multiplayer (UDP or Steam players) is not wired yet. Run commands from the repo root (WSL: `cmd.exe /c gradlew.bat ...`).

1. `.\gradlew.bat run`. Create a **new project**, open the `FeatureShowcase` scene tab, press **Play** and click **Netcode** in the legend. The rider carves a circle and ollies.
   - The **blue** box (own prediction) stays on the **green** ghost (server), just ahead of it by the inputs in flight. Its **Prediction Error (m)** readout is mostly that lead, not a misprediction.
   - The **orange** box (another client, interpolated) trails behind on the same path.
1a. Select `[Netcode] Session` and set **Latency** to 1000 (the maximum, a 2 s round trip). After the restart, blue still responds at once and stays smooth on the green path, with no snapping back: the 3 s prediction buffer covers the round trip (NET-06).
1b. Set **Packet Loss** to 20. Blue only occasionally eases back onto the server path (all redundant copies of an input lost), then follows it again. Stop Play: the boxes return to their edit positions.
2. `.\gradlew.bat :test --tests "com.pafoid.skate.engine.net.PredictionTest"`. Expected: 6 PASSED.
   - Local player moves on the same frame with 80 ms latency; a non-predicted client is still at 0 after 11 frames.
   - With 60 ms latency and 20 % loss, the server applies every input once, the prediction converges to the server position (±2 mm) with no correction above 1 cm, and the second client interpolates the first client's player.
   - A server-only wall: the client overshoots, is corrected to x = 1 and the visual offset decays.
   - Predictor replay, smoothing and snapping.
   - Input queue de-duplication and overflow; input codec.
3. `.\gradlew.bat :test --tests "com.pafoid.skate.engine.net.LagCompensationTest"`. Expected: 3 PASSED.
   - History blending and window clamping.
   - `rewound(client)` puts a moving target where that client saw it (6–15 ticks back) and leaves the shooter alone; the Transform is restored bit-exact afterwards.
   - View tick clamped to the window.
4. `.\gradlew.bat :test --tests "com.pafoid.skate.engine.net.ReliabilityLayerTest"`. Expected: 6 PASSED, including `Reliability_SequencedChannelDeliversEveryMessageOfOnePacket` (NET-02 bug fix).
4a. `.\gradlew.bat :test --tests "com.pafoid.skate.engine.net.LoopbackPredictionSessionTest" --tests "com.pafoid.skate.engine.net.LoopbackPredictionSystemTest" --tests "com.pafoid.skate.game.net.*"`. Expected: all PASSED.
   - No divergence at the demo's maximum latency.
   - The 3 s prediction buffer covers the round trip with headroom.
   - The predictor reuses its state buffer.
   - The skater step is bit-identical on server and client.
5. `.\gradlew.bat :test --tests "com.pafoid.skate.engine.net.*"`. Expected: all PASSED (ReplicationTest and LobbySessionTest unchanged in behaviour; protocol version is now 2).
6. `.\gradlew.bat :test`. Expected: `BUILD SUCCESSFUL`.
