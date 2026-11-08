# AST-18 — Protocol Buffers for runtime data (design note)

Status: **proposal, awaiting human approval**. No code has been written.

## Goal

Evaluate Protocol Buffers (protobuf) as a serialization format for Skate's **runtime data** only — packed exports and network messages. Scenes, prefabs and project settings stay JSON to keep git diffs readable (EDT-60). Measure load time and file size gains before deciding on adoption scope.

## Decision summary

| Topic | Decision |
|---|---|
| Scope | **Runtime data only**: packed export bundles, network messages, save games. Not scenes, prefabs or project settings. |
| Library | [protobuf-jvm](https://github.com/protocolbuffers/protobuf/tree/java) (official Kotlin/JVM support via `protoc` codegen). Version: 3.x for compatibility with existing network libraries. |
| Schema location | `skate-core/src/main/proto/` — `.proto` files checked into version control alongside the engine source. |
| Build integration | Gradle plugin (`com.google.protobuf`) generates Kotlin classes from `.proto` files at build time. Output goes to `build/generated/source/proto/`. |
| JSON fallback | All `.proto` messages support JSON serialization via `JsonFormat` (official protobuf utility) for debugging and config interchange. |

## 1. Why protobuf?

### 1.1 Problems with current approach

- **JSON is verbose**: packed export manifests, network messages and save games are large text files that take longer to serialize/deserialize.
- **No schema enforcement**: JSON can drift; missing fields cause silent defaults or crashes at runtime.
- **Network overhead**: sending JSON over the wire (for future multiplayer) wastes bandwidth and CPU on serialization.

### 1.2 Why not protobuf for everything?

- **Git diffs**: protobuf binary format is unreadable in `git diff`. Scenes, prefabs and settings are edited by hand and committed; they need to stay human-readable.
- **Tooling complexity**: adding `protoc` to the build adds a native dependency that must be installed on all developer machines and CI.
- **Debugging**: JSON can be inspected with any text editor; protobuf requires a schema-aware viewer (`protoc --decode`).

### 1.3 The hybrid approach

| Data type | Format | Reason |
|---|---|---|
| Scene files (`.skn`) | JSON | Human-editable, git-friendly |
| Prefabs (`.prefab`) | JSON | Human-editable, git-friendly |
| Project settings (`project.json`) | JSON | Human-editable |
| Packed export manifests | **protobuf** | Large binary blobs; no human editing needed |
| Network messages | **protobuf** | Bandwidth efficiency, schema enforcement |
| Save games (`.skv`) | **protobuf** | Fast I/O, compact storage |

## 2. Schema design

### 2.1 Core message types

```proto
// Packed export bundle manifest
message ExportBundle {
  string version = 1;           // "1.0"
  repeated AssetEntry assets = 2;
  uint64 totalSizeBytes = 3;
  string compression = 4;       // "none", "zlib", "lz4"
}

message AssetEntry {
  string path = 1;              // relative path in the bundle
  uint64 sizeBytes = 2;
  bytes data = 3;               // compressed payload (inline for small assets)
  string hash = 4;              // SHA-256 of original content
}

// Network message envelope
message NetMessage {
  uint32 messageId = 1;
  string type = 2;              // fully qualified class name
  bytes payload = 3;            // serialized message-specific data
  uint64 timestamp = 4;         // server time in ms
}

// Save game state
message SaveGame {
  string version = 1;
  string projectId = 2;
  repeated GameObjectState objects = 3;
  map<string, string> metadata = 4;  // custom key-value pairs
}

message GameObjectState {
  uint64 id = 1;                // persistent GameObject ID (EDT-60)
  string name = 2;
  Vec3 position = 3;
  Vec3 rotation = 4;            // stored as quaternion (x, y, z, w)
  Vec3 scale = 5;
  repeated ComponentState components = 6;
}

message ComponentState {
  string type = 1;              // e.g., "Transform", "Rigidbody"
  bytes data = 2;               // component-specific serialized data
}

// Vector types (reusable)
message Vec3 { float x = 1; float y = 2; float z = 3; }
message Quat { float x = 1; float y = 2; float z = 3; float w = 4; }
```

### 2.2 Kotlin codegen

The `protobuf-kotlin` plugin generates idiomatic Kotlin classes:

```kotlin
// Generated from ExportBundle.proto
val bundle = ExportBundle.newBuilder()
    .setVersion("1.0")
    .addAssets(assetEntry)
    .build()

val bytes: ByteArray = bundle.toByteArray()  // serialize
val decoded = ExportBundle.parseFrom(bytes)   // deserialize
```

### 2.3 JSON interchange for debugging

```kotlin
// Serialize to JSON (for debugging / config files)
val json = JsonFormat.printer().print(bundle)

// Parse from JSON
val bundle = JsonFormat.parser().merge(json, ExportBundle.newBuilder()).build()
```

## 3. Build integration

### 3.1 Gradle setup (`skate-core/build.gradle.kts`)

```kotlin
plugins {
    id("com.google.protobuf") version "0.9.4"
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.5"
    }
    plugins {
        id("kotlin") {
            artifact = "io.grpc:protoc-gen-kotlin:1.4.1"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("kotlin") {
                    outputSubDir = "generated/source/proto"
                }
            }
        }
    }
}

sourceSets {
    main {
        proto {
            srcDir("src/main/proto")
        }
        kotlin {
            srcDir("build/generated/source/proto/main/kotlin")
        }
    }
}
```

### 3.2 Developer setup

- `protoc` is bundled via the Gradle plugin (downloaded automatically). No manual install needed.
- `.proto` files are in `skate-core/src/main/proto/`.
- Generated Kotlin code goes to `build/generated/source/proto/main/kotlin/` and is **not** committed to git (added to `.gitignore`).

## 4. Packed export integration

### 4.1 Current flow (JSON)

```
PackBuilder → iterate assets → write JSON manifest + raw bytes → zip
```

### 4.2 New flow (protobuf)

```
PackBuilder → iterate assets → build ExportBundle protobuf → serialize to ByteArray → compress with zlib/lz4 → write to file
```

The export format changes from:
```json
{
  "version": "1.0",
  "assets": [
    { "path": "models/car.glb", "sizeBytes": 12345, "data": "base64..." }
  ]
}
```

To a binary protobuf message (compact, no base64 overhead).

### 4.3 Size comparison (estimated)

| Format | Manifest size (100 assets) | Load time (Feature Showcase) |
|---|---|---|
| JSON (current) | ~50 KB | Baseline |
| Protobuf | ~8 KB (~84% reduction) | TBD (measure) |
| Protobuf + zlib | ~3 KB (~94% reduction) | TBD (measure) |

## 5. Network message integration (future, NET-01)

Protobuf messages are serialized to `ByteArray` and sent over the network:

```kotlin
// Sending
val msg = NetMessage.newBuilder()
    .setMessageId(nextId())
    .setType("PlayerMove")
    .setPayload(playerMove.toByteArray())
    .build()
channel.write(msg.toByteArray())

// Receiving
val raw = channel.read()
val envelope = NetMessage.parseFrom(raw)
val payload = when (envelope.type) {
    "PlayerMove" -> PlayerMove.parseFrom(envelope.payload)
    // ...
}
```

## 6. Test plan

1. **Schema round-trip**: create a `SaveGame` with 50 GameObjects, serialize to protobuf, deserialize, verify all fields match (position, rotation, components).
2. **Export bundle size**: pack the Feature Showcase with JSON manifest vs. protobuf; measure file size difference and load time difference.
3. **Compression comparison**: test zlib + protobuf vs. lz4 + protobuf; compare compression ratio and decompression speed.
4. **JSON interchange**: serialize a `SaveGame` to JSON, verify it's human-readable and parseable by the existing JSON loader (for debugging).
5. **Large bundle stress test**: pack 10,000 small assets into an `ExportBundle`; measure serialization time and memory usage.
6. **Backward compatibility**: modify a message field (add optional field), verify old clients can still parse the new format (skip unknown fields).

## 7. Risks

- **protoc installation**: while the Gradle plugin handles downloading, some firewalls/air-gapped environments may block it. Document manual install as fallback.
- **Schema evolution**: adding/removing fields requires care. Use only `optional` and `repeated` fields; avoid changing field numbers or types of existing fields.
- **Debugging difficulty**: binary protobuf files require `protoc --decode` to inspect. Provide a CLI tool (`skate-cli proto-decode`) for convenience.
- **Kotlin codegen maturity**: `protoc-gen-kotlin` is newer than the C++/Java generators; test thoroughly with complex message types (nested messages, maps, oneofs).

## 8. Decisions

Human decisions recorded on 2026-11-07:

1. **Protobuf version**: Use protobuf 3.x (stability over newer Kotlin support in 4.x).
2. **Save game versioning**: Per-engine-version, no migration logic for now.

## 9. Open questions

_No remaining open questions._
3. Should we add a `skate-cli proto-decode` tool for inspecting protobuf files? (Proposed: yes, as part of TLG tasks.)
4. Are there any message types that should stay JSON for interoperability with external tools? (e.g., network messages sent to non-Skate clients.)
