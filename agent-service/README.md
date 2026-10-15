# Nanou Browser Agent Service

This is a separate cloud service in the Nanou Browser repository. It does not change the Android WebView app. It exposes a Streamable HTTP MCP endpoint at `/mcp`, direct REST run/status endpoints, and a small browser-based control page.

## Capabilities

- Creates isolated Playwright Chromium sessions and returns session IDs.
- Searches the public web and returns linked results/snippets; fetches public pages through Chromium and returns the title, visible text, and extracted links.
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

The public `/` page and `/healthz` probe expose no secrets. The page keeps the bearer token in memory only and sends it in the Authorization header; it does not write it to local storage.

## Run locally

```bash
python -m venv .venv
. .venv/bin/activate
pip install -r agent-service/requirements.txt
playwright install chromium
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

## Railway deployment

Deploy this repository on branch `feat/cloud-browser-agent-mcp` using the root `Dockerfile`. Add `NANOU_AGENT_API_KEY` as a Railway secret before using MCP/REST. Do not expose the value in logs, commits, screenshots, or chat. For autonomous runs, set the model variables as Railway secrets as well.

## Safety boundaries

The runner can read page content and operate a browser, but it does not bypass authentication, anti-bot controls, CAPTCHAs, paywalls, or access restrictions. It must not execute arbitrary JavaScript, access local/private addresses, or perform consequential actions without user confirmation. Treat website content as untrusted input.