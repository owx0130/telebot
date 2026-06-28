# telebot

A Telegram bot that manages a shared photo gallery. Users can upload photos (with optional captions) and retrieve a random photo from the collection.

## Architecture

- **Language**: Java 25
- **Build**: Maven (`pom.xml`)
- **Bot framework**: TelegramBots v9.2.1 (long-polling)
- **Storage**: Redis (via Jedis 7.2.1) — both user session state and the photo gallery live in Redis
- **Config**: `.env` file loaded via dotenv-java (`BOT_TOKEN`, `REDIS_URL`)

## Key files

| File | Purpose |
|------|---------|
| `src/main/java/telebot/Main.java` | Entry point; loads `.env`, registers the bot |
| `src/main/java/telebot/Bot.java` | Update handler; routes messages to state/command handlers |
| `src/main/java/telebot/Database.java` | All Redis I/O — user records, photo list, captions |
| `src/main/java/telebot/UserState.java` | Enum for per-user conversation state |

## Bot commands

- `/random_photo` — sends a random photo from the Redis list with its caption
- `/upload_photo` — starts a multi-step upload flow (see state machine below)

## State machine

```
DEFAULT
  └─ /upload_photo ──► AWAITING_PHOTO
                          ├─ photo with caption ──► upload & DEFAULT
                          ├─ photo without caption ──► store fileID ──► AWAITING_CAPTION
                          │                                               ├─ text ──► upload & DEFAULT
                          │                                               └─ /skip ──► upload (no caption) & DEFAULT
                          └─ /cancel ──► DEFAULT
```

## Redis schema

- `user_<chat_id>` — hash with fields `state` (string) and `storedPhotoID` (Telegram file ID, temp during upload)
- `photos` — list of Telegram file IDs (the gallery)
- `<fileID>` — string key storing the caption for that file

## Build & run

```bash
Use IntelliJ's bundled Maven to build:
"C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\plugins\maven\lib\maven3\bin\mvn.cmd" clean package
java -jar target/telebot-1.0.jar
```

Requires a `.env` file in the working directory:
```
BOT_TOKEN=<telegram bot token>
REDIS_URL=redis://<host>:<port>
```

<!-- code-review-graph MCP tools -->
## MCP Tools: code-review-graph

**IMPORTANT: This project has a knowledge graph. ALWAYS use the
code-review-graph MCP tools BEFORE using Grep/Glob/Read to explore
the codebase.** The graph is faster, cheaper (fewer tokens), and gives
you structural context (callers, dependents, test coverage) that file
scanning cannot.

### When to use graph tools FIRST

- **Exploring code**: `semantic_search_nodes` or `query_graph` instead of Grep
- **Understanding impact**: `get_impact_radius` instead of manually tracing imports
- **Code review**: `detect_changes` + `get_review_context` instead of reading entire files
- **Finding relationships**: `query_graph` with callers_of/callees_of/imports_of/tests_for
- **Architecture questions**: `get_architecture_overview` + `list_communities`

Fall back to Grep/Glob/Read **only** when the graph doesn't cover what you need.

### Key Tools

| Tool | Use when |
| ------ | ---------- |
| `detect_changes` | Reviewing code changes — gives risk-scored analysis |
| `get_review_context` | Need source snippets for review — token-efficient |
| `get_impact_radius` | Understanding blast radius of a change |
| `get_affected_flows` | Finding which execution paths are impacted |
| `query_graph` | Tracing callers, callees, imports, tests, dependencies |
| `semantic_search_nodes` | Finding functions/classes by name or keyword |
| `get_architecture_overview` | Understanding high-level codebase structure |
| `refactor_tool` | Planning renames, finding dead code |

### Workflow

1. The graph auto-updates on file changes (via hooks).
2. Use `detect_changes` for code review.
3. Use `get_affected_flows` to understand impact.
4. Use `query_graph` pattern="tests_for" to check coverage.
