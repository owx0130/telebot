---
name: feedback-github-mcp
description: Use GitHub MCP tools instead of git CLI for GitHub operations like push, commit, PR creation
metadata:
  type: feedback
---

Always use `mcp__github__*` tools for any GitHub-related operation. Only fall back to `git` or `gh` CLI if no relevant MCP tool exists for the task.

**Why:** User explicitly requested MCP tools be used whenever possible.

**How to apply:**
- Before reaching for `git push`, `gh pr create`, `gh api`, etc., first check whether an `mcp__github__*` tool covers the operation.
- Examples: use `mcp__github__push_files` to push, `mcp__github__create_pull_request` for PRs, `mcp__github__get_file_contents` to read remote files, `mcp__github__list_commits` to inspect history.
- Only use CLI (`git`, `gh`) as a last resort when no MCP tool can accomplish the task.
