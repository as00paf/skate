# NET-05 — Manual test steps (Steam networking transport)

The networking is engine-only: no editor UI, template or default-project content uses it yet, so a new project behaves the same as before. Run commands from the repo root (WSL: `cmd.exe /c gradlew.bat ...`).

1. Create a **new project** in the editor (File → New Project), open its scene and press Play. Expected: no change from before, and nothing Steam-related is needed. `PlatformServicesFactory` and `LobbySession` are not wired into the editor/game yet.
2. `.\gradlew.bat :test --tests "com.pafoid.skate.engine.net.PeerTransportTest"`. Expected: 6 PASSED. Covers peer-id handshake, reliable order with 1/3 loss, datagrams never exceeding the platform limit, close releasing Steam sessions on both sides, invalid address rejection and connect timeout.
3. `.\gradlew.bat :test --tests "com.pafoid.skate.engine.net.LobbySessionTest"`. Expected: 5 PASSED. Covers direct host/join without Steam (a replication client gets CONNECTED), the Steam lobby flow over fake Steam (lobby data published, client connects to the host peer and receives a spawned skater), protocol mismatch leaving the lobby, create/join failures, and a late lobby result after `leave()`.
4. `.\gradlew.bat :test --tests "com.pafoid.skate.engine.platform.*"`. Expected: all PASSED, including `assertNull(peerNetwork)` for the no-op platform.
5. Optional, needs two Steam accounts on two PCs with the Steam client running and `steam_appid.txt` set to `480`: in a scratch `main`, host with `LobbySession(PlatformServicesFactory.create()).host(4)`, then pump `platform.update()` each frame until the state is `Ready`. Start a `ReplicationServer(ready.transport)` and print `ready.lobby.id`.
6. On the second PC, call `join(<lobbyId>)` and pump `update()` until the state is `Ready`. Then `ReplicationClient(ready.transport, ...).connect(ready.address, ready.port)`. Expected: the client state becomes `CONNECTED` and the host's `clientCount` is 1. Traffic goes over Steam P2P, with no port forwarding.
7. `.\gradlew.bat :test`. Expected: `BUILD SUCCESSFUL`.
