# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Build/test use the Maven wrapper (`./mvnw`) — no separate lint step is configured.

```bash
# Run the full test suite (unit + the H2/Flyway-backed integration test)
./mvnw test

# Run a single test class
./mvnw test -Dtest=CustomerServiceTest

# Run a single test method
./mvnw test -Dtest=CustomerServiceTest#register_duplicateEmail_throwsAndNeverSaves

# Build the jar (skip tests for a quick packaging check)
./mvnw clean package -DskipTests

# Run the app locally (needs the DB below and OPENAI_API_KEY set; falls back to
# empty suggestions if OPENAI_API_KEY is unset)
export OPENAI_API_KEY=sk-...
docker-compose up -d db
./mvnw spring-boot:run

# Full stack in Docker (app + Postgres)
docker-compose up --build
```

Flyway runs automatically against whichever datasource is active — `V1__initial_schema.sql` on startup for Postgres, and the same migration against an in-memory H2 (PostgreSQL-compatibility mode) for tests via `src/test/resources/application.properties`. There's no separate test database to provision.

## Architecture

Spring Boot 4 / Spring Framework 7, Java 21, package root `com.codecraft.eventsuggestion`. Standard layered structure: `controller` → `service` → `repository`, plus `domain` (JPA entities), `dto` (records, converted via static `from(...)` factory methods), `security`, `config`, `exception`.

**Domain model**: `Customer` 1–1 `CustomerPreferences` (sports/hobbies/interests as `@ElementCollection`s, plus a free-text `learnedProfile`), `Customer` 1–N `Suggestion` (category `DAILY`/`WEEKEND`/`MONTHLY`, status `PENDING`/`ACCEPTED`/`REJECTED`/`WISHLIST`).

**Auth**: Stateless JWT. `JwtAuthFilter` (a `OncePerRequestFilter`) reads `Authorization: Bearer <token>`, validates via `JwtUtil`, and loads the principal via `UserDetailsServiceImpl` — on any failure it just leaves the security context unauthenticated and lets the filter chain continue (it never blocks the request itself; `SecurityConfig`'s `authorizeHttpRequests` is what actually rejects unauthenticated calls). `/api/auth/**`, `/actuator/health`, and the Swagger paths are the only public routes.

**Suggestion generation loop** (`SuggestionService`): three `@Scheduled` cron jobs (daily 08:00, weekly Friday 09:00, monthly on the 1st) iterate all customers and call `generateForCustomer`, which delegates prompt-building and the actual OpenAI call to `OpenAIService`, then persists the results as `PENDING` suggestions. Each customer is processed independently — one customer's exception is caught and logged without stopping the batch. Every 5th non-pending feedback (`count % 5 == 0`, checked in `provideFeedback`) triggers `refreshLearnedProfile`, which asks OpenAI to summarize behavior patterns into `CustomerPreferences.learnedProfile`, which is then folded into future prompts.

**OpenAIService**: builds its own `RestClient` in the constructor from injected `baseUrl`/`apiKey`/`model` properties (not injected as a bean), so it's not mockable via a test double — tests point `baseUrl` at a local server instead. It fails soft everywhere: a blank API key or any HTTP/parsing exception returns an empty list / `null` rather than throwing, so a missing key degrades the app to "no suggestions" instead of breaking it. Uses Jackson 3's `tools.jackson.databind` package (not `com.fasterxml.jackson` — Spring Boot 4's default), which matters for any new JSON handling code.

**Testing conventions**: Service/security-layer logic gets plain JUnit 5 + Mockito unit tests (no Spring context). The one full-stack test, `SecurityIntegrationTest`, boots the real server (`@SpringBootTest(webEnvironment = RANDOM_PORT)`) and drives it with `RestTestClient` (`org.springframework.test.web.servlet.client`) — Spring Framework 7 replaced `TestRestTemplate` with this fluent, `WebTestClient`-style API. `OpenAIService` is tested against a local JDK `com.sun.net.httpserver.HttpServer` rather than a mocking library, since its `RestClient` isn't injectable.

## Project Conventions

- Always use the latest versions of dependencies.
- Always write Java code as the Spring Boot application; use Maven for dependency management.
- Create both positive and negative test cases for generated code.
- Keep the CircleCI pipeline (`.circleci/config.yml`) passing — it runs `mvnw test` then `mvnw package` on every push.
- Minimize the amount of code generated.
- The Maven artifact name must match the parent directory name; group ID is `com.codecraft`.
- Bump the POM's PATCH version on each generated change, and add a matching entry to the README changelog (see existing entries for the expected format). The `Dockerfile`'s jar filename must be updated to match.
- Do not use Lombok — entities/DTOs use plain getters/setters or records.
- Keep `docker-compose.yml` in sync with whatever infrastructure the app depends on.

## Workflow

- Enter plan mode for any non-trivial task (3+ steps or architectural decisions); write the spec up front.
- After a user correction, record the pattern so it isn't repeated.
- Never mark a task done without proving it works (run the tests, check logs, diff behavior).
- For non-trivial changes, pause and consider whether a more elegant approach exists before presenting the work.
- Use `.claude/skills/` and `.claude/agents/` liberally — one skill per capability, one subagent per focused sub-task — to keep the main context window clean.
