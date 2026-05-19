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

## Files

- `claude_desktop_config.json`: Claude Desktop `mcpServers` template.
- `windsurf_mcp_config.json`: Windsurf `mcpServers` template.
- `opencode.json`: OpenCode MCP template.
- `.kafka-mcp.json`: Kafka connection used by the `kafka-mcp` inspection package.
- `lsp-mcp.json`: Java LSP configuration used by the `java-lsp` MCP server.
- `AGENTS.md.update.patch`: Patch for `AGENTS.md`; the file is root-owned in this workspace and could not be edited directly by the current user.

## Code Intelligence MCP Servers

- `gitnexus`: runs `npx -y gitnexus mcp` and exposes indexed repository graph tools such as `gitnexus_query`, `gitnexus_context`, `gitnexus_impact`, `gitnexus_rename`, and `gitnexus_detect_changes`.
- `java-lsp`: runs `npx -y lsp-mcp-server` and exposes semantic Java navigation/refactoring tools through a Java language server.
- `java-app-modernization`: runs Microsoft's `@microsoft/github-copilot-app-modernization-mcp-server` for Java upgrade and modernization analysis. Telemetry is disabled with `APPMOD_MCP_COLLECT_TELEMETRY=false`.

## Java LSP Prerequisite

The `java-lsp` MCP entry expects a Java language server executable named `jls` on `PATH`. If it is not installed, GitNexus remains the primary code intelligence tool and the LSP server will fail to start until `jls` is available.

Use `gitnexus` first for repository-wide architecture, impact, callers/callees, and execution flows. Use `java-lsp` for precise editor-like symbol navigation, diagnostics, rename previews, and local Java refactorings.

## Research Notes

- Bruno stores collections as version-controlled plain-text `.bru` files. This repo uses `bruno/bookstore` with a `Local` environment.
- GitNexus exposes stdio MCP via `npx -y gitnexus mcp`.
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
