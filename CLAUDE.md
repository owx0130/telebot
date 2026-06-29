# telebot

A Telegram bot with two features: a shared photo gallery (upload photos with optional captions and retrieve a random one) and a Wordle game (play the daily NYT word in easy or hard mode). Wordle is a **Telegram Mini App** — a hosted web page (board + on-screen keyboard with NYT-style green/yellow/gray letters) launched from the bot and backed by an embedded HTTP/JSON API.

## Architecture

- **Language**: Java 25
- **Build**: Maven (`pom.xml`), shaded into a fat jar via `maven-shade-plugin` (main class `telebot.Main`)
- **Bot framework**: TelegramBots v9.2.1 (long-polling)
- **Storage**: Redis (via Jedis 7.2.1) — user session state, the photo gallery, and in-progress Wordle sessions all live in Redis
- **Web/HTTP**: JDK `com.sun.net.httpserver.HttpServer` serves the Wordle Mini App page + JSON API; `java.net.http.HttpClient` fetches the Wordle solution from NYT
- **Logging**: SLF4J + slf4j-simple
- **Config**: `.env` file loaded via dotenv-java (`BOT_TOKEN`, `REDIS_URL`, `NGROK_AUTHTOKEN`, optional `WEB_PORT`, `WEBAPP_DEV_AUTH_BYPASS`). The public Mini App URL comes from the ngrok tunnel.

The code is organized into feature/responsibility packages under `telebot`:

| Package | Responsibility |
|---------|----------------|
| `telebot` | `Main` (entry point) and `Bot` (composition root + update router) |
| `telebot.telegram` | `Messenger` — the single point of contact with the Telegram send API |
| `telebot.handler` | Feature controllers: `PhotoHandler`, `WordleHandler` |
| `telebot.game` | `Wordle` — pure, Telegram-agnostic game logic |
| `telebot.web` | Wordle Mini App backend: `WebServer`, `WordleApiHandler`, `TelegramAuth` |
| `telebot.storage` | Redis access: `RedisConnection` + per-feature stores/repositories |
| `telebot.model` | Plain data types: `UserState` enum, `Photo` record |

Layering: `Bot` wires everything and routes; handlers orchestrate a feature by calling `Messenger` (output), the storage classes (state), and (for Wordle) launching the Mini App. The web layer reuses the pure `Wordle` rules and `WordleSessionStore`. **Threading:** the bot runs on the long-polling single thread; the `WebServer` runs on its own single-threaded executor with its **own** `RedisConnection` — so each non-thread-safe `Jedis` connection is touched by exactly one thread.

## Key files

| File | Purpose |
|------|---------|
| `src/main/java/telebot/Main.java` | Entry point; loads `.env`, registers bot via `TelegramBotsLongPollingApplication`, blocks the main thread |
| `src/main/java/telebot/Bot.java` | Implements `LongPollingSingleThreadUpdateConsumer`; composition root that builds storage/messenger/handlers and routes each `Update` by `UserState` and message content |
| `src/main/java/telebot/telegram/Messenger.java` | Owns the `TelegramClient`; `sendText`/`sendPhoto`/`sendWebAppButton` (logs & swallows `TelegramApiException`) and `registerCommands` (`SetMyCommands`) |
| `src/main/java/telebot/handler/PhotoHandler.java` | Photo-gallery feature: upload flow, random photo, unexpected-photo reminder |
| `src/main/java/telebot/handler/WordleHandler.java` | Wordle controller: builds the Mini App URL and sends a `web_app` launch button (all game logic lives in the web layer) |
| `src/main/java/telebot/game/Wordle.java` | Pure Wordle logic: word-list validation, NYT solution fetch, guess evaluation, hard-mode checks |
| `src/main/java/telebot/web/WebServer.java` | Embedded `HttpServer`: serves the Mini App static page (`/web/*` on the classpath) and routes `/api/wordle/*`; single-thread executor + own `RedisConnection` |
| `src/main/java/telebot/web/WordleApiHandler.java` | JSON API (`/api/wordle/start`, `/guess`): reuses `Wordle` + `WordleSessionStore`, returns board + keyboard state, hides the answer until won |
| `src/main/java/telebot/web/TelegramAuth.java` | Validates Mini App `initData` (HMAC-SHA256 with bot token) → user id; dev bypass flag |
| `src/main/resources/web/index.html`, `style.css`, `app.js` | Mini App UI: board + QWERTY keyboard with green/yellow/gray letter graying, talks to the JSON API |
| `src/main/java/telebot/storage/RedisConnection.java` | Owns a `Jedis` connection and the `user_<id>` key convention; `AutoCloseable` (bot and web layer each construct their own) |
| `src/main/java/telebot/storage/UserStateStore.java` | Core per-user hash fields: `state` and `storedPhotoID` |
| `src/main/java/telebot/storage/WordleSessionStore.java` | In-progress Wordle session fields on the same user hash (written by the web API) |
| `src/main/java/telebot/storage/PhotoRepository.java` | The shared photo gallery (`photos` list + per-file caption keys) |
| `src/main/java/telebot/model/UserState.java` | Enum (`DEFAULT`, `AWAITING_PHOTO`, `AWAITING_CAPTION`, `UNKNOWN`) with `fromString` parser |
| `src/main/java/telebot/model/Photo.java` | `record Photo(String fileID, String caption)` |
| `src/main/resources/wordle-words.txt` | Bundled 5-letter word list used for guess validation |

## Bot commands

Registered programmatically in `Messenger.registerCommands` via `SetMyCommands`:

- `/random_photo` — sends a random photo from the gallery with its caption
- `/upload_photo` — starts a single-photo upload flow (see state machine)
- `/wordle` — starts a Wordle game in **hard mode**
- `/wordle_easy` — starts a Wordle game with normal rules

Mid-flow inputs (not in the registered menu): `/cancel` (abort upload or Wordle), `/skip` (upload with empty caption).

## Update routing

`consume(Update)` ignores updates without a message, then for each message: auto-registers the user (`UserStateStore.addUser`), reads `UserState`, and dispatches:

1. `AWAITING_PHOTO` → `photoHandler.handleAwaitingPhoto`
2. `AWAITING_CAPTION` → `photoHandler.handleAwaitingCaption`
3. else if text → `handleCommand` (slash-command switch)
4. else if photo → `photoHandler.handleUnexpectedPhoto`

`handleCommand` switch: `/random_photo`, `/upload_photo`, `/wordle` (hard), `/wordle_easy` (easy); unknown text falls through to a default reply.

`/wordle` and `/wordle_easy` send a `web_app` button that opens the Mini App; gameplay then happens entirely in the web page against the JSON API (no further bot updates). State-bearing inputs (`/cancel`, captions, `/skip`) are handled inside the respective handler, not in the command switch.

## State machine

```
DEFAULT
  ├─ /random_photo ──► send random photo & DEFAULT
  ├─ /upload_photo ──► AWAITING_PHOTO
  │                       ├─ photo with caption ──► upload & DEFAULT
  │                       ├─ photo without caption ──► store fileID ──► AWAITING_CAPTION
  │                       │                                               ├─ text ──► upload with caption & DEFAULT
  │                       │                                               └─ /skip ──► upload (empty caption) & DEFAULT
  │                       └─ /cancel ──► DEFAULT
  ├─ /wordle | /wordle_easy ──► send web_app launch button & DEFAULT (gameplay happens in the Mini App)
  └─ photo (no /upload_photo) ──► prompt to use /upload_photo & DEFAULT
```

Wordle no longer uses a Telegram `UserState`; once the Mini App opens, the page drives the game directly against the JSON API (see "Wordle Mini App").

## Redis schema

- `user_<chat_id>` — one hash per user holding both core and Wordle fields:
  - `state` (string) — current `UserState`
  - `storedPhotoID` (string) — Telegram file ID temporarily held during an upload
  - `wordleAnswer` (string) — today's solution for the active game
  - `wordleGuesses` (string) — comma-joined list of guesses so far
  - `wordleHardMode` (string) — `"true"` / `"false"`
- `photos` — list of Telegram file IDs (the gallery); new photos appended via `RPUSH`
- `<fileID>` — string key storing the caption for that file (empty string if no caption)

`UserState.UNKNOWN` is a fallback for unrecognised `state` strings; it is never dispatched (silently ignored).

## Storage API

**`UserStateStore`**

| Method | Redis operation |
|--------|----------------|
| `addUser(chat_id)` | `HSET user_<id> state DEFAULT storedPhotoID ""` (only if key doesn't exist) |
| `getUserState(chat_id)` | `HGET user_<id> state` → `UserState.fromString` |
| `setUserState(chat_id, state)` | `HSET user_<id> state <name>` |
| `getUserStoredPhotoID(chat_id)` | `HGET user_<id> storedPhotoID` |
| `setUserStoredPhotoID(chat_id, fileID)` | `HSET user_<id> storedPhotoID <fileID>` |

**`WordleSessionStore`** (all on the same `user_<id>` hash; absent fields read back as sensible defaults)

| Method | Redis operation |
|--------|----------------|
| `getWordleAnswer` / `setWordleAnswer` | `HGET` / `HSET user_<id> wordleAnswer` |
| `getWordleGuesses` / `setWordleGuesses` | `HGET` / `HSET user_<id> wordleGuesses` (comma-joined) |
| `isWordleHardMode` / `setWordleHardMode` | `HGET` / `HSET user_<id> wordleHardMode` |
| `clearWordleSession(chat_id)` | `HSET` resets `wordleAnswer`/`wordleGuesses`/`wordleHardMode` to defaults |

**`PhotoRepository`**

| Method | Redis operation |
|--------|----------------|
| `getRandomPhoto()` | `LLEN photos` → random index → `LINDEX` → `GET <fileID>` → `Photo` |
| `uploadPhoto(fileID, caption)` | `RPUSH photos <fileID>` + `SET <fileID> <caption>` |

## Wordle Mini App

The bot's `/wordle*` commands send a `web_app` button (`Messenger.sendWebAppButton`) pointing at the ngrok tunnel's public URL — or `http://localhost:<WEB_PORT>` when no tunnel is active — (with `?hard=true` for hard mode). Tapping it opens the hosted page inside Telegram; the page (`index.html`/`style.css`/`app.js`) renders the board and a QWERTY keyboard, graying each guessed letter to its best green > yellow > gray state — the NYT effect.

The page calls the JSON API served by the **same** embedded `WebServer` (single origin → no CORS). The word list never leaves the server; the answer is returned only once the player has won.

Wordle has **unlimited guesses**; a session ends only when the player guesses the answer. Hard mode enforces that revealed greens stay in place and revealed yellows are reused (`Wordle.hardModeViolation`), checked server-side in the API.

### Game logic

Pure, Telegram-agnostic. Constants: `WORD_LENGTH = 5`, tile states `GREEN = 2` / `YELLOW = 1` / 0 (gray).

| Member | Role |
|--------|------|
| `Wordle()` / `loadWordList()` | Loads `/wordle-words.txt` (5-letter words) into an in-memory set |
| `isValidWord(guess)` | Membership check against the word list |
| `fetchSolution()` (static) | GETs `nytimes.com/svc/wordle/v2/<date>.json` (date resolved in `Asia/Singapore`), regex-extracts `solution`; returns `null` on any failure |
| `puzzleDate()` (static) | The puzzle's calendar date (ISO `yyyy-MM-dd`, GMT+8) — the same date `fetchSolution()` fetches; surfaced to the Mini App UI |
| `evaluate(guess, answer)` (static) | Standard two-pass green/yellow/gray scoring → `int[]` |
| `hardModeViolation(priorGuesses, answer, guess)` (static) | Returns a human-readable reason a guess breaks hard mode, or `null` if allowed |

### API endpoints

| Endpoint (POST) | Params | Returns |
|-----------------|--------|---------|
| `/api/wordle/start` | `initData`, `hardMode` | Always seeds a fresh game for today (overwriting any stored session), so reopening the Mini App never resumes stale guesses. Board (`guesses[]` with per-tile `eval`), aggregated `keyboard` map, `hardMode`, `date`, `won`. |
| `/api/wordle/guess` | `initData`, `guess` | Validates length / `isValidWord` / `hardModeViolation`, appends the guess, persists via `WordleSessionStore`. Same board shape; `{ok:false, error}` on a rejected guess; `answer` only when `won`. |
| `/api/wordle/end` | `initData` | Clears the user's session via `clearWordleSession`. Best-effort cleanup called from the page's unload hook (`navigator.sendBeacon`) when the Mini App closes. |

Auth: `TelegramAuth.authenticate` validates Telegram Mini App `initData` (HMAC-SHA256 of the data-check-string with a `WebAppData`-keyed secret derived from the bot token) and returns the Telegram user id (== `chatId` in a private chat), used as the `WordleSessionStore` key. `WEBAPP_DEV_AUTH_BYPASS=true` skips verification for local curl testing.

## Build & run

```bash
Use IntelliJ bundled Maven to build:
"$MVN" clean package
java -jar target/telebot-1.0.jar
```

**Before building, verify `$MVN` is set:** run `echo $MVN` and confirm it prints a path. If it is empty, the user must add `MVN` to `.claude/settings.local.json` before proceeding.

Requires a `.env` file in the working directory:
```
BOT_TOKEN=<telegram bot token>
REDIS_URL=redis://<host>:<port>
NGROK_AUTHTOKEN=<ngrok authtoken>            # opens the public HTTPS tunnel for the Mini App
WEB_PORT=8080                                # optional, local bind port for the embedded server (default 8080)
WEBAPP_DEV_AUTH_BYPASS=false                 # optional, set true to skip initData validation for local testing
```

The embedded server binds to `WEB_PORT` (default `8080`). Telegram requires Mini App URLs to be **HTTPS**, so the server is exposed via an ngrok tunnel opened on startup (`NGROK_AUTHTOKEN`); its public HTTPS URL is what the Mini App button opens. If no tunnel is active, the button falls back to `http://localhost:<WEB_PORT>` (only reachable locally). No BotFather game registration is needed; `web_app` inline buttons just need an HTTPS URL.

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
