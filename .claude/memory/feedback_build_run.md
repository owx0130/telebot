---
name: feedback-build-run
description: Use exact build and run commands from CLAUDE.md, no paraphrasing or modification
metadata:
  type: feedback
---

When building or running the project, use the commands exactly as written in CLAUDE.md — no paraphrasing, no substitutions, no shortcuts.

**Why:** The user explicitly wants the exact commands from CLAUDE.md used verbatim.

**How to apply:**
- Build: `"$MVN" clean package`
- Run: `java -jar target/telebot-1.0.jar`
- Do not use `/run` skill, `mvn` directly, or any variant of these commands. Copy them character-for-character from CLAUDE.md.
