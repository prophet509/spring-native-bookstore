# MCP Setup for Spring Native Bookstore

This directory contains local MCP client configuration templates for the bookstore development stack.

## Runtime Targets

- Postgres catalog: `postgresql://user:password@localhost:5432/polardb_catalog`
- Postgres order: `postgresql://user:password@localhost:5433/polardb_order`
- Postgres inventory: `postgresql://user:password@localhost:5434/polardb_inventory`
- Redis: `redis://localhost:6379/0`
- Kafka: `localhost:9092`
- Keycloak: `http://localhost:8080`, admin `user` / `password`

Start dependencies with `make compose-up` or `make infra-up` before using the servers.

## MCP Servers (canonical set — 12 servers)

All CLIs are configured with the same set:

| Server | Command | Purpose |
|--------|---------|---------|
| `codegraph` | `codegraph serve --mcp` | Indexed code knowledge graph (search, callers, callees, impact, trace) |
| `code-review-graph` | `uvx code-review-graph serve` | Persistent code-review graph |
| `claude-mem` | `node .../mcp-server.cjs` | Session long-term memory |
| `java-lsp` | `npx -y lsp-mcp-server` | Semantic Java navigation/refactoring (needs `jls` on PATH) |
| `java-app-modernization` | `npx -y @microsoft/github-copilot-app-modernization-mcp-server` | Java upgrade/modernization analysis |
| `bookstore-postgres-catalog` | `uvx postgres-mcp --access-mode=restricted` | Catalog DB (`:5432`, read-only) |
| `bookstore-postgres-order` | `uvx postgres-mcp --access-mode=restricted` | Order DB (`:5433`, read-only) |
| `bookstore-postgres-inventory` | `uvx postgres-mcp --access-mode=restricted` | Inventory DB (`:5434`, read-only) |
| `bookstore-redis` | `uvx redis-mcp-server` | Redis (`:6379`) |
| `bookstore-keycloak` | `npx -y keycloak-model-context-protocol` | Keycloak admin (`:8080`) |
| `bookstore-kafka` | `uvx mcp-kafka` | Kafka inspection (`:9092`, read-only) |
| `bookstore-elasticsearch` | `uvx elasticsearch-mcp-server-es9` | Elasticsearch 9.x (`:9200`) |

## Per-CLI Configuration

Each CLI reads MCP servers from a different file/format. The repo ships ready-to-use configs;
templates live in this `mcp/` directory for CLIs whose config lives outside the repo.

| CLI | Config file | Format | Status |
|-----|-------------|--------|--------|
| **OpenCode** | `opencode.json` (repo root) | `mcp` + `type:"local"` + `command` array + `environment` | committed |
| **Claude Code** | `.mcp.json` (repo root) | `mcpServers` + `command`/`args`/`env` | committed |
| **Kiro** | `.kiro/settings/mcp.json` (workspace) | `mcpServers` + `command`/`args`/`env` | committed |
| **Cursor** | `.cursor/mcp.json` | `mcpServers` + `type:"stdio"` + `command`/`args`/`env` | committed |
| **VS Code** | `.vscode/mcp.json` | `mcpServers` + `command`/`args`/`env` | committed |
| **Codex** | `~/.codex/config.toml` | `[mcp_servers.*]` TOML tables | template: `mcp/codex_config.toml` |
| **Claude Desktop** | `~/Library/Application Support/Claude/claude_desktop_config.json` | `mcpServers` + `command`/`args`/`env` | template: `mcp/claude_desktop_config.json` |
| **Windsurf** | `~/.codeium/windsurf/mcp_config.json` | `mcpServers` + `command`/`args`/`env` | template: `mcp/windsurf_mcp_config.json` |

### OpenCode, Claude Code, Kiro, Cursor, VS Code

No action needed — the config files are committed in the repo. Open the project and the CLI
picks them up automatically. In Kiro, run `/mcp` to verify servers loaded.

### Codex

Codex reads MCP servers from `~/.codex/config.toml`. Merge the blocks from
`mcp/codex_config.toml` into that file:

```bash
cat mcp/codex_config.toml >> ~/.codex/config.toml
```

### Claude Desktop

Copy the template to the Claude Desktop config location, then restart the app:

```bash
cp mcp/claude_desktop_config.json "$HOME/Library/Application Support/Claude/claude_desktop_config.json"
```

### Windsurf

Copy the template to the Windsurf config location, then reload MCP servers in Settings:

```bash
mkdir -p "$HOME/.codeium/windsurf"
cp mcp/windsurf_mcp_config.json "$HOME/.codeium/windsurf/mcp_config.json"
```

> Note: configs use absolute paths for this workspace
> (`/Users/locpham/Desktop/Workspace/spring-native-bookstore`) and the `claude-mem` server path.
> Adjust those paths if you clone the repo elsewhere or use a different user.

## Supporting Files

- `claude_desktop_config.json`: Claude Desktop `mcpServers` template.
- `windsurf_mcp_config.json`: Windsurf `mcpServers` template.
- `codex_config.toml`: Codex `[mcp_servers.*]` template.
- `opencode.json`: OpenCode MCP template (the active config is the repo-root `opencode.json`).
- `.kafka-mcp.json`: Kafka connection used by the `kafka-mcp` inspection package.
- `lsp-mcp.json`: Java LSP configuration used by the `java-lsp` MCP server.
- `AGENTS.md.update.patch`: Patch for `AGENTS.md`; the file is root-owned in this workspace and could not be edited directly by the current user.

## Code Intelligence MCP Servers

- `codegraph`: runs `codegraph serve --mcp` and exposes indexed knowledge graph tools such as `codegraph_context`, `codegraph_trace`, `codegraph_explore`, `codegraph_search`, `codegraph_callers`, `codegraph_callees`, `codegraph_impact`, and `codegraph_node`.
- `java-lsp`: runs `npx -y lsp-mcp-server` and exposes semantic Java navigation/refactoring tools through a Java language server.
- `java-app-modernization`: runs Microsoft's `@microsoft/github-copilot-app-modernization-mcp-server` for Java upgrade and modernization analysis. Telemetry is disabled with `APPMOD_MCP_COLLECT_TELEMETRY=false`.

## Java LSP Prerequisite

The `java-lsp` MCP entry expects a Java language server executable named `jls` on `PATH`. If it is not installed, CodeGraph remains the primary code intelligence tool and the LSP server will fail to start until `jls` is available.

Use `codegraph` first for repository-wide architecture, impact, callers/callees, and execution traces. Use `java-lsp` for precise editor-like symbol navigation, diagnostics, rename previews, and local Java refactorings.

## Broker & Search MCP Servers

- `bookstore-kafka`: runs `uvx mcp-kafka` and exposes 12 Kafka tools: list/describe/create topics, consume/produce messages, consumer group management, cluster info, broker listing, watermarks. Write operations disabled by default (read-only safe mode). Connects to `localhost:9092`.
- `bookstore-elasticsearch`: runs `uvx elasticsearch-mcp-server-es9` and exposes Elasticsearch 9.x tools: list indices, get mappings, search documents, execute ES|QL, get shards. Connects to `http://localhost:9200` (no auth in local dev).

## Research Notes

- Bruno stores collections as version-controlled plain-text `.bru` files. This repo uses `bruno/bookstore` with a `Local` environment.
- CodeGraph exposes stdio MCP via `codegraph serve --mcp`.
- Java semantic tooling is provided through `lsp-mcp-server`; the project config lives in `mcp/lsp-mcp.json`.
- Java modernization tooling is provided through `@microsoft/github-copilot-app-modernization-mcp-server`.
- Postgres uses `crystaldba/postgres-mcp` via `uvx postgres-mcp`; access mode is `restricted` by default for safer read-only database inspection.
- Redis uses the official `redis-mcp-server` package via `uvx`.
- Keycloak uses `keycloak-model-context-protocol` via `npx`.
- Kafka currently has less mature MCP server coverage than Postgres/Redis/Keycloak. This repo includes `kafka-mcp` configuration for Kafka inspection workflows; if you need full stdio MCP tools exposed to all clients, use this as the connection baseline for a project-local wrapper.

## Install Prerequisites

- Node.js/npm for `npx` based servers.
- `uv` for `uvx` based servers.
- Docker services running for Postgres, Redis, Kafka, and Keycloak.
