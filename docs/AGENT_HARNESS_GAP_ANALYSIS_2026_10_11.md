# Agent Harness Gap Analysis (2026-10-11)

Audit of the OpenCode agent/tooling harness in this repository: the `.opencode/` agent
definitions, the project-level config, `AGENTS.md`, skills, and the relationship between
the documented verify workflow and CI.

Scope is the **harness**, not product features (see `GAP_ANALYSIS_2026_10_09.md` for the
product/module audit).

Method: inspected `.opencode/`, `opencode.jsonc.json`, `AGENTS.md`, root `.gitignore`,
`.github/workflows/pilot-validation.yml`, global config (`~/.config/opencode/`), and the
authoritative OpenCode config/agents schema (`https://opencode.ai/config.json`).

Legend: **Critical** = harness cannot function as intended · **High** = safety/behaviour
silently wrong · **Medium** = drift or undiscoverable capability · **Low** = hygiene.

## Executive summary

The harness is **defined but not wired**. Four subagent prompts exist
(`architect`, `developer`, `tester`, `reviewer`), but:

- they are **not version-controlled** (root `.gitignore` ignores all of `.opencode/`),
- the project config file (`opencode.jsonc.json`) is **never loaded** by OpenCode, so the
  provider/model definitions are inert,
- the agents' `permissions:` frontmatter is **not a recognised agent field**, so the
  intended edit/bash safety rails are likely no-ops,
- two agents are pinned to a **model that is defined nowhere**,
- there is **no orchestrating agent, no `default_agent`, and no workflow documented in
  `AGENTS.md`**, so nothing sequences or enforces the pipeline.

Net effect: the agents are effectively invisible and unenforced — consistent with the
original observation that the agent workflow is not being used.

## Findings

| # | Severity | Finding | Evidence |
|---|---|---|---|
| G1 | **Critical** | **Agent definitions are not version-controlled.** Root `.gitignore` ignores the entire `.opencode/` directory, so all four agent prompts are local-only — absent from CI, other clones and teammates. | `.gitignore:16` (`.opencode/`) matches `.opencode/agents/*.md`; `git ls-files .opencode` returns nothing |
| G2 | **Critical** | **Project config never loads.** OpenCode loads only `opencode.json` / `opencode.jsonc`. The repo ships `opencode.jsonc.json`, so the `deepseek` provider and `deepseek-flash` / `deepseek-v4-pro` model definitions never apply. | OpenCode config docs (Locations); `~/.config/opencode/opencode.jsonc` contains `$schema` only |
| G3 | **High** | **Agent permission blocks are malformed → restrictions not enforced.** Frontmatter uses `permissions:` (plural) as a list of `{action, resource, effect}`. `AgentConfig` supports only `permission:` (singular), as a rule map or action. Intended denies (architect/reviewer edit; developer/tester `git push`, `git reset --hard`) are therefore not applied. | `.opencode/agents/architect.md:6-9`, `developer.md:6-13`, `tester.md:6-13`, `reviewer.md:6-9`; schema `AgentConfig.permission → PermissionConfig`, no `permissions` key |
| G4 | **High** | **Two agents run a non-existent model.** `architect.md` and `reviewer.md` pin `model: openai/gpt-5.6-sol`, which is defined in no loaded config (and is not a real OpenAI model ID). Expect load failure or silent fallback. | `.opencode/agents/architect.md:4`, `reviewer.md:4`; global and project configs |
| G5 | **High** | **No orchestration and no documented workflow.** There is no primary/orchestrator agent sequencing architect → developer → tester → reviewer, no `default_agent`, and `AGENTS.md` never mentions agents, skills, or when to use them. Nothing makes the pipeline happen. | `AGENTS.md` (no agent/skill section); config has no `agent` / `default_agent` |
| G6 | **Medium** | **Per-app instruction files are not wired.** Only root `AGENTS.md` is auto-loaded. `apps/*/README.md` are never listed in `instructions`, so they are read ad hoc (or not at all). | Config has no `instructions`; `apps/backend/README.md`, `apps/mobile/README.md`, `apps/rfid-gateway/README.md` |
| G7 | **Medium** | **Skills are global-only, not repo-versioned or referenced.** No `.opencode/skills/` exists; the ~23 skills live in `~/.agents/skills` and `~/.claude/skills`. No skills policy is documented in `AGENTS.md`. | `find` for `SKILL.md` in repo → none; listings of `~/.agents/skills`, `~/.claude/skills` |
| G8 | **Medium** | **"Verify (matches CI)" is inaccurate.** CI additionally runs `mvn -B test` for `apps/rfid-reader-demo` and `apps/rfid-reader-impinj` (omitted from AGENTS.md); CI uses Node 22 while `.node-version` pins 20.19; AGENTS.md mixes `pnpm --filter race-tracker-mobile start` with CI's `pnpm --dir apps/mobile …`. | `AGENTS.md:27-36` vs `.github/workflows/pilot-validation.yml:24-31,63-66`; `.node-version` |
| G9 | Low | **`.opencode/package.json` is ignored by `.opencode/.gitignore`**, so the `@opencode-ai/plugin` dependency is not reproducible. No plugin currently uses it. | `.opencode/.gitignore` |
| G10 | Low | **Inverted priorities in git.** `opencode.jsonc.json` (agent-related, inert) is tracked, while the actual agent assets are ignored. | `git ls-files` vs G1 |

## Impact

- **Safety rails are not real.** The architect/reviewer "read-only" posture and the
  developer/tester push/reset guards (G3) give a false sense of protection.
- **Non-reproducible.** A fresh clone or CI runner has none of the agent prompts (G1) and
  no working provider config (G2), so agent-assisted work cannot be reproduced or reviewed.
- **Undiscoverable.** With no orchestration or documentation (G5), and per-app docs unwired
  (G6), correct tool use depends entirely on operator memory.

## Remediation roadmap

Ordered by dependency; each item cites the finding it closes.

- **R1 — Fix config naming (G2).** Rename `opencode.jsonc.json` → `opencode.jsonc`. Confirm
  the `deepseek` provider resolves (`opencode models`). Add the optional keys below once the
  file loads.
- **R2 — Fix agent frontmatter (G3).** Replace the `permissions:` list with the supported
  `permission:` map. Example for `architect` / `reviewer`:
  ```yaml
  permission:
    edit: deny
    webfetch: allow
  ```
  Example for `developer` / `tester`:
  ```yaml
  permission:
    bash:
      "git push *": deny
      "git reset --hard *": deny
  ```
  Optionally add `permission.task` to constrain which subagents each can spawn.
- **R3 — Use real models (G4).** Repoint `architect` / `reviewer` to a defined model
  (candidate: `deepseek/deepseek-v4-pro`, subject to R1). Verify each agent loads.
- **R4 — Version-control the agent prompts (G1, G10).** Narrow the root ignore so
  `.opencode/agents/**` is tracked while `.opencode/node_modules/` stays ignored, e.g.
  ```
  .opencode/*
  !.opencode/agents/
  .opencode/node_modules/
  ```
  Add a `.opencode/README` note if helpful. Keep `opencode.jsonc.json` tracked only if
  renamed per R1.
- **R5 — Document and orchestrate the workflow (G5, G6, G7).** Add an "Agent workflow"
  section to `AGENTS.md` covering: when to use architect vs developer vs tester vs
  reviewer, the skills policy, and the rule that per-app `README.md` files are read before
  touching that app. Optionally add a primary orchestrator agent and set `default_agent`,
  plus wire per-app docs via the `instructions` config key.
- **R6 — Reconcile verify with CI (G8).** Update `AGENTS.md`'s Verify section to include the
  reader-adapter `mvn -B test` runs, reconcile the Node version (22 vs `.node-version`
  20.19), and use one consistent mobile command form.

## Open decisions

1. **Track the agent prompts in git (R4)?** The root ignore is deliberate ("Local agent
   tooling"). Recommitting them makes the harness reviewable and reproducible; keeping them
   local keeps project noise down.
2. **Model for architect / reviewer (R3):** `deepseek/deepseek-v4-pro`, or another defined
   model?
3. **Orchestration (R5):** add a dedicated primary orchestrator agent, or document the
   pipeline in `AGENTS.md` and rely on the default agent plus `@`-mentions?

## Remediation status (2026-10-11)

Decisions taken: track the agent prompts (R4), use `deepseek/deepseek-v4-pro` for
architect/reviewer (R3), and **document** the pipeline rather than add a synthetic
orchestrator (R5).

| Item | Status | Notes |
|---|---|---|
| R1 — config naming | **Done** | Renamed to `opencode.jsonc`; corrected to the supported `provider` / `npm` / `options` shape. |
| R2 — agent permissions | **Done** | All four agents use the supported `permission:` map. |
| R3 — models | **Done** | architect/reviewer repointed to `deepseek/deepseek-v4-pro`. |
| R4 — track prompts | **Done** | Root ignore narrowed; agent prompts staged for tracking. |
| R5 — workflow | **Done** | "Agent workflow" section in `AGENTS.md`; per-app docs via `instructions`. Orchestrator intentionally not added. |
| R6 — verify | **Done** | Reader-adapter tests, Node 22 vs 20.19, unified mobile command form. |
