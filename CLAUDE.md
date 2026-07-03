# telebot

A Telegram bot with two features: a shared **photo gallery** (upload photos with optional captions, retrieve a random one) and a **Wordle** game (the daily NYT word, easy or hard mode). Wordle is a **Telegram Mini App** — a hosted web page (board + on-screen keyboard with NYT-style green/yellow/gray letters) launched from the bot and backed by an embedded HTTP/JSON API.

**Stack:** Java 25 · Maven (shaded fat jar, main class `telebot.Main`) · TelegramBots v9.2.1 (long-polling) · Redis via Jedis · JDK `HttpServer` for the Mini App · SLF4J · dotenv-java config.

Code is organized into feature/responsibility packages under `telebot`: `telegram` (send API), `handler` (feature controllers), `game` (pure Wordle logic), `web` (Mini App backend), `storage` (Redis), `model` (data types). See the docs below.

**On startup, always read [docs/code-review-graph.md](docs/code-review-graph.md)** — this project has a knowledge graph; prefer the code-review-graph MCP tools over Grep/Glob/Read for exploration.

## Documents (read on-demand)

This file is a map; the details live in `docs/`. **Before editing code or answering a question in an area that a doc covers, always read that doc first**.

- [docs/architecture.md](docs/architecture.md) — packages, key files, layering/threading, update routing, state machine, bot commands
- [docs/storage.md](docs/storage.md) — Redis schema and the storage API (`UserStateStore`, `WordleSessionStore`, `PhotoRepository`)
- [docs/wordle.md](docs/wordle.md) — Wordle Mini App: game logic, JSON API endpoints, Mini App auth
- [docs/build-and-run.md](docs/build-and-run.md) — build commands, `.env` config, ngrok tunnel

## Instructions

- Whenever you change anything in the code, provide a summary at the end of your response that details each specific change and the line number of the file where the change was made.
