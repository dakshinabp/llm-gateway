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