# Skate MCP server

Model Context Protocol server (stdio, newline-delimited JSON-RPC 2.0) that exposes the Skate CLI services
(`tools/cli`) plus project introspection. It is headless and never opens a window.

Build: `gradlew.bat :tools:mcp:installDist`, then run `tools/mcp/build/install/skate-mcp/bin/skate-mcp.bat`.

Client config example:

```json
{ "mcpServers": { "skate": { "command": "C:/path/to/tools/mcp/build/install/skate-mcp/bin/skate-mcp.bat" } } }
```

| Tool | Purpose |
|------|---------|
| `create_project`, `create_sample` | Create a project / the FeatureShowcase sample |
| `validate_project` | Validate project file and scenes |
| `export_project` | Validate, pack and export `game.bin` |
| `run_scene` | Run a scene headless, return engine stats |
| `list_scenes` | Scenes of a project |
| `list_entities` | Scene tree with component types (entity `path` = names joined by `/`) |
| `get_entity` | Full serialized components of an entity |
| `get_logs` | Engine log entries from the server session |

Scope note: introspection reads project files on disk (no live editor connection yet); live editor
state would need an editor-side bridge.
