# Nanou Browser repository instructions

Read root `AGENTS.md` first. This repository's active deliverable is the TinyFish-style **web browser agent service and remote MCP endpoint**, not an Android APK. Treat `app/` and Gradle as a legacy/experimental Android alternative only; do not run `assembleDebug`, sign/package/upload an APK, or spend time on an Android emulator unless the user explicitly changes scope.

Keep the separate products straight: Nanou Browser = browser automation website + hosted MCP; Nanoumada = web-based AI coding workspace; LudoMada = Android Ludo game plus backend. Never reuse another project's repo, deployment project, credentials, CI runner or feature flags.

Work proactively: inspect current branch/SHA/PR and relevant failure evidence first, fix root causes, validate on the exact commit, and report only evidence-backed results. Use the connected browser/noVNC path for authenticated dashboard setup when possible; diagnose/reconnect it if disconnected. Use Desktop Commander only as a last resort and never interrupt unrelated work. Ask for an owner-only login/MFA/CAPTCHA/permission step only after completing the surrounding setup.

CI and QA are web-service scoped: Agent Service tests, safety/auth/SSRF checks, container/build config, Render readiness, remote MCP `initialize` + `tools/list`, and a harmless browser-control smoke test. Never claim the service is available directly inside ChatGPT until a supported ChatGPT plan/account has connected the public HTTPS `/mcp` endpoint and completed a tool call. Preserve all bearer secrets, do not expose CDP, and keep high-impact actions behind confirmation.
