# TASK

The orchestrator ran `./mvnw -B clean verify` on the host (with Docker, including every `*IT` test) for branch `{{BRANCH}}`, and it failed. Find the cause and fix it on this branch.

# CONTEXT

## Failing verify output (tail)

```text
{{VERIFY_OUTPUT}}
```

## Commits on this branch

!`git log {{TARGET_BRANCH}}..{{BRANCH}} --oneline`

# Project

- This is a Java 25 / Spring Boot 4 project built with Maven (use the wrapper `./mvnw`, never a global `mvn`).
- All application code lives in the `platform-api/` folder (package `com.fulfillflow.platform_api`). Run every Maven command from inside `platform-api/`.
- Follow the coding standards in @.sandcastle/CODING_STANDARDS.md.

# RULES

- Fix the real cause in production code, configuration or a genuinely wrong test. Never delete, disable, skip or weaken a test to make the build green, and do not change behaviour beyond what the original issue asked for.
- There is no Docker in this sandbox: you cannot run `*IT` tests or `./mvnw verify`. Reason from the output above, then run `./mvnw -B clean test` (always `clean`: stale files in `target/` make builds falsely green). The orchestrator re-runs `clean verify` on the host after you finish.
- Stay on the current branch: do not create, switch or rename branches, and do not push.
- Commit the fix with a message starting with `RALPH: fix verify —` followed by the cause and the fix. No AI attribution (no `Co-Authored-By:` trailer, no "Generated with Claude Code" line).
- If you cannot find the cause, do not commit; explain what you found.

Once complete, output <promise>COMPLETE</promise>.
