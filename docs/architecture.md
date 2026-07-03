# Architecture

- **Language**: Java 25
- **Build**: Maven (`pom.xml`), shaded into a fat jar via `maven-shade-plugin` (main class `telebot.Main`)
- **Bot framework**: TelegramBots v9.2.1 (long-polling)
- **Storage**: Redis (via Jedis 7.2.1) — user session state and the photo gallery live in Redis
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

**Layering**: `Bot` wires everything and routes; handlers orchestrate a feature by calling `Messenger` (output) and the storage classes (state). The bot itself runs on the long-polling single thread. The Wordle Mini App's web layer (`WebServer` + JSON API) and its threading model are documented in [wordle.md](wordle.md).

**Entry points:** `telebot.Main` is the process entry point (loads `.env`, registers the bot, blocks the main thread); `telebot.Bot` is the composition root that builds storage/messenger/handlers and routes each `Update`. Start there; use the code-review-graph MCP tools to explore individual classes from there.

## Update routing

`consume(Update)` ignores updates without a message, then for each message: auto-registers the user (`UserStateStore.addUser`), reads `UserState`, and dispatches:

1. `AWAITING_PHOTO` → `photoHandler.handleAwaitingPhoto`
2. `AWAITING_CAPTION` → `photoHandler.handleAwaitingCaption`
3. else if text → `handleCommand` (slash-command switch)
4. else if photo → `photoHandler.handleUnexpectedPhoto`

`handleCommand` switch: `/random_photo`, `/upload_photo`, `/wordle` (hard), `/wordle_easy` (easy); unknown text falls through to a default reply.

`/wordle` and `/wordle_easy` send a `web_app` button that opens the Mini App; the game then runs entirely in the web page. State-bearing inputs (`/cancel`, captions, `/skip`) are handled inside the respective handler, not in the command switch.

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

Wordle no longer uses a Telegram `UserState`; once the Mini App opens, the page drives the game directly against the JSON API.

## Bot commands

Registered programmatically in `Messenger.registerCommands` via `SetMyCommands`:

- `/random_photo` — sends a random photo from the gallery with its caption
- `/upload_photo` — starts a single-photo upload flow (see state machine)
- `/wordle` — starts a Wordle game in **hard mode**
- `/wordle_easy` — starts a Wordle game with normal rules

Mid-flow inputs (not in the registered menu): `/cancel` (abort upload or Wordle), `/skip` (upload with empty caption).
