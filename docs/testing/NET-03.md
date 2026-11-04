# NET-03 — Manual test steps (client-side prediction and lag compensation)

NET-03 is engine-side only. It is not wired into the skate game or the editor yet, which needs a deterministic kinematic skater step (GPL/PHY). So there is no scene, component or Feature Showcase demo to look at, and the tests below run against a simulated network (latency and loss, no sockets). Run commands from the repo root (WSL: `cmd.exe /c gradlew.bat ...`).

1. Create a **new project** in the editor, open `MainScene` and the Feature Showcase scene, and press Play in each. Expected: behaviour unchanged; nothing in a scene uses networking yet.
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
5. `.\gradlew.bat :test --tests "com.pafoid.skate.engine.net.*"`. Expected: all PASSED (ReplicationTest and LobbySessionTest unchanged in behaviour; protocol version is now 2).
6. `.\gradlew.bat :test`. Expected: `BUILD SUCCESSFUL`.
