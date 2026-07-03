# Build & run

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
