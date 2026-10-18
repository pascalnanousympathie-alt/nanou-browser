# Nanou Browser — Agent Operating Instructions

These instructions apply only to this repository. Do not mix Nanou Browser with Nanoumada, LudoMada, or other projects.

## Take initiative and finish the task

- Start doing the requested work in the current session. Do not stop at a plan when repository, CI, browser, or hosting tools can perform the next action.
- Inspect the current branch, latest commit, pull request, changed files, CI runs, job steps and logs before retrying a failed test. Distinguish a runner/allocation failure from a code/test failure.
- Prefer targeted diagnosis and fixes over blind repeated reruns. Re-run only after making a meaningful correction or when verifying a specific hypothesis.
- Use available connected MCP/app integrations first. For visual/browser and authenticated-dashboard steps, try the Opera Browser Connector connected through the noVNC browser session before asking the user to navigate manually. Check existing tabs and connection state, use the active tab when possible, and continue through every safe, automatable step.
- If Opera/noVNC reports that it is disconnected, try the available reconnect/open-tab path and diagnose the connector state. Use Desktop Commander only as a last resort when the browser/MCP route is unavailable, and never interrupt unrelated project processes.
- Do not ask the user to repeat information already in the conversation or repository. If an action truly requires an account owner's consent, password/2FA, CAPTCHA, payment, accepting terms, or a permission that the agent cannot grant, complete all prior setup, stop at that exact checkpoint, and request only the single necessary action. Continue the remaining workflow as soon as access is available.
- Do not promise background work or say you will finish later. Make the maximum safe, evidence-backed progress now.

## CI and troubleshooting

- Check the job metadata, runner ID/name, logs URL, steps, and event/ref before diagnosing CI. When jobs fail before checkout with no runner and no steps, do not call that a code regression; inspect repository/org Actions settings, runner availability and billing/usage restrictions.
- If GitHub-hosted runners remain unavailable, activate the checked-in CircleCI backup in `.circleci/config.yml` and verify the CircleCI pipeline. Do not claim it is active until the repository is connected and a real pipeline has run.
- Keep the Agent Service CI and Android CI scoped to relevant files. The Android job should not run for server-only changes.
- Never state that tests passed unless their actual output is available. If Android/Termux cannot install a dependency, use a supported Linux CI environment rather than altering production dependencies solely to accommodate Android.
- Preserve a diagnostic summary: symptom, evidence, root cause or unknown, changes made, checks performed, remaining blocker and exact next action.

## Browser service, hosting and security

- Keep the bearer API key and model-provider keys in hosting secrets only; never commit, print, screenshot, or paste secrets into issues or chat.
- Never expose Chromium remote-debugging ports to the public internet. Preserve SSRF protections, bearer authentication, high-impact-action blocking, resource limits and user confirmation boundaries.
- Before reporting a service as live, verify the deployed URL, `/healthz`, authentication with missing/wrong tokens, a valid MCP handshake/tool listing at `/mcp`, and at least one harmless public-browser session.
- Treat a free hosting instance as a preview unless load, restart, memory, session persistence and availability have been verified. Document any in-memory session/job limitations honestly.
- Do not deploy over the wrong project, change unrelated branches, merge an unverified pull request, or claim TinyFish parity without explicit capability and reliability tests.
