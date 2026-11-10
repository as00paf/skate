# AST-18: Protocol Buffers (design note)

Status: **revision 2, awaiting the human's approval.** Revision 1 was rejected as not grounded in this codebase. It
assumed a `skate-core` module, a JSON+base64 export bundle and JSON network messages, none of which exist. It also
left open questions 3 and 4 below a "no remaining open questions" line. No code has been written.

## 0. Human decisions (recorded 2026-11-07, kept)

1. **Protobuf version:** use protobuf 3.x (stability over newer Kotlin support in 4.x).
2. **Save game versioning:** per engine version, no migration logic for now.

Backlog scope (unchanged): protobuf only for runtime data (packed exports, network messages). Scenes, prefabs and
settings stay JSON so git diffs and merges (EDT-60) stay readable. Measure load time and size gains first.

## 1. What exists today

| Area | Code | Format |
|---|---|---|
| Build | One Gradle module, root project `skate` (`build.gradle.kts`, `settings.gradle`: `include(':tools:cli')`, `include(':tools:mcp')`). There is **no `skate-core` module**. kotlinx-serialization 1.7.3 (`kotlinx-serialization-json`, plugin `kotlin-serialization`). | |
| Scenes, prefabs, `.skateproject`, `.skmat`, `.meta`, settings | `Serializer` (`engine/assets/serialization/`), `AssetMeta`, `MaterialAsset`, `GameSettings` | JSON (kotlinx). Stays JSON (scope). |
| Export pack | `AssetsPacker` + `engine/assets/pack/PackBuilder.kt` | `game.bin` = 4-byte big-endian length + **atlas** (`Atlas = LinkedHashMap<extension, List<AssetInfo>>`, JSON) + data section (raw or per-entry gzip). `manifest.json` next to it: `PackManifest` (schema, sha256 per entry), JSON, used by `PackBuilder.verify`. |
| Pack reading | `game/GameBin.kt` (`Pack(file, file.readBytes())`: the **whole** file in memory), `engine/utils/AssetsResolver.kt` (`find`, `read` → `PackBuilder.extract`), `GameMain.loadProjectAndIcon` | Only the atlas is parsed at start; entries are extracted on demand. |
| Network | `engine/net/`: `NetWriter`/`NetReader` (`NetBuffer.kt`: deterministic little-endian, unsigned LEB128 varints, zig-zag), `Snapshots.kt` (`ObjectDelta(netId, mask, deltas)`, delta against a baseline), `Replication*`, `ReliabilityLayer`, `UdpTransport`, `DatagramTransport`, `PeerTransport` (Steam, NET) | **Custom binary**, already compact, delta- and bit-mask-encoded. Not JSON. |
| Save games | none | No save system exists yet. |

What this means for revision 1's claims:

- **"JSON is verbose" for exports:** only the atlas header is JSON. Asset bytes are raw, with no base64. Its size is
  one `AssetInfo` per packed file (path, type, position, size, rawSize, compressed, sha256).
- **"JSON network messages":** they are not JSON. `NetWriter` deltas are already smaller than protobuf would make
  them: protobuf has no delta/baseline encoding, so it would add tags per field.
- **Save games:** nothing to convert; the format choice is only for when a save system is designed.

## 2. Measure first (as the backlog asks)

Before any adoption, AST-18a measures on the Feature Showcase export and a synthetic 10 000-entry pack:

| Metric | How |
|---|---|
| Atlas size | bytes of the JSON atlas in `game.bin` |
| Atlas decode time | `Serializer.decode(Atlas)` vs. the same data as protobuf (both warm and cold JVM, median of 20) |
| Game start to first frame | timestamps logged in `GameMain` (pack read, atlas decode, first scene loaded) |
| Pack read | `GameBin.read` (`readBytes` of the whole file) time and memory, vs. a memory-mapped/random-access read |

- **Harness:** a dev-only `skate pack --stats <project>` CLI option (TLG) that prints these numbers. A unit test
  covers only the stats formatting (no timing assertions in tests).
- **Results** are recorded in this note (§6) before anything else is decided.

**Expectation, unverified until measured:**

- The atlas for ~100 assets is about 10–20 KB and decodes in a few milliseconds, which is negligible next to loading
  textures and models.
- The real start-up cost for big projects is reading all of `game.bin` into memory (REN-28 rev 2 flags
  `GameBin.kt:49` for streaming worlds; random-access reads are REN-28 task 19, AST-owned). Protobuf does not help
  with that.

## 3. Recommendation

| Data | Recommendation | Why |
|---|---|---|
| Scenes, prefabs, project, materials, settings | **JSON** (unchanged) | Scope; readable diffs (EDT-60). |
| Pack atlas (`game.bin` header) | **Keep JSON unless §2 shows the decode costs more than 5 % of start-up.** If it does: encode the atlas with `kotlinx-serialization-protobuf` (the same `@Serializable` `AssetInfo`), header flag + `PackManifest.SCHEMA` bump. | Small header; the gain is likely below noise. |
| `manifest.json` | **JSON** (unchanged) | Read by tools and `verify`, not by the game's hot path; humans and CI read it. |
| Network | **Keep `NetWriter`/`NetReader`** | Already compact, deterministic and delta-encoded; protobuf would be larger and would not replace the delta logic. Interop with non-Skate clients is not a goal (NET is Skate-to-Skate and Steam). |
| Save games (future) | **Protobuf wire format through `kotlinx-serialization-protobuf`**, decided when the save system is designed | Compact, tolerant to added fields (unknown fields skipped), no separate schema files to keep in sync. Per-engine-version, no migration (decision 2). |
| Pack reading at scale | **Random-access reads** (`FileChannel`/memory map + atlas offsets) instead of `readBytes` | The real start-up and memory cost; owned by REN-28 task 19 / AST. Format-independent. |

### 3.1 Library choice, if protobuf is used

| Option | Build impact | Fit |
|---|---|---|
| **`kotlinx-serialization-protobuf`** (same version line as the JSON one, 1.7.3) | One dependency, **no `protoc`, no codegen, no Gradle plugin**. Uses the existing `@Serializable` classes with `@ProtoNumber`. `ProtoBufSchemaGenerator` writes `.proto` files for outside tools. | **Recommended.** It writes the proto3-compatible wire format. |
| Google `protobuf-java` 3.x + `com.google.protobuf` Gradle plugin + `protoc` | Plugin and `protoc` download in a Windows-only Gradle build, generated sources under `build/`, Java builder classes beside our Kotlin data classes | Heavier for no measured need. |

Decision 1 says "protobuf 3.x". Both options produce **proto3 wire format**. Whether 3.x meant the Google 3.x library
or the proto3 format is Q1.

## 4. If adopted: rules

- **Schema evolution:** never renumber or retype a field. New fields are optional with defaults. Removed field numbers
  are reserved (`@ProtoNumber` gaps are documented in the class KDoc).
- **Versioning:** every binary format keeps a schema/version number next to it (`PackManifest.SCHEMA`; a save header).
  Decision 2: saves are tied to the engine version, with no migration code. A save from another engine version is
  refused with a clear message.
- **Debugging:** any protobuf payload has a JSON view through the same `@Serializable` class (decode protobuf, encode
  JSON). A `skate inspect <file>` CLI command (TLG) prints it. That replaces revision 1's proposed `proto-decode` tool
  and needs no `protoc`.
- **Architecture guard:** protobuf encoding stays out of the scene/prefab/project serializers. A guard test in
  `src/test/kotlin/com/pafoid/skate/architecture/` fails if `ProtoBuf` is used by `Serializer` for
  `FileKind.SCENE`/`PREFAB`/project files.

## 5. Tasks

| Task | Owner | Contents | Deps |
|---|---|---|---|
| AST-18a | skate-assets (+ TLG for the CLI flag) | The §2 measurements, `skate pack --stats`, results written into §6 of this note. | approval |
| AST-18b (conditional) | skate-assets | Protobuf atlas, only if §2 crosses the 5 % threshold: `kotlinx-serialization-protobuf` dependency, header flag, schema bump, `AssetsResolver`/`GameMain` read path, round-trip tests. | AST-18a |
| Random-access pack reads | skate-assets (REN-28 task 19) | Already planned in REN-28 rev 2; listed here because it is the real start-up fix. | REN-28 approval |
| Save format | owner of the future save system | Use §3/§4 rules. | a save-system design |

## 6. Measurements

(to be filled by AST-18a)

## 7. Tests (unit only)

- Stats formatting for `skate pack --stats`.
- If AST-18b happens:
  - atlas round trip JSON ↔ protobuf with the same `AssetInfo` values;
  - a pack written with a protobuf atlas reads through `AssetsResolver.find`/`read`;
  - an unknown added field is skipped by an older reader (forward compatibility);
  - the architecture guard (no protobuf for scenes, prefabs, project files).

## 8. Open questions (with recommended answers)

1. **"Protobuf 3.x" (decision 1):** did it mean Google's `protobuf-java` 3.x library, or the proto3 wire format?
   Recommended: the wire format, through `kotlinx-serialization-protobuf` (no `protoc`, no codegen, existing
   `@Serializable` classes).
2. **Adoption threshold:** recommended: convert the pack atlas only if it costs more than 5 % of game start-up in the
   §2 measurements; otherwise close AST-18 with JSON kept and random-access reads as the follow-up.
3. **Inspecting binary files** (revision 1's leftover question 3, `proto-decode`): recommended: a `skate inspect <file>`
   CLI command (TLG) that prints any of our binary formats as JSON through their `@Serializable` classes; it is only
   needed once a protobuf format exists.
4. **Formats that must stay JSON for outside tools** (revision 1's leftover question 4): recommended: scenes, prefabs,
   project files, `.skmat`, `.meta`, `manifest.json` and `game.settings.json` (players edit it) all stay JSON. Network
   messages need no outside interop (Skate-to-Skate and Steam), so they keep the existing `NetWriter` binary format.
