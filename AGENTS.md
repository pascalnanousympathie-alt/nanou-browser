# Nanou Browser — Agent Operating Instructions

## Identity, scope and non-negotiable output

- **This repository's active target is a TinyFish-style browser-agent WEBSITE/SERVICE**: web control page + hosted Playwright browser service + remote Streamable HTTP MCP endpoint at `/mcp`, so compatible ChatGPT accounts can call its tools.
- It is not an Android-APK delivery task. The root `app/` and Gradle files are a separate legacy/experimental Android browser implementation. Do **not** build, sign, install, upload or discuss an APK as the requested deliverable unless the user explicitly asks to switch scope to Android.
- Nanoumada is a separate browser-based AI coding workspace/web app. LudoMada is a separate Android Ludo game and backend. Do not copy their code, credentials, config, deployment targets or runner into this repository.
- Before each change, read the current request and this file, check the exact repo/branch/commit/PR, and map the change to the web/MCP acceptance criteria. Correct project identity takes precedence over stale README text, inherited Gradle files or old tool output.
- Successful tests do not equal successful deployment. Claim “callable directly in ChatGPT” only after a supported account has connected to the public HTTPS `/mcp` endpoint, discovered tools and completed a harmless live call.

## Take initiative and finish the task

- Start doing the requested work in the current session. Do not stop at a plan when repository, CI, browser, or hosting tools can perform the next safe action.
- Inspect the current branch, latest commit, pull request, changed files, CI runs, job steps and logs before retrying a failed test. Distinguish runner/allocation failures from code/test failures.
- Prefer targeted diagnosis and fixes over blind repeated reruns. Re-run only after a meaningful correction or to verify a specific hypothesis.
- For authenticated dashboards and website setup, try the connected browser/noVNC path first. If the browser connector says it is disconnected, diagnose the state and attempt a safe reconnect; use Desktop Commander only when the connector route is unavailable. Never interrupt unrelated project processes.
- Do not ask the owner to repeat information already present in conversation, repository or runbook. If a step truly requires account-owner login, MFA, CAPTCHA, authorization or terms acceptance, complete the surrounding safe setup first and ask only for that exact remaining action.
- Do not promise background work. Make the maximum safe, evidence-backed progress in the current session.

## CI and diagnostics

- **Active CI scope is `agent-service/**`, root `Dockerfile`, `render.yaml`, and service workflow/config changes.** It runs Agent Service safety/API tests and syntax/config checks; Android builds/APK artifacts are not part of this scope.
- Use the repository-scoped Agent Service workflow and the independent Termux runner for service tests. Never reuse or reconfigure the LudoMada runner. Never send untrusted fork code to a personal phone runner.
- CircleCI is a fallback only when a real run proves it is active; its configuration does not activate the project automatically.
- On a failing check, inspect provider, branch/SHA, event, runner allocation, steps and logs first. An allocation failure without checkout/logs is not a demonstrated code regression. Do not blindly repeat the same failure.
- Never report PASS without the actual command output for the exact SHA. Record symptom, evidence, root cause or unknown, changes, tests, remaining blocker and next action.

## Deployment and ChatGPT MCP connection

- Target **Render free preview** from `render.yaml` unless the owner explicitly approves another host. Do not deploy this Python/Playwright service to Nanoumada's Vercel project or LudoMada's Railway backend. Do not spend paid credits or start a paid trial without explicit authorization.
- Keep `NANOU_AGENT_API_KEY` and any `NANOU_LLM_API_KEY` in the host's secret store. Never print or commit them. Use a strong randomly generated bearer token and establish a safe way to enter it into ChatGPT's connection form.
- ChatGPT needs a public HTTPS MCP endpoint. A local `127.0.0.1` smoke test cannot prove ChatGPT connectivity. Check current plan/account eligibility: OpenAI documentation does not list Free as eligible for custom MCP connections, Pro is read/fetch-only, and full browser-control capabilities need a supported full-MCP plan/workspace. Do not promise an unsupported direct connection or attempt to bypass product-plan restrictions.
- Before calling deployment “live”, verify the URL and commit, `/healthz`, REST/MCP missing and wrong token rejection, valid-token `initialize`, `tools/list`, and one harmless public browser session/navigation/snapshot/close. Check restarts, memory, free-tier sleep and in-memory session/run loss.
- Never expose Chromium remote-debugging ports to the public internet. Preserve SSRF protections, bearer auth, high-impact-action blocking and confirmation boundaries. Do not bypass sign-in, MFA, CAPTCHA, paywalls, anti-bot protections or access restrictions.

## Acceptance checklist for this project

- [ ] Scope remains web service + MCP; no APK work unless explicitly requested.
- [ ] Service tests pass on the exact commit.
- [ ] Container/build config and hosting health route are verified.
- [ ] Deployment permissions/host setup completed without paid-plan surprises.
- [ ] Public HTTPS `/mcp` connection successfully completes `initialize` and `tools/list`.
- [ ] A harmless live tool call succeeds through the intended ChatGPT account/plan.
- [ ] Exact SHA, public endpoint, test evidence, connection result and any limitation are recorded in the PR.
