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
mvn package           # produces out/telebot.jar (via IDEA artifact) or target/
java -jar out/telebot.jar
```

Requires a `.env` file in the working directory:
```
BOT_TOKEN=<telegram bot token>
REDIS_URL=redis://<host>:<port>
```
