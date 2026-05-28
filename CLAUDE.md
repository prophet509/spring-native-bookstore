# CLAUDE.md (CAVEMAN COMPRESSED)

## MCP Setup
Templates: `mcp/`
* Claude Desktop: `mcp/claude_desktop_config.json`
* Windsurf: `~/.codeium/windsurf/mcp_config.json` (template: `mcp/windsurf_mcp_config.json`)
* OpenCode: `opencode.json` (template: `mcp/opencode.json`)
* Claude Code: `.mcp.json`
* VS Code / Cursor: `.vscode/mcp.json`
* Codex: `~/.codex/config.toml`

### Active MCP Servers & Commands
1. **codegraph**: serve codebase graph. Command: `codegraph serve --mcp`
2. **code-review-graph**: persistent codebase relationships, blast radius. Command: `uvx code-review-graph serve`
3. **claude-mem**: persistent session memory. Command: `node /Users/locpham/.claude/plugins/marketplaces/thedotmack/plugin/scripts/mcp-server.cjs`
4. **java-lsp**: semantic Java navigation. Command: `npx -y lsp-mcp-server`. *Needs `jls` on PATH, config `mcp/lsp-mcp.json`*
5. **java-app-modernization**: upgrade analysis. Command: `npx -y @microsoft/github-copilot-app-modernization-mcp-server@latest` (env `APPMOD_MCP_COLLECT_TELEMETRY=false`)
6. **bookstore-postgres-catalog**: DB. Command: `uvx postgres-mcp --access-mode=restricted` (env `DATABASE_URI=postgresql://user:password@localhost:5432/polardb_catalog`)
7. **bookstore-postgres-order**: DB. Command: `uvx postgres-mcp --access-mode=restricted` (env `DATABASE_URI=postgresql://user:password@localhost:5433/polardb_order`)
8. **bookstore-postgres-inventory**: DB. Command: `uvx postgres-mcp --access-mode=restricted` (env `DATABASE_URI=postgresql://user:password@localhost:5434/polardb_inventory`)
9. **bookstore-redis**: Cache. Command: `uvx --from redis-mcp-server@latest redis-mcp-server --url redis://localhost:6379/0`
10. **bookstore-keycloak**: Auth. Command: `npx -y keycloak-model-context-protocol` (env `KEYCLOAK_URL=http://localhost:8080`, `KEYCLOAK_ADMIN=user`, `KEYCLOAK_ADMIN_PASSWORD=password`)
11. **bookstore-kafka**: Stream. Command: `uvx mcp-kafka` (env `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`)
12. **bookstore-elasticsearch**: Search. Command: `uvx elasticsearch-mcp-server-es9` (env `ELASTICSEARCH_HOSTS=http://localhost:9200`)

---

## CodeGraph / Code-Review-Graph Rules
* **Before editing symbol**: MUST run `codegraph_impact` or CRG `detect-changes` check. Report blast radius (callers, callees, risk level).
* **If risk HIGH / CRITICAL**: MUST warn user first.
* **Symbol path between X & Y**: Use `codegraph_trace`.
* **Survey multiple symbols**: Use `codegraph_explore`.
* **Search symbol name**: Use `codegraph_search`.
* **Map task area**: Use `codegraph_context`.
* **Find affected test files**: run `git diff --name-only | codegraph affected --stdin`.
* **Index missing**: run `codegraph init && codegraph index`. Run `uvx code-review-graph build` to re-index CRG SQLite graph database.
* **No Find-and-Replace**: use `java-lsp` rename or IDE refactoring.

---

## RTK (Rust Token Killer) Commands
* Always prefix command with `rtk`. Saves 60-99% tokens.
* E.g. `rtk git status`, `rtk git diff`, `rtk cargo test`, `rtk npm run <script>`.
* Works with `&&`: `rtk git add . && rtk git commit -m "msg" && rtk git push`.
* Check savings: `rtk gain`. View history: `rtk gain --history`.
* Setup global hook: `rtk init -g`. Local setup: `rtk init`.

---

## Claude-Mem Tools
Use long-term session memory tools for query:
* `search`: semantic search over memories
* `timeline`: session events chronological
* `get_observations`: list captured knowledge
* `smart_search` / `smart_outline` / `smart_unfold`: context-aware navigation
* `build_corpus` / `query_corpus` / `prime_corpus`: corpus knowledge management

---

## LLM Coding Guidelines
*Bias caution over speed.*

### 1. Think First
* No assumptions. If confused, ask.
* Multiple options? Present all. No silent picking.
* Simpler way exists? Suggest. Push back.
* Unclear? Stop. Name confusion. Ask.

### 2. Simplicity First
* Minimum code. No speculative features.
* No single-use abstractions. No extra configurations.
* No dead-end error checks.
* Short over long: 200 lines ➔ 50 lines. Overcomplicated? Simplify.

### 3. Surgical Edits
* Touch only what requested. No "improvement" of surrounding code.
* No unsolicited refactoring. Match existing style.
* Dead code found? Mention only. Do not delete.
* Remove unused imports/vars caused by YOUR edits.

### 4. Goal-Driven
* Plan: `[Step] ➔ verify: [check]`.
* "Add validation" ➔ write failing test, make pass.
* "Fix bug" ➔ write reproducing test, make pass.
* "Refactor" ➔ run tests before/after.