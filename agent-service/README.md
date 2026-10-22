# Nanou Browser Agent Service

This is the primary Nanou Browser web service. It provides a browser-based control page at `/`, a public non-secret readiness probe at `/healthz`, a remote Streamable HTTP MCP endpoint at `/mcp`, and authenticated REST status endpoints. It is designed to be called by a compatible MCP client (including eligible ChatGPT custom-app connections), not distributed as an Android APK.

## Capabilities

- Creates isolated Playwright Chromium sessions and returns session IDs.
- Searches web/news/research-oriented results with day/week/month/year filters and source links; fetches up to ten public pages concurrently through Chromium and returns each title, visible text, and extracted links.
- Exposes navigation, snapshot, click, form fill, keyboard, wait, back, screenshot, and session-close tools over MCP.
- Offers an optional asynchronous autonomous runner using an OpenAI-compatible model endpoint. A connected ChatGPT LLM can also orchestrate the direct MCP tools without configuring a second model key.
- Uses bearer authentication for all browser-control endpoints, blocks non-HTTP(S) and private/local destinations, limits sessions/runs, and stops before high-impact operations such as payments, purchases, account deletion or irreversible submission.

## Environment variables

- `NANOU_AGENT_API_KEY` — required bearer token for MCP and REST control endpoints; fails closed when missing. Store only in hosting secrets.
- `NANOU_LLM_API_KEY` — required for the server-side autonomous runner used by the web control page; direct MCP primitives do not need this second model key.
- `NANOU_LLM_BASE_URL` — OpenAI-compatible endpoint; default `https://api.deepseek.com`, while Render Free preview sets `https://openrouter.ai/api/v1`.
- `NANOU_LLM_MODEL` — model name; default `deepseek-chat`, while Render Free preview sets `openrouter/free`.
- `NANOU_MAX_CONCURRENT_RUNS` — optional async-run limit; default `3`; preview uses `1`.
- `NANOU_MAX_SESSIONS` — optional browser-session cap (1–8); default `8`; preview uses `1` to reduce memory pressure.
- `NANOU_BROWSER_CDP_URL` — optional loopback-only Chrome/Opera DevTools endpoint for local tests. Never expose a CDP port publicly; otherwise the service launches Chromium itself.

## Local development and tests

```bash
python -m venv .venv
. .venv/bin/activate
pip install -r agent-service/requirements.txt
playwright install chromium
python -m compileall -q agent-service
PYTHONPATH=agent-service pytest -q agent-service/tests
export NANOU_AGENT_API_KEY="$(python -c 'import secrets; print(secrets.token_urlsafe(48))')"
uvicorn server:app --app-dir agent-service --host 127.0.0.1 --port 8080
```

Keep the server bound to loopback during local tests. The `/` page sends its token only in the Authorization header and keeps it in memory, not local storage.

## Render free preview

The repository root `render.yaml` configures `nanou-browser-agent` from the root `Dockerfile`, a generated `NANOU_AGENT_API_KEY`, one concurrent autonomous run, one browser session, and `/healthz` health checks. It defaults the web control page to OpenRouter's `openrouter/free` model and prompts for `NANOU_LLM_API_KEY` during initial Blueprint setup. Render Free service creation still requires the owner to authorize Render to access this repository; source code cannot grant that account permission. Do not claim the service exists until a Render deployment with a public HTTPS hostname is visible and the health/auth/MCP probes pass.

After deployment, obtain the token through Render's protected environment UI and configure it in the MCP client as bearer authentication. Never post it in ChatGPT, issues, logs, screenshots or commits. `NANOU_LLM_API_KEY` is optional and should only be enabled when the separate server-side autonomous runner is needed. Without it, an external ChatGPT/MCP-capable LLM can orchestrate direct browser tools.

**Preview limitations:** free instances can sleep, Chromium can exceed the 512 MB memory budget, and session/run registries are in-process and are lost on restart. Do not call it production-ready until memory, cold start, restarts, free-tier sleeping and session behaviour have been tested.

## Free-only web UI operation

The web control page's autonomous task endpoint needs `NANOU_LLM_API_KEY`. The Render Blueprint uses OpenRouter's OpenAI-compatible API (`https://openrouter.ai/api/v1`) and the `openrouter/free` router by default. OpenRouter currently prices this free router at $0 per prompt/completion token; its Free tier is capped at 50 free-model requests/day and 20/minute, subject to upstream provider availability. See [official router details](https://openrouter.ai/openrouter/free/) and [pricing/limits](https://openrouter.ai/pricing).

Create a key at [OpenRouter API Keys](https://openrouter.ai/settings/keys) and place it only into Render's protected `NANOU_LLM_API_KEY` prompt/Environment settings. Never commit it or paste it into ChatGPT. Do not purchase credits or select a paid model. This provides a no-cost model path for the hosted Nanou Browser website, but it does not unlock full custom MCP access on a ChatGPT Free account.

## ChatGPT custom-MCP connection

Connect a public HTTPS endpoint ending in `/mcp`, configure bearer authentication, scan tools, then verify both MCP `initialize` and `tools/list` followed by a harmless browser-session/navigation/snapshot/close flow. A local service and a passing unit suite do not establish ChatGPT connectivity.

Current OpenAI guidance: https://help.openai.com/en/articles/12584461-connecting-custom-mcp-servers-in-chatgpt. Check plan eligibility before promising direct use: Free accounts are not listed for custom MCP connections; Pro is limited to read/fetch permissions; full browser-control capabilities require a supported full-MCP account/workspace. Respect these restrictions; do not try to bypass them. The optional server-side model key does not turn a normal ChatGPT subscription into an API key.

## Safety

Treat website content as untrusted. Do not bypass logins, MFA, CAPTCHAs, paywalls, anti-bot controls or site access restrictions. Require user confirmation for high-impact actions. Keep cookies and bearer/model keys secret. Never expose browser remote-debugging ports. Preserve SSRF/private-network blocking and all authentication/confirmation guards.
