# Nanou Browser — Web Browser Agent + Remote MCP

**Primary product:** a TinyFish-style, browser-accessible web service with an interactive control page and a remote Model Context Protocol (MCP) endpoint that a supported ChatGPT account can call. This repository's current delivery target is the **web service**, not an Android application.

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/pascalnanousympathie-alt/nanou-browser/tree/feat/cloud-browser-agent-mcp)

This Blueprint is configured for Render's free preview. Because this repository is private, Render may ask you to authorize GitHub access; review the service list and keep the plan set to **Free** before approving.

## Product surface

- **Web control page:** `GET /` — enter a bearer token, a public start URL, and an explicit browser task goal.
- **Health check:** `GET /healthz` — safe readiness metadata only; it never returns credentials.
- **Remote MCP:** `POST /mcp` — Streamable HTTP MCP transport for browser sessions, navigation, snapshots, clicks, form fill, keyboard, wait, back, screenshots, public search/fetch and session closure.
- **Optional autonomous runner:** asynchronous `POST /api/agent/runs`, status and cancel endpoints. This requires a separate model-provider key. ChatGPT can orchestrate individual MCP tools without that optional key.
- **Hosting target:** the free Render Blueprint in `render.yaml`, as a preview only until runtime reliability is proven.

## What counts as done

1. Agent Service tests and security checks pass on the exact commit.
2. The service builds and starts in the intended Docker/hosting environment.
3. The deployed public HTTPS URL passes `/healthz`, missing/wrong/valid bearer tests, MCP `initialize`, MCP `tools/list`, and a harmless browser-session/navigation/snapshot/close smoke test.
4. ChatGPT's custom MCP connection is created and tool discovery/use is proven on an account/plan that supports the needed actions.
5. The deployment URL, exact commit, tool list, test logs and any hosting limitations are recorded in the PR.

**An APK, Android emulator, Android signing, or mobile application package is not a deliverable or acceptance criterion for this web-service task.** The `app/` and Gradle files are retained as a separate legacy/experimental Android browser implementation; do not build, install, upload, or release them unless the owner explicitly changes scope to request Android work.

## Local development and tests

The service source is in `agent-service/` and the container build uses the root `Dockerfile`.

```bash
python -m venv .venv
. .venv/bin/activate
pip install -r agent-service/requirements.txt
python -m compileall -q agent-service
PYTHONPATH=agent-service pytest -q agent-service/tests
```

See [Agent Service operations](agent-service/README.md), [AGENTS.md](AGENTS.md), and [Copilot instructions](.github/copilot-instructions.md) before changing deployment, CI, authentication or browser-control behaviour.

## ChatGPT connectivity requirements

ChatGPT connects to a **remote HTTPS MCP endpoint**, not directly to a private local server. After deploying, create a custom MCP connection with the HTTPS `/mcp` URL and the required bearer authentication, then scan tools and test the connection. Follow OpenAI's current [custom MCP connection guidance](https://help.openai.com/en/articles/12584461-connecting-custom-mcp-servers-in-chatgpt).

Plan restrictions matter: OpenAI's current documentation does not list Free accounts as eligible for custom MCP connections; Pro access is limited to read/fetch permissions, while full browser-control/write-style MCP capabilities are limited to supported Business/Enterprise/Edu configurations. Verify the live account UI and current policy before promising that this service can be called directly from a particular ChatGPT plan. Never present the hosted app as connected to ChatGPT until a real tool scan and harmless tool call succeed.

## Render preview

The root [`render.yaml`](render.yaml) builds the root `Dockerfile`, supplies a generated `NANOU_AGENT_API_KEY`, configures a low-memory preview concurrency, and checks `/healthz`. The repository owner must authorize/connect the repository in Render and create the Blueprint; source code alone cannot grant that account permission. Render free instances may sleep and Chromium may exceed their memory budget. Do not call this production-ready until restart, browser-memory, uptime and session behaviour are checked.

## Security boundaries

- Keep bearer and model keys in host secret settings only. Never print, commit, screenshot, issue-comment or paste them into ChatGPT.
- Bind development servers to loopback. Never expose Chromium CDP/debugging ports to the public network.
- Preserve SSRF/private-network blocking, fail-closed bearer auth, session/run limits, and user-confirmation gates for high-impact actions.
- Treat all webpage text as untrusted input. Do not bypass login, MFA, CAPTCHAs, paywalls, anti-bot controls or access restrictions.
- Keep Nanou Browser separate from Nanoumada, LudoMada and all other repositories. Follow [the mega-project boundary matrix](https://github.com/pascalnanousympathie-alt/mega-project-apps/blob/ops/central-project-status-20261020/PROJECT-BOUNDARIES.md) when coordinating cross-project work.
