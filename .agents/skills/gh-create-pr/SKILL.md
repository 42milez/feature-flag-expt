---
name: gh-create-pr
description: Create GitHub pull requests from local repository changes. Use when Codex is asked to publish changes, open or create a PR, make a draft PR, push a branch for review, or perform the full local git-to-GitHub PR flow for this repository.
---

# Create GitHub PR

Use this skill to turn local changes in this repository into a safe draft GitHub pull request. The
workflow uses local `git` for repository state, commits, and pushes; prefers the GitHub connector
for PR creation; and uses `gh` for authentication checks, repository metadata, and same-repository
fallback PR creation.

## Workflow

1. Confirm the intended scope.
   - Run `git status -sb` and inspect the diff before staging.
   - If the worktree contains unrelated or ambiguous changes, ask which files belong in the PR.
   - Do not stage unrelated user changes silently.
2. Check GitHub CLI availability and authentication.
   - Run `gh --version`.
   - Run `gh auth status`.
   - If `gh` is unavailable or unauthenticated, ask the user to install or authenticate it before
     continuing.
3. Determine the branch strategy.
   - If currently on `main`, `master`, or the repository default branch, create a new branch named
     `codex/<short-description>`.
   - Otherwise, stay on the current branch unless the user requests a new branch.
4. Stage and commit only the confirmed files.
   - Prefer explicit file paths whenever the worktree is mixed.
   - Use `git add -A` only when the user has confirmed the whole worktree belongs in scope.
   - Use a terse English Conventional Commit message that summarizes the PR.
5. Run repo-required validation.
   - If any file under `service/` changed, run these checks in order:
     1. `./gradlew :service:spotlessCheck`
     2. `./gradlew :service:compileJava`
     3. `./gradlew :service:test`
   - If no files under `service/` changed, skip the Gradle checks and state why.
6. Push the branch with upstream tracking.
   - Use `git push -u origin <current-branch>`.
7. Open a draft PR.
   - Prefer the GitHub connector after the branch is pushed.
   - Derive `repository_full_name` from `origin`, such as `42milez/feature-flag-expt`.
   - Stop if `origin` is not the intended PR base repository; do not create cross-repository or
     fork-based PRs with this skill.
   - Derive `head_branch` from the current branch.
   - Derive `base_branch` from the user request when specified; otherwise use the remote default
     branch.
   - If same-repository connector creation fails for a tool-specific reason, use
     `gh pr create --draft` as the fallback.
8. Request the repository owner as reviewer.
   - Request `42milez` as an individual reviewer immediately after the draft PR is created.
   - Prefer the GitHub connector reviewer request API; use
     `gh pr edit <pr-url-or-number> --add-reviewer 42milez` as the fallback.
   - If GitHub rejects the reviewer request, such as when the reviewer is also the PR author,
     keep the PR and report the exact reviewer request failure.
9. Report the result.
   - Include branch name, commit SHA, PR URL or number, base branch, requested reviewer,
     validation results, and any unresolved user action.

## Safety Rules

- Default to draft PRs unless the user explicitly requests ready-for-review.
- Never push or create a PR before the intended diff is understood.
- Never overwrite, revert, or discard existing user changes while preparing the PR.
- If checks fail, stop before PR creation unless the user explicitly wants a PR with failing checks.
- If network, authentication, or permission failures block GitHub operations, explain the blocker
  and the exact command that failed.

## Commit Messages

Use Conventional Commits 1.0.0 for every commit created by this skill:

```text
<type>[optional scope]: <description>
```

- Use `feat` for a user-visible feature and `fix` for a bug fix.
- Use common supporting types when they fit better, such as `docs`, `test`, `refactor`, `build`,
  `ci`, `chore`, or `perf`.
- Add a scope when it makes the affected area clearer, such as `docs`, `service`, `security`, or
  `observability`.
- Mark breaking changes with `!` before the colon or a `BREAKING CHANGE:` footer.
- Keep the first line short, imperative, and English.

Examples:

```text
docs: add GitHub PR creation skill
feat(security): require authentication for protected routes
fix(service): preserve validation error response schema
```

## PR Text

Write the PR title and body in English. Use concise Markdown prose that covers:

- What changed.
- Why it changed.
- User or developer impact.
- Root cause when the PR fixes a bug.
- Validation commands and results.
