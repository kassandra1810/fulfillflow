// Sequential Reviewer — implement-then-review loop
//
// This template drives a two-phase workflow per issue:
//   Phase 1 (Implement): A sonnet agent picks an open issue, works on it
//                        on a dedicated branch, commits the changes, and signals
//                        completion.
//   Phase 2 (Review):    A second sonnet agent reviews the branch diff and either
//                        approves it or makes corrections directly on the branch.
//
// Both phases share a single sandbox created via createSandbox(), so the
// implementer and reviewer work on the same explicit branch.
//
// The sandbox has no Docker, so agents cannot run the *IT tests
// (Testcontainers). After each phase the orchestrator runs `./mvnw -B clean verify`
// on the host, in the sandbox's worktree, and on failure gives a fixer agent
// the output, up to MAX_FIX_ATTEMPTS times.
//
// The outer loop repeats up to MAX_ITERATIONS times, processing one issue per
// iteration and stopping early once the backlog is exhausted (an implement
// phase that produces no commits). This is a middle-complexity option between
// the simple-loop (no review gate) and the parallel-planner (concurrent
// execution with a planning phase).
//
// Usage:
//   npx tsx .sandcastle/main.mts
// Or add to package.json:
//   "scripts": { "sandcastle": "npx tsx .sandcastle/main.mts" }

import { spawnSync } from "node:child_process";
import { join } from "node:path";
import * as sandcastle from "@ai-hero/sandcastle";
import { docker } from "@ai-hero/sandcastle/sandboxes/docker";

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

// Maximum number of implement→review cycles to run before stopping.
// Each cycle works on one issue. Raise this to process more issues per run.
const MAX_ITERATIONS = 10;

// How many times a fixer agent may try to repair a failing host verify
// before the branch is left for a human.
const MAX_FIX_ATTEMPTS = 2;

// Lines from the end of the Maven output handed to the fixer agent.
const VERIFY_OUTPUT_TAIL_LINES = 200;

// ---------------------------------------------------------------------------
// Host verification
// ---------------------------------------------------------------------------

type VerifyResult = { ok: boolean; output: string };

// Runs the full build, including *IT tests, on the host where Docker exists.
function hostVerify(worktreePath: string): VerifyResult {
  const result = spawnSync("./mvnw", ["-B", "clean", "verify"], {
    cwd: join(worktreePath, "platform-api"),
    encoding: "utf8",
    maxBuffer: 64 * 1024 * 1024,
  });
  const output = `${result.stdout ?? ""}${result.stderr ?? ""}${result.error ? `\n${result.error}` : ""}`;
  return { ok: result.status === 0, output };
}

function tail(text: string, lines: number): string {
  return text.trimEnd().split("\n").slice(-lines).join("\n");
}

// Verifies the branch on the host; on failure lets a fixer agent repair it.
// Returns true once verify passes, false if it still fails after all attempts.
async function verifyWithFixes(
  sandbox: sandcastle.Sandbox,
  branch: string,
  stage: string,
): Promise<boolean> {
  for (let attempt = 0; ; attempt++) {
    console.log(`\nHost verify (${stage}${attempt ? `, after fix ${attempt}` : ""})...`);
    const verify = hostVerify(sandbox.worktreePath);
    if (verify.ok) {
      console.log("Host verify passed.");
      return true;
    }
    console.log("Host verify FAILED.");
    if (attempt === MAX_FIX_ATTEMPTS) {
      return false;
    }

    const fix = await sandbox.run({
      name: `fixer-${stage}-${attempt + 1}`,
      maxIterations: 1,
      agent: sandcastle.claudeCode("claude-opus-4-8"),
      promptFile: "./.sandcastle/fix-prompt.md",
      promptArgs: {
        BRANCH: branch,
        VERIFY_OUTPUT: tail(verify.output, VERIFY_OUTPUT_TAIL_LINES),
      },
    });
    if (!fix.commits.length) {
      console.log("Fixer made no commits; leaving the branch for a human.");
      return false;
    }
  }
}

const failedBranches: string[] = [];

// ---------------------------------------------------------------------------
// Main loop
// ---------------------------------------------------------------------------

for (let iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
  console.log(`\n=== Iteration ${iteration}/${MAX_ITERATIONS} ===\n`);

  // Generate a unique branch name for this iteration.
  const branch = `sandcastle/sequential-reviewer/${Date.now()}`;

  // Create a single sandbox that both the implementer and reviewer share.
  // This gives both agents a real, named branch that persists across phases.
  const sandbox = await sandcastle.createSandbox({
    branch,
    sandbox: docker(),
  });

  try {
    // -----------------------------------------------------------------------
    // Phase 1: Implement
    //
    // A sonnet agent picks the next open issue, writes the
    // implementation (using RGR: Red → Green → Repeat → Refactor), and
    // commits the result.
    //
    // The agent signals completion via <promise>COMPLETE</promise> when done.
    // -----------------------------------------------------------------------
    // One iteration so each outer pass implements a single issue on its own
    // branch, then hands it to the reviewer. A higher value lets the agent
    // drain the whole backlog onto this one branch in a single pass, which
    // defeats the per-issue review.
    const implement = await sandbox.run({
      name: "implementer",
      maxIterations: 1,
      agent: sandcastle.claudeCode("claude-opus-4-8"),
      promptFile: "./.sandcastle/implement-prompt.md",
    });

    if (!implement.commits.length) {
      // No commits means the backlog is empty or every remaining issue is
      // blocked — there is nothing left to implement or review, so stop.
      console.log("Implementation agent made no commits. Stopping.");
      break;
    }

    console.log(`\nImplementation complete on branch: ${branch}`);
    console.log(`Commits: ${implement.commits.length}`);

    // Reviewing code that does not build or pass *IT tests is wasted effort,
    // so a branch that stays red is left for a human and the loop moves on.
    if (!(await verifyWithFixes(sandbox, branch, "implement"))) {
      failedBranches.push(branch);
      continue;
    }

    // -----------------------------------------------------------------------
    // Phase 2: Review
    //
    // A second sonnet agent reviews the diff of the branch produced by
    // Phase 1. It uses the {{BRANCH}} prompt argument to inspect the right
    // branch, and either approves or makes corrections directly on the branch.
    // -----------------------------------------------------------------------
    await sandbox.run({
      name: "reviewer",
      maxIterations: 1,
      agent: sandcastle.claudeCode("claude-opus-4-8"),
      promptFile: "./.sandcastle/review-prompt.md",
      promptArgs: {
        BRANCH: branch,
      },
    });

    console.log("\nReview complete.");

    // The reviewer may have changed code, so verify again.
    if (!(await verifyWithFixes(sandbox, branch, "review"))) {
      failedBranches.push(branch);
    }
  } finally {
    await sandbox.close();
  }
}

if (failedBranches.length) {
  console.log("\nHost verify still FAILS on these branches — fix them before opening a PR:");
  for (const failed of failedBranches) {
    console.log(`  ${failed}`);
  }
}

console.log("\nAll done.");
