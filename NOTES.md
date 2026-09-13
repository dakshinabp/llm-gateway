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

## Session 3 — Sep 12, 2026: timeouts, error handling, retries

**Timeouts**
Connect 5s, read 30s. Asymmetric on purpose: establishing a TCP connection
should be near-instant, so 5s means the network is broken and waiting longer
won't help. Generating 1024 tokens legitimately takes time, so a tight read
timeout would kill healthy requests. Before this there were no timeouts at all —
a hung provider request could pin a Tomcat thread forever, and enough of those
means the gateway itself becomes the outage.

**Validate at the boundary**
Empty prompt → 400 before any network call. The cheapest request is the one you
don't make, and a caller who sent bad input deserves a 400, not a 500 implying
my service broke.

**One error contract**
`GatewayExceptionHandler` (`@RestControllerAdvice`) returns `{status, message}`
for every failure. Callers can't tell which exception type produced it, and
shouldn't be able to.

**Retryability is a property of the failure, not the status code**
The design decision I'd defend in an interview. Bad credentials and "provider is
down" both map to 502 on the way out. If I decided retryability by reading 502,
I'd retry a broken API key three times for nothing. So `DownstreamException`
carries a `retryable` flag, set where the cause is actually known.

| Provider says | I return | Retry? |
|---|---|---|
| 400 | 400 | no — malformed now, malformed next time |
| 401/403 | 502 | no — retrying can't fix a credential |
| 429 | 429 | yes — rate limits are temporary by definition |
| 5xx | 502 | yes — their server had a bad moment |
| timeout | 504 | yes — could be a blip |
| empty response | 502 | no — contract violation, not a blip |

401/403 becomes 502 deliberately: the caller did nothing wrong and can't fix it.
Returning 401 would make them think *their* gateway key was bad.

Provider error text is never passed through. Only my wording goes out — upstream
error messages can carry internal details, and leaking them is how information
disclosure happens.

**Retries: 3 attempts, exponential backoff, jitter**
500ms then 1000ms. Doubling because hammering a struggling provider every 100ms
makes it worse. Jitter because if a thousand clients fail at the same instant and
all retry at exactly 500ms, that's a synchronized stampede that knocks the
service over again.

`Thread.currentThread().interrupt()` restored before throwing — catching
`InterruptedException` swallows the signal and breaks graceful shutdown.

**Configurable downstream URL**
`anthropic.base-url` with a default. A hardcoded base URL is untestable, and this
is also the groundwork for a fallback provider.

**Verified, not assumed**
- Bogus base URL → 3 WARN lines; log timestamps 622ms and 1206ms apart against
  requested delays of 611ms and 1200ms. Then 504.
- Bogus API key → exactly 1 attempt, 502. The flag works in both directions.

**Biggest remaining weakness**
No overall request deadline. 3 attempts × 30s read timeout + backoff is a worst
case near 90 seconds for a single caller. Per-attempt timeouts aren't enough —
the real answer is a budget for the whole request, checked before each retry.
Also no circuit breaker, so retries still pound a provider that's fully down.

## Session 4 — Sep 13, 2026: rate limiting and first tests

**Per-key rate limiting**
Counts requests per gateway key per clock minute. Over the limit returns 429 with
`Retry-After: 60`, so callers know when to come back instead of retrying
immediately. `ConcurrentHashMap.compute` makes the increment safe when many
requests land at once.

Counted per key rather than globally. There's one key today because there's one
user, but issuing more keys later needs no code change.

The key check runs before the limit check. Counting unknown keys would let anyone
fill the map with made-up keys.

**Rejected requests still count toward the limit.** A 400 is still a request the
gateway had to handle. Deliberate, not an oversight.

**Known weaknesses**
- Counter is in memory. Two instances = two counters = double the real limit.
  Shared storage (Redis) is the fix.
- Fixed window, not sliding. 10 requests at 10:00:59 and 10 more at 10:01:00 is
  20 in one second and technically allowed.

**First tests — 7 of them**

`RateLimiterTest`: limit enforcement, per-key isolation, and window reset.
The reset test is only possible because `RateLimiter` takes a `Clock` instead of
calling the system clock directly — the test hands it a clock it controls and
jumps forward a minute. Without that, the test would have to sleep 60 seconds,
which means in practice nobody writes it and the reset never gets tested.

`ChatEndpointTest`: `MockMvc` sends fake HTTP requests through the real filter
chain — 401 with no key, 400 on an empty prompt, 200 returning the model answer.
`AnthropicClient` is replaced with a mock, so the tests never touch the network,
never cost money, and give the same result every run.

**Tests must not need real secrets**
`src/test/resources/application.properties` holds fake values. The base URL points
at a dead port, so if a test ever did attempt a real call it fails immediately
rather than quietly reaching the internet. Verified by running the suite with the
real environment variables deliberately removed.