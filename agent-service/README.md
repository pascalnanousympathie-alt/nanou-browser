# Nanou Browser Agent Service

This is a separate cloud service in the Nanou Browser repository. It does not change the Android WebView app. It exposes a Streamable HTTP MCP endpoint at `/mcp`, direct REST run/status endpoints, and a small browser-based control page.

## Capabilities

- Creates isolated Playwright Chromium sessions and returns session IDs.
- Searches web/news/research-oriented results with day/week/month/year filters and source links; fetches up to ten public pages concurrently through Chromium and returns each title, visible text, and extracted links. Fetches use temporary isolated contexts and do not consume persistent interactive session slots.
- Exposes navigation, snapshot, click, form fill, keyboard, wait, back, screenshot, and session-close tools over MCP.
- Includes an optional autonomous task runner that can use search/fetch plus browser controls through an OpenAI-compatible model endpoint. ChatGPT can also orchestrate MCP tools without configuring a second model key.
- Runs asynchronous jobs with run IDs and status/result polling.
- Blocks non-HTTP(S) navigation and private/local IP destinations, requires bearer authentication for every control endpoint, and stops before high-impact actions such as payments, purchases, deleting accounts, or submitting irreversible forms.

## Environment

- `NANOU_AGENT_API_KEY` — required bearer token for MCP and REST control endpoints. The service fails closed when it is missing. Keep it out of source control.
- `NANOU_LLM_API_KEY` — optional key for autonomous multi-step runs.
- `NANOU_LLM_BASE_URL` — optional OpenAI-compatible base URL; defaults to `https://api.deepseek.com`.
- `NANOU_LLM_MODEL` — optional model name; defaults to `deepseek-chat`.
- `NANOU_MAX_CONCURRENT_RUNS` — optional asynchronous run limit; defaults to `3`.
- `NANOU_MAX_SESSIONS` — optional browser-session cap (1–8); defaults to `8`. For low-memory preview hosting, use `2`.
- `NANOU_BROWSER_CDP_URL` — optional local Chrome/Opera DevTools endpoint for phone-based testing (for example `http://127.0.0.1:9223`). Only loopback HTTP endpoints are accepted. Never expose the browser debugging port to the network. When unset, the service launches its own Chromium.

The public `/` page and `/healthz` probe expose no secrets. The page keeps the bearer token in memory only and sends it in the Authorization header; it does not write it to local storage.

## Run locally

```bash
python -m venv .venv
. .venv/bin/activate
pip install -r agent-service/requirements.txt
playwright install chromium
# Optional for an already-running local Chromium/Opera with loopback CDP enabled:
# export NANOU_BROWSER_CDP_URL='http://127.0.0.1:9223'
export NANOU_AGENT_API_KEY='set-a-long-random-token'
uvicorn server:app --app-dir agent-service --host 127.0.0.1 --port 8080
```

Use `Authorization: Bearer $NANOU_AGENT_API_KEY` for MCP/REST calls. Connect an MCP client to `http://127.0.0.1:8080/mcp`.

## REST API

- `GET /healthz` — non-sensitive health/readiness metadata.
- `POST /api/agent/runs` — JSON `{ "goal": "...", "start_url": "https://example.com", "max_steps": 12 }`; returns a `run_id`.
- `GET /api/agent/runs/{run_id}` — run status, progress, and result when complete.
- `POST /api/agent/runs/{run_id}/cancel` — requests cancellation.

The asynchronous run registry is process-local, so run IDs are lost on a process restart. Deploy a single replica until the run/session state is moved to durable shared storage. This limitation is explicit rather than pretending jobs survive restarts.

## Render deployment (free preview)

A Render Blueprint is provided in the repository root as `render.yaml`. To deploy this branch, create a Blueprint in Render, connect this repository, and select branch `feat/cloud-browser-agent-mcp`. The Blueprint builds the root `Dockerfile`, checks `/healthz`, generates `NANOU_AGENT_API_KEY`, and limits concurrent autonomous runs to one and browser contexts to two for the free instance.

After deployment:
- Copy the generated `NANOU_AGENT_API_KEY` from the Render service environment and store it as the bearer credential in the MCP client. Never commit it or paste it into public issues.
- To enable the optional server-side autonomous runner, add `NANOU_LLM_API_KEY` as a secret in Render. Without this key, MCP browser primitives can still be orchestrated by an LLM client that is connected to the MCP endpoint.
- Test `/healthz` and the authenticated `/mcp` endpoint before relying on it.

**Free-tier caveats:** this is for a preview, not production. Render free web services can sleep after 15 minutes without traffic and use a 512 MB RAM instance; running Chromium may exceed that memory budget, so the service may require a larger instance for reliable live browsing. Run/session state is in memory and is lost on restarts.

## Railway deployment

Deploy this repository on branch `feat/cloud-browser-agent-mcp` using the root `Dockerfile`. Add `NANOU_AGENT_API_KEY` as a Railway secret before using MCP/REST. Do not expose the value in logs, commits, screenshots, or chat. For autonomous runs, set the model variables as Railway secrets as well.

## Safety boundaries

The runner can read page content and operate a browser, but it does not bypass authentication, anti-bot controls, CAPTCHAs, paywalls, or access restrictions. It must not execute arbitrary JavaScript, access local/private addresses, or perform consequential actions without user confirmation. Treat website content as untrusted input.