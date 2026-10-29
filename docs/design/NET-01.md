# NET-01 — Multiplayer architecture

Status: **awaiting lead approval**. No code is written until approved. Paths relative to `src/main/kotlin/com/pafoid/skate/engine/` (new packages `net/**`, `platform/**`).

## 1. Goals / non-goals

Goals: 2–16 players, any 3D game (not skate-only), server-authoritative, deterministic and unit-testable serialization, Steam optional.
Non-goals (v1): dedicated-server hosting infrastructure, voice chat, rollback netcode, anti-cheat beyond server validation, MMO-scale interest management.

## 2. Model

**Client-server, server-authoritative**, with client-side prediction + server reconciliation for the locally controlled object (NET-03) and snapshot interpolation for everything else (NET-02).

- Topologies share one code path: **dedicated** (headless server, `Skate CLI`), **listen server** (host is server + local client over a loopback transport, no sockets), **single player** (no net layer).
- Server simulates at the ECS fixed step (`FIXED_UPDATE`, ECS-03; default 60 Hz). Snapshots are sent every N ticks (default 20 Hz). Tick number (`Int`, wrapping with serial-number arithmetic) is the single time base.
- Clients send input commands per tick (redundantly: last 3 inputs per packet to survive loss). Server applies them in tick order.
- Physics (Bullet) is not deterministic across machines, so we do **not** do lockstep or client-side re-simulation of other bodies; only the local player is predicted (kinematic/character-controller style, NET-03).

## 3. Transport

Abstraction first, implementation swappable (NET-05 adds Steam):

```kotlin
interface Transport { fun listen(cfg); fun connect(addr); fun poll(handler); fun close() }
interface Connection { val id: ConnectionId; fun send(channel: Channel, payload: ByteBuffer); val rttMs: Float; fun close(reason) }
enum class Channel { RELIABLE_ORDERED, UNRELIABLE_SEQUENCED, UNRELIABLE }
```

Options considered:

| Option | Pros | Cons |
|---|---|---|
| **Own thin layer on `java.nio` `DatagramChannel`** (recommended) | No dependency, fully controllable, trivially fakeable (`InMemoryTransport`), small surface we need (3 channels, fragmentation, acks) | We write reliability/ack/fragmentation (~500 LOC) |
| KryoNet | Ready TCP+UDP | Unmaintained, TCP+UDP split, reflection-based serialization conflicts with our determinism requirement |
| Netty | Mature, async | Heavy dependency, pipeline model is overkill, still needs our own reliability on UDP |
| Steam Networking Sockets | NAT punch, relay | Steam-only; becomes `SteamTransport` in NET-05 behind the same interface |

Recommendation: **`DatagramChannel` + custom reliability** (`UdpTransport`), plus `InMemoryTransport` (loopback, used by listen-server and all unit tests — no real sockets in tests). Packet header: protocol id/version, sequence, ack + ack bitfield (32). Connection handshake: `Hello(protocolVersion, buildHash, name)` → `Welcome(clientId, tick, tickRate)` / `Reject(reason)`; challenge token to prevent spoofed-source floods. MTU budget 1200 bytes; fragmentation only for reliable channel (scene/state bootstrap).

## 4. Serialization

- Hand-written binary codec: `NetWriter`/`NetReader` over `ByteBuffer` (little-endian, varints, quantized floats). **No reflection, no kotlinx-serialization on the wire** (scene JSON stays for assets only).
- Deterministic: fixed field order, defined quantization (position 1/1024 m in a ±bound range, rotation as smallest-three quaternion 10 bits/comp, velocities half precision), no map-iteration dependence (entities sorted by `NetId`). Same input ⇒ identical bytes; round-trip tests assert this.
- Messages have a `Short` type id from a `MessageRegistry` (explicit registration, duplicate id = error at startup).

## 5. Replication of ECS state

- **Identity:** new `NetworkIdentity` component (`netId: Int`, `ownerClientId`, `prefabId`/`archetype`). Server allocates `netId` (separate from `GameObject.uId`, which is per-scene and editor-assigned). Only objects with `NetworkIdentity` replicate.
- **Opt-in per component**: a component type implements `NetReplicated` with explicit `writeState(NetWriter, baseline)` / `readState(NetReader)` and a stable `netTypeId`. No automatic reflection of all fields. Initial set (NET-02): `Transform` (position/rotation/scale), `RigidBody3D` linear/angular velocity, game-defined player state component (e.g. skate state enum/flags).
- **Spawn/despawn:** reliable `Spawn(netId, prefab, owner, initial state)` / `Despawn(netId)`; clients instantiate from the prefab library (ECS prefab instances) so no scene data crosses the wire. Late joiners receive full spawns for all live netIds.
- **Snapshots:** unreliable-sequenced, per client, **delta compressed against the last snapshot the client acked** (baseline tick kept in a ring buffer of 32 per client). Per-object: changed-bits mask + only changed fields; unchanged objects omitted; full resend if baseline is older than the ring (`baseline = 0`). Static objects cost 0 bytes.
- **Relevancy:** v1 = send all; hook `RelevancyFilter` interface (distance-based) reserved for later.
- **Client apply:** snapshots go to an interpolation buffer; remote objects render at `serverTime - interpDelay` (default 100 ms = 2 snapshots) with `Transform` lerp/slerp; limited extrapolation (≤ 250 ms) then freeze. Writes go through the ECS component mutation API (ECS-02 change notification) so caches stay valid.
- **Ownership/authority:** server owns all state. Owner client may *propose* via inputs only; clients never write replicated state directly (except predicted local object, reconciled).

## 6. RPCs

- Reliable messages, declared in code: `@Rpc(target = SERVER | CLIENT_OWNER | ALL_CLIENTS, channel = RELIABLE_ORDERED)` on functions of a `NetworkBehaviour`-style script/component, dispatched by **registered numeric id** (explicit registry, args encoded with `NetWriter`; supported arg types: primitives, String, Vector2/3f, Quaternionf, `NetId`).
- Server validates sender (owner check, rate limit, argument bounds); invalid ⇒ dropped + counted, repeated ⇒ kick.
- Scripts (SCR) get the same through the language-agnostic script API later (reported as `NEEDS SCR` when NET-02 is done).

## 7. Prediction & lag compensation (NET-03, design only)

- Client keeps a ring of (tick, input, predicted state). On each snapshot containing the local object, compare at the acked tick; if error > threshold, snap/ smooth-correct and replay remaining inputs. Requires a deterministic-enough player step function (`PlayerController` fixed step with no Bullet dependency or with kinematic character controller) → **NEEDS GPL/PHY** later.
- Lag compensation for hit queries: server keeps 250 ms history of collider transforms per netId and rewinds for the query. Not in v1 MVP.

## 8. Platform services (NET-04) interaction

`PlatformServices` (achievements, stats, leaderboards, rich presence, lobbies) is independent of `Transport`. `NoOpPlatformServices` is the default; Steam implementation loaded reflectively/optionally so the jar runs without steamworks4j natives. Lobby discovery yields a connect target (`IP:port` or Steam id) consumed by `Transport.connect`.

## 9. Package layout

- `engine/net/` — `Transport`, `Connection`, `UdpTransport`, `InMemoryTransport`, `NetWriter/NetReader`, `MessageRegistry`, `NetworkIdentity`, `NetReplicated`, `ReplicationServer/Client`, `SnapshotBuffer`, `NetSystem`s (phases: PreUpdate receive, FixedUpdate input apply/simulate, LateUpdate send).
- `engine/platform/` — `PlatformServices`, `NoOpPlatformServices`, later `steam/`.
- Net systems are normal ECS systems using ECS-03 phases; engine layer must not import `editor.*` (architecture guard).

## 10. Testing

Unit only, no sockets: codec round-trips and golden bytes, delta encode/decode against baselines, ack/reliability state machine driven with fake clock and lossy/reordering `InMemoryTransport`, interpolation math, RPC dispatch/validation.

## 11. Breaking changes / cross-domain needs

- No breaking change to existing code in NET-01 (doc only).
- Later (reported when needed): `NEEDS ECS`: stable component-mutation notification for replicated writes (ECS-02 covers) and prefab instantiate-by-id API; `NEEDS GPL`: player step function separable from Bullet for prediction; `NEEDS INP`: per-tick input sampling struct; `NEEDS PHY`: kinematic/character-controller option and collider history access.

## 12. Assumptions / open questions for the lead

1. Own UDP layer (§3) approved over KryoNet/Netty?
2. Tick 60 Hz / snapshot 20 Hz / 100 ms interpolation defaults acceptable?
3. Predict only the local player; remote Bullet bodies are interpolated, not simulated (§2).
4. Listen-server is a first-class mode (loopback transport).
5. Max 16 players for v1.
