# TASK

Review the code changes on branch `{{BRANCH}}` and improve code clarity, consistency, and maintainability while preserving exact functionality.

# CONTEXT

## Branch diff

!`git diff {{TARGET_BRANCH}}...{{BRANCH}}`

## Commits on this branch

!`git log {{TARGET_BRANCH}}..{{BRANCH}} --oneline`

# Project

- This is a Java 25 / Spring Boot 4 project built with Maven (use the wrapper `./mvnw`, never a global `mvn`).
- All application code lives in the `platform-api/` folder (package `com.fulfillflow.platform_api`). Run every Maven command from inside `platform-api/`.

# Verification

After every change, from inside `platform-api/`:

1. Run `./mvnw -B compile test-compile` and make sure it succeeds (main and test code must compile).
2. Run the unit tests with `./mvnw -B test` and make sure they pass.

Do NOT run tests that use Testcontainers (and do not run `./mvnw verify`, which runs integration tests). There is no Docker inside this sandbox, so those tests cannot start their containers and will fail for reasons unrelated to your change. If you write a test that needs Testcontainers, name it `*IT.java` so it is treated as an integration test, make sure it compiles, and leave running it to CI. If a Testcontainers test is picked up by `./mvnw -B test`, exclude it (e.g. `-Dtest='!SomeIT'`) rather than trying to make it pass.

# REVIEW PROCESS

1. **Understand the change**: Read the diff and commits above to understand the intent.

2. **Analyze for improvements**: Look for opportunities to:
   - Reduce unnecessary complexity and nesting
   - Eliminate redundant code and abstractions
   - Improve readability through clear variable and function names
   - Consolidate related logic
   - Remove unnecessary comments that describe obvious code
   - Avoid nested ternary operators - prefer switch statements or if/else chains
   - Choose clarity over brevity - explicit code is often better than overly compact code

3. **Check correctness**:
   - Does the implementation match the intent? Are edge cases handled?
   - Are new/changed behaviours covered by tests?
   - Are there unsafe casts, `any` types, or unchecked assumptions?
   - Does the change introduce injection vulnerabilities, credential leaks, or other security issues?

4. **Maintain balance**: Avoid over-simplification that could:
   - Reduce code clarity or maintainability
   - Create overly clever solutions that are hard to understand
   - Combine too many concerns into single functions or components
   - Remove helpful abstractions that improve code organization
   - Make the code harder to debug or extend

5. **Apply project standards**: Follow the coding standards defined in @.sandcastle/CODING_STANDARDS.md

6. **Preserve functionality**: Never change what the code does - only how it does it. All original features, outputs, and behaviors must remain intact.

# EXECUTION

If you find improvements to make:

1. Make the changes directly on this branch
2. Run the commands from the Verification section above to ensure nothing is broken
3. Commit describing the refinements, without any AI attribution (no `Co-Authored-By:` trailer, no "Generated with Claude Code" line)

If the code is already clean and well-structured, do nothing.

Once complete, output <promise>COMPLETE</promise>.
