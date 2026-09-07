# Notes

**The four data types and why there are four**
- `ChatRequest` / `ChatResponse` — *my* gateway's API shape.
- `AnthropicRequest` / `AnthropicResponse` — the *downstream* API's shape.

Keeping them separate is the point. If Anthropic changes their format, or I add
an OpenAI backend, my own API stays the same for callers. Translation happens
inside the gateway.

**Annotations used**
- `@RestController` — this class handles HTTP; return values become JSON.
- `@RequestMapping("/v1")` — URL prefix for every method in the class.
- `@PostMapping("/chat")` — handles POST to `/v1` + `/chat`.
- `@RequestBody` — convert the incoming JSON into this Java object.
- `@Service` — Spring creates one instance and manages it.
- `@Value("${anthropic.api-key}")` — inject a config value.
- `@JsonProperty("max_tokens")` — Java uses `maxTokens`, Anthropic's JSON uses
  `max_tokens`. This is the translation. Without it: 400 from the API.

**Config from the environment, not the code**
`application.properties` has `anthropic.api-key=${ANTHROPIC_API_KEY}`.
The key is never in a file, never in git. Locally I `export` it in the terminal;
in the cloud the platform sets the same variable. Same code, both places.
Interview phrasing: *config lives in the environment, not in the artifact.*

**Only declaring the fields I use**
Anthropic's response has ~10 fields. `AnthropicResponse` declares one: `content`.
Spring Boot configures Jackson to ignore unknown JSON fields by default, so
anything undeclared is dropped. Means their API can add fields without breaking
me. I'll add `usage` later — that's where the token counts for cost tracking are.

**RestClient is built once, in the constructor**
Base URL and headers configured a single time and reused for every request.
Building a new HTTP client per call is a common performance mistake.

## Known weaknesses — deliberate, not oversights

- `response.content().get(0)` throws if Anthropic returns an error or an empty
  list. Caller gets a 500. → retries / error-handling session.
- No timeout on the downstream call. A hung Anthropic request hangs my thread.
- `/v1/chat` has no auth. Once this is on a public URL, anyone who finds it
  spends my API credit. Must fix at or before deploy.

## Where the day actually went

About half the session was environment setup and vocabulary, not project work.
Java 11 → 21, macOS dev tools, GitHub auth. The REST API primer mid-session was
what unblocked the rest.

## End of session 1

Working locally: `POST /v1/chat` → Anthropic → response. Committed and pushed.
Not done: Dockerfile, deploy. Those are session 2.

## Session 2 — Sep 7, 2026: auth, Docker, deploy

**Gateway auth**
Two separate keys now, and conflating them would be a security bug:
- `ANTHROPIC_API_KEY` — what the gateway uses downstream. Costs money.
- `GATEWAY_API_KEY` — what callers present to the gateway. Generated with
  `openssl rand -hex 32`.

Enforced in `ApiKeyFilter`, a `OncePerRequestFilter`. A filter runs before the
controller on every request; if it doesn't call `filterChain.doFilter(...)`, the
request stops there. One place to change, protects every endpoint I add later.
Only `/v1/` paths are checked — `/actuator/health` stays open because the deploy
platform polls it without credentials. 401 (not 403): "I don't know who you are."

Rate limiting will go in this same filter — it has to happen before any work.

**Local secrets**
Env vars don't cross terminal tabs. Lost 20 minutes to exporting keys in the
wrong tab. Fixed with a gitignored `env.sh` that I `source` at the start of a
session. Convention now: Tab 1 = server, Tab 2 = commands.

**Dockerfile — multi-stage**
Stage 1 (`21-jdk`) compiles the jar. Stage 2 (`21-jre`) copies just the jar
across. Build tools never reach the final image: ~200MB instead of ~700MB, and no
compiler in production. No secrets in the image — injected at runtime.

**Deploy — Render free tier**
- Needed `server.port=${PORT:8080}`. Platforms assign the port via a `PORT` env
  var; hardcoding 8080 gives you "no open ports detected" and a failed deploy.
- Env vars set in Render's dashboard, same names as `env.sh`. Same code, both
  environments — the artifact doesn't change, the config does.
- Health check path set to `/actuator/health`.
- Free tier spins down after 15 min idle; first request after that takes ~1 min.

**Verified in production**
- `GET /actuator/health` → 200 `{"status":"UP"}`
- `POST /v1/chat` with key → real model response
- `POST /v1/chat` without key → 401

**Still outstanding**
- No retries, no timeout on the downstream call.
- `content().get(0)` still throws on an error or empty response → 500.
- No rate limiting, so a leaked gateway key can still drain the credit.
- No README yet.