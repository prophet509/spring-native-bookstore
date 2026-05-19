# Claude Project Tools

## MCP Setup

- MCP templates live in `mcp/`.
- Use `mcp/claude_desktop_config.json` for Claude Desktop.
- Expected MCP servers: `gitnexus`, `java-lsp`, `java-app-modernization`, `bookstore-postgres-catalog`, `bookstore-postgres-order`, `bookstore-postgres-inventory`, `bookstore-redis`, and `bookstore-keycloak`.
- Use `gitnexus` before editing Java symbols for impact, context, callers/callees, execution flows, and safe renames.
- Use `java-lsp` for precise Java symbol navigation, diagnostics, rename previews, hover/type information, code actions, and call/type hierarchy.
- Use `java-app-modernization` for Java/Spring upgrade or migration analysis only.
- `java-lsp` reads `mcp/lsp-mcp.json` and expects `jls` on `PATH`; if unavailable, use GitNexus and Gradle checks instead.
- Keep `APPMOD_MCP_COLLECT_TELEMETRY=false` for the modernization MCP server.

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **spring-native-bookstore** (1526 symbols, 2534 relationships, 31 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/spring-native-bookstore/context` | Codebase overview, check index freshness |
| `gitnexus://repo/spring-native-bookstore/clusters` | All functional areas |
| `gitnexus://repo/spring-native-bookstore/processes` | All execution flows |
| `gitnexus://repo/spring-native-bookstore/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->
