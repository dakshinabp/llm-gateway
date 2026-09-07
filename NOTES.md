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