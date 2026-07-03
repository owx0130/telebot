# Storage (Redis)

## Schema

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
