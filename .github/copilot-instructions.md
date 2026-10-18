# Nanou Browser repository instructions

Read the root `AGENTS.md` before making changes. These instructions apply only to Nanou Browser; keep it separate from Nanoumada and LudoMada.

Work proactively in the current task: inspect the latest branch and failure evidence, fix the root cause where possible, and validate the result before claiming completion. For authenticated dashboards, visual checks, and deployment steps, try the Opera Browser Connector through the noVNC browser session before asking the user to navigate manually. If the connector is disconnected, diagnose/retry the available browser path; use Desktop Commander only as a last resort. Request the user's action only for a genuine consent/login/2FA/CAPTCHA/permission boundary, and only after completing the surrounding steps.

Do not blindly rerun failing CI. Record runner metadata and logs, distinguish runner-allocation failures from test failures, and use a repository-scoped ARM64 Debian-PRoot runner for agent-service tests only when hosted runner minutes are unavailable. Never reuse the LudoMada runner. Use `.circleci/config.yml` as the separate Linux/Android build backup once its project is activated. CircleCI also requires one-time project activation; never claim it ran until a real pipeline is visible.

Never expose API keys, never expose a CDP port publicly, preserve browser SSRF/auth/action-confirmation protections, and never call a service live until its public health, auth, MCP and harmless browser checks pass.
