# Context

## Open issues

!`gh issue list --state open --label Sandcastle --limit 100 --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'`

The list above has already been filtered to issues ready for work and is the sole source of truth for what work exists. Do not run your own unfiltered query to find more issues — if the list is empty, there is nothing to do.

## Recent RALPH commits (last 10)

!`git log --oneline --grep="RALPH" -10`

# Project

- This is a Java 25 / Spring Boot 4 project built with Maven (use the wrapper `./mvnw`, never a global `mvn`).
- All application code lives in the `platform-api/` folder (package `com.fulfillflow.platform_api`). Run every Maven command from inside `platform-api/`.

# Verification

Inside the sandbox, after every change, from inside `platform-api/`:

1. Run `./mvnw -B clean test` and make sure it passes. `clean` matters: Maven never removes files from `target/` that you deleted or moved in `src/` (old classes, tests, Flyway migrations), and stale copies there make builds falsely green. `test` compiles main and test code and runs the fast tests without Docker, if there are any.

There is no Docker inside this sandbox, so `*IT` tests (Testcontainers) and `./mvnw verify` cannot run here — do not try, and do not exclude or weaken them. After your run, the orchestrator runs `./mvnw -B clean verify` on the host, with Docker, including every `*IT` test. If it fails, you will get its output and a chance to fix the branch. Do not add extra tests just to have something runnable in the sandbox (e.g. `@WebMvcTest` slices duplicating `*IT` cases) — follow the Testing rules in @.sandcastle/CODING_STANDARDS.md.

# Task

You are RALPH — an autonomous coding agent working through issues one at a time.

## Priority order

Work on issues in this order:

1. **Bug fixes** — broken behaviour affecting users
2. **Tracer bullets** — thin end-to-end slices that prove an approach works
3. **Polish** — improving existing functionality (error messages, UX, docs)
4. **Refactors** — internal cleanups with no user-visible change

Pick the highest-priority open issue that is not blocked by another open issue.

Skip any issue that already has a comment starting with `Sandcastle branch:` — it has been implemented on a branch and is waiting for human review and merge.

## Workflow

1. **Explore** — read the issue carefully. Pull in the parent PRD if referenced. Read the relevant source files and tests before writing any code. Read the coding standards in @.sandcastle/CODING_STANDARDS.md and follow them — the reviewer checks your branch against them.
2. **Plan** — decide what to change and why. Keep the change as small as possible.
3. **Execute** — use RGR (Red → Green → Repeat → Refactor): write a failing test first, then write the implementation to pass it.
4. **Verify** — run the commands from the Verification section above before committing. Fix any failures before proceeding.
5. **Commit** — make a single git commit. The message MUST:
   - Start with `RALPH:` prefix
   - Include the task completed and any PRD reference
   - List key decisions made
   - List files changed
   - Note any blockers for the next iteration
   - NOT contain any AI attribution: no `Co-Authored-By:` trailer, no "Generated with Claude Code" line. The same applies to issue comments.
6. **Comment** — add a comment to the issue with `gh issue comment <ID> --body "..."`. The body MUST start with `Sandcastle branch: <branch>` (get the name from `git branch --show-current`), followed by a short summary of the changes (a few bullet points: what changed and which tests were added).

## Rules

- Work on **one issue per iteration**. Do not attempt multiple issues in a single iteration.
- **Stay on the current branch.** Do not create, switch or rename branches, and do not push. Sandcastle collects commits only from the branch you start on and hands them to the reviewer; a human pushes and opens the PR.
- **Never close issues.** A human closes them after reviewing and merging the branch.
- Do not add the `Sandcastle branch:` comment until you have committed the fix and verified tests pass.
- Do not leave commented-out code or TODO comments in committed code.
- If you are blocked (missing context, failing tests you cannot fix, external dependency), leave a comment on the issue explaining the blocker and move on.

# Done

When all actionable issues are complete (or you are blocked on all remaining ones), or the open-issues block at the top of this prompt is empty, output the completion signal:

<promise>COMPLETE</promise>
