# Claude Project Tools

## MCP Setup

- MCP templates live in `mcp/`.
- Use `mcp/claude_desktop_config.json` for Claude Desktop.
- Expected MCP servers: `codegraph`, `java-lsp`, `java-app-modernization`, `bookstore-postgres-catalog`, `bookstore-postgres-order`, `bookstore-postgres-inventory`, `bookstore-redis`, `bookstore-keycloak`, `bookstore-kafka`, and `bookstore-elasticsearch`.
- Use `codegraph` before editing Java symbols for impact, context, callers/callees, execution traces.
- Use `java-lsp` for precise Java symbol navigation, diagnostics, rename previews, hover/type information, code actions, and call/type hierarchy.
- Use `java-app-modernization` for Java/Spring upgrade or migration analysis only.
- `java-lsp` reads `mcp/lsp-mcp.json` and expects `jls` on `PATH`; if unavailable, use CodeGraph and Gradle checks instead.
- Keep `APPMOD_MCP_COLLECT_TELEMETRY=false` for the modernization MCP server.

# CodeGraph — Code Intelligence

This project is indexed by CodeGraph (333 files, 2924 nodes, 5038 edges). Use CodeGraph MCP tools to understand code, assess impact, and navigate safely.

> If `.codegraph/` does not exist, run `codegraph init && codegraph index` first.

## Tool Selection

| Tool | Use For |
|------|---------|
| `codegraph_context` | Map a task / feature / area first — composes search + node + callers + callees in one call |
| `codegraph_trace` | "How does X reach Y" — the call path, each hop's body inline |
| `codegraph_explore` | Survey several related symbols' source in one budget-capped call |
| `codegraph_search` | Find a symbol by name |
| `codegraph_callers` / `codegraph_callees` | Walk call flow one hop at a time |
| `codegraph_impact` | Check what's affected before editing |
| `codegraph_node` | Get a single symbol's source / signature |
| `codegraph_files` | Get indexed file structure |
| `codegraph_status` | Check index health and statistics |

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `codegraph_impact` and report the blast radius (callers, callees, risk level) to the user.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `codegraph_search` + `codegraph_context` instead of grepping.
- When you need the call path between two symbols, use `codegraph_trace`.
- Pre-commit check: `git diff --name-only | codegraph affected --stdin` to find affected test files.

## Never Do

- NEVER edit a function, class, or method without first running `codegraph_impact`.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `java-lsp` rename or a refactoring IDE.
