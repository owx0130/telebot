# Wordle Mini App

The bot's `/wordle*` commands send a `web_app` button (`Messenger.sendWebAppButton`) pointing at the ngrok tunnel's public URL — or `http://localhost:<WEB_PORT>` when no tunnel is active — (with `?hard=true` for hard mode). Tapping it opens the hosted page inside Telegram; the page (`index.html`/`style.css`/`app.js`) renders the board and a QWERTY keyboard, graying each guessed letter to its best green > yellow > gray state — the NYT effect.

The page calls the JSON API served by the **same** embedded `WebServer` (single origin → no CORS). The word list never leaves the server; the answer is returned only once the player has won.

Wordle has **unlimited guesses**; a session ends only when the player guesses the answer. Hard mode enforces that revealed greens stay in place and revealed yellows are reused (`Wordle.hardModeViolation`), checked server-side in the API.

## Web server

`WebServer` is the embedded JDK `HttpServer` that hosts the whole Mini App. It's the composition root for the web feature: on construction it builds its **own** `RedisConnection` → `WordleSessionStore`, a `TelegramAuth`, and the `WordleApiHandler`, then registers two HTTP contexts:

- `/` → `StaticHandler`, serving the page assets from the classpath under `/web` (`index.html`/`style.css`/`app.js`), with a `..` path-traversal guard.
- `/api/wordle/` → `WordleApiHandler`, the JSON API below.

It runs on a **single-threaded executor**, so its non-thread-safe `Jedis` connection is only ever touched by that one thread — independent of the bot's own connection. `start()` boots the server and then opens the ngrok tunnel (`startTunnel()`); tunnel failure is non-fatal (the app keeps serving on localhost). `getPublicUrl()` exposes the tunnel's HTTPS URL for the bot's Mini App button. See [build-and-run.md](build-and-run.md) for the port/tunnel config.

## Auth

`TelegramAuth.authenticate` validates Telegram Mini App `initData` (HMAC-SHA256 of the data-check-string with a `WebAppData`-keyed secret derived from the bot token) and returns the Telegram user id (== `chatId` in a private chat), used as the `WordleSessionStore` key. `WEBAPP_DEV_AUTH_BYPASS=true` skips verification for local curl testing.

## Game logic

Lives in `Wordle.java` (`telebot.game`). Pure, Telegram-agnostic, and **all-static** (not instantiable — the word list loads once into a static set at class load). Constants: `WORD_LENGTH = 5`, tile states `GREEN = 2` / `YELLOW = 1` / 0 (gray).

| Member | Role |
|--------|------|
| `loadWordList()` (static) | Loads `/wordle-words.txt` (5-letter words) into the static in-memory set at class load |
| `isValidWord(guess)` (static) | Membership check against the word list |
| `fetchSolution()` (static) | GETs `nytimes.com/svc/wordle/v2/<date>.json` (date resolved in `Asia/Singapore`), regex-extracts `solution`; returns `null` on any failure |
| `puzzleDate()` (static) | The puzzle's calendar date (ISO `yyyy-MM-dd`, GMT+8) — the same date `fetchSolution()` fetches; surfaced to the Mini App UI |
| `evaluate(guess, answer)` (static) | Standard two-pass green/yellow/gray scoring → `int[]` |
| `hardModeViolation(priorGuesses, answer, guess)` (static) | Returns a human-readable reason a guess breaks hard mode, or `null` if allowed |

## API endpoints

Handled in `WordleApiHandler.java` (`telebot.web`), which routes each path to its handler method and reuses the pure `Wordle` logic + `WordleSessionStore`.

| Endpoint (POST) | Handler | Description |
|-----------------|---------|-------------|
| `/api/wordle/start` | `handleStart` | Seeds a fresh game for today and returns the (empty) board. |
| `/api/wordle/guess` | `handleGuess` | Validates and records a guess; returns the updated board (`answer` only when won). |
| `/api/wordle/end` | `handleEnd` | Clears the user's session on Mini App close (best-effort). |
