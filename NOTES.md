# Notes

## Session 1 — Sep 6, 2026

- The Anthropic **Console** (console.anthropic.com) is a separate product from
  the Claude chat app. A Claude subscription gives you zero API credits.
  You buy API credits separately.
- My Mac had Java 11. Spring Boot 4 needs 17+, and I want 21.
- Homebrew installs the JDK somewhere macOS doesn't look. Had to symlink it into
  /Library/Java/JavaVirtualMachines before `java -version` picked it up.
- Homebrew was broken until I ran `xcode-select --install`. macOS Command Line
  Tools are a prerequisite for basically everything, including git.
- Spring Initializr auto-filled my package name as `com.dakshina.llm-gateway`.
  Hyphens are illegal in Java package names. Had to fix it by hand.

## What the scaffold actually is

Nothing below was code I wrote — this is workbench setup.

**Prerequisites (not project work)**
- macOS ships without developer tools. Homebrew and git both need
  `xcode-select --install`.
- Had Java 11; Spring Boot 4 requires 17+. Installed 21 via Homebrew.

**What Spring Initializr generated**
- `build.gradle` — dependency list. Tells Gradle to fetch Spring Web + Actuator.
- `gradlew` — the Gradle wrapper. Anyone cloning the repo runs `./gradlew` and
  gets the exact build tool version I used. Kills "works on my machine."
- `LlmGatewayApplication.java` — entry point, one `main` method.
- **Jar packaging** — Tomcat is bundled *inside* the app. One self-contained
  file. This is what makes the Dockerfile trivial later.

**`./gradlew bootRun`**
Compiles, starts embedded Tomcat, listens on port 8080.
`/actuator/health` came free with the Actuator dependency — that's the endpoint
the deploy platform will poll to know the container is alive.

**Git / GitHub**
`git init` = track changes locally. `commit` = snapshot. `push` = copy to GitHub.
GitHub no longer accepts password auth for git; `gh auth login` handles tokens.

**State at end of setup:** a running, empty web service with a health check,
pushed to GitHub. Does nothing yet.

## Building the endpoint

**Vocabulary I had to learn**
- **Endpoint** — one specific URL the service answers on. `/actuator/health` and
  `/v1/chat` are both endpoints on the same service.
- **Request body / response body** — the JSON going in and coming back.
- **Record** — Java 21 shorthand for a class that only holds data.
  `public record ChatRequest(String prompt) { }` replaces ~30 lines of
  field + constructor + getter + equals + hashCode + toString.
- **Annotation** — an `@Something` label on a class or parameter. Spring scans
  for them at startup and wires things up accordingly.
- **Dependency injection** — I never call `new AnthropicClient(...)`. I declare
  in the constructor that I need one, and Spring hands me the instance it
  created. `@Service` is what makes that instance exist.

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