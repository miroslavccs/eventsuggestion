# EventSuggestion

A personalized life enrichment platform that suggests activities, events, and experiences tailored to each registered customer. Suggestions are powered by OpenAI and continuously refined by learning from the customer's feedback.

---

## What it does

After registration, customers receive AI-generated suggestions organized in three categories:

| Category | Frequency | Examples |
|---|---|---|
| **Daily** | Every morning | Nearby concerts, theatre plays, sport events |
| **Weekend** | Every Friday | Day-trips, local festivals, hiking routes |
| **Monthly** | 1st of the month | City breaks, seasonal travel, longer retreats |

Each suggestion arrives as a notification. The customer can:
- **Accept** — they plan to attend (add a review comment later)
- **Reject** — not interested (optional comment to explain why)
- **Wishlist** — nice idea but not currently possible

Every 5 feedback responses the system asks OpenAI to summarise what it has learned about the customer and stores a *learned profile* that shapes all future prompts.

---

## Tech stack

- **Java 21** with virtual threads
- **Spring Boot 4.0.5** (Spring Framework 7, Spring Security 7)
- **Spring Data JPA** + **PostgreSQL** (schema managed by Flyway)
- **OpenAI GPT-4o** via `RestClient`
- **JWT** authentication (`jjwt 0.12.6`)
- **Swagger UI** via `springdoc-openapi 2.8.8`
- **Docker Compose** for local infrastructure

---

## Prerequisites

| Tool | Minimum version |
|---|---|
| JDK | 21 |
| Maven | 3.9 (or use the included `./mvnw`) |
| Docker & Docker Compose | 24+ |
| OpenAI API key | — |

---

## Running the application

### Option A — Database in Docker, app from Maven (recommended for development)

#### 1. Clone the repository

```bash
git clone <repo-url>
cd livelife  # or your project directory
```

#### 2. Set environment variables

```bash
export OPENAI_API_KEY=sk-...
# JWT_SECRET is optional — a dev default is used if not set
```

#### 3. Start PostgreSQL

```bash
docker-compose up -d db
```

This starts a `postgres:16-alpine` container on port `5432` with:
- Database: `eventsuggestion`
- Username: `eventsuggestion`
- Password: `eventsuggestion`

Wait for it to be healthy:

```bash
docker-compose ps   # STATUS should show "healthy"
```

#### 4. Run the application

```bash
./mvnw spring-boot:run
```

Flyway runs automatically on startup and applies `V1__initial_schema.sql`. The server starts on **http://localhost:8080**.

---

### Option B — Full stack in Docker

Builds the app image and starts both the database and the application together.

```bash
export OPENAI_API_KEY=sk-...

# Build the image and start everything
docker-compose up --build
```

To run in the background:

```bash
docker-compose up --build -d
```

Useful commands:

```bash
docker-compose logs -f app        # Stream application logs
docker-compose logs -f db         # Stream database logs
docker-compose down               # Stop all containers
docker-compose down -v            # Stop and delete the database volume
```

---

## Exploring the API

### Swagger UI

Open **http://localhost:8080/swagger-ui/index.html** in your browser.

1. Call `POST /api/auth/register` to create an account and get a JWT token.
2. Click the **Authorize** button (top-right), paste the token, and click **Authorize**.
3. All other endpoints are now authenticated.

---

## Quick API walkthrough

```bash
# 1. Register
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jane@example.com",
    "password": "secret123",
    "firstName": "Jane",
    "lastName": "Doe",
    "gender": "FEMALE",
    "age": 30,
    "address": { "city": "London", "country": "UK" },
    "education": "MSc Computer Science",
    "currentEmployment": "Software Engineer",
    "sports": ["yoga", "cycling"],
    "hobbies": ["photography", "cooking"],
    "interests": ["jazz", "contemporary art"],
    "likesTraveling": true,
    "likesNightlife": false
  }'
# → { "token": "eyJ...", "email": "jane@example.com", ... }

TOKEN=eyJ...

# 2. Generate suggestions (calls OpenAI)
curl -s -X POST "http://localhost:8080/api/suggestions/generate?category=DAILY" \
  -H "Authorization: Bearer $TOKEN"

# 3. Check notification feed
curl -s http://localhost:8080/api/suggestions/notifications \
  -H "Authorization: Bearer $TOKEN"

# 4. Accept suggestion #1
curl -s -X PUT http://localhost:8080/api/suggestions/1/feedback \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{ "status": "ACCEPTED", "comment": "Loved it!" }'

# 5. Reject suggestion #2 with a reason (helps the AI learn)
curl -s -X PUT http://localhost:8080/api/suggestions/2/feedback \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{ "status": "REJECTED", "comment": "Not into opera" }'

# 6. Snooze suggestion #3 until next week
curl -s -X PUT http://localhost:8080/api/suggestions/3/snooze \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{ "until": "2027-01-08" }'

# 7. Rate suggestion #1 after attending (must be ACCEPTED and past its date)
curl -s -X PUT http://localhost:8080/api/suggestions/1/rating \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{ "rating": 5 }'
```

---

## Scheduled suggestion generation

| Cron | Category | Trigger |
|---|---|---|
| `0 0 8 * * *` | DAILY | Every day at 08:00 |
| `0 0 9 * * FRI` | WEEKEND | Every Friday at 09:00 |
| `0 0 10 1 * *` | MONTHLY | 1st of each month at 10:00 |

---

## Configuration reference

All settings can be overridden via environment variables:

| Property | Env variable | Default |
|---|---|---|
| `spring.datasource.url` | `DB_URL` | `jdbc:postgresql://localhost:5432/eventsuggestion` |
| `spring.datasource.username` | `DB_USERNAME` | `eventsuggestion` |
| `spring.datasource.password` | `DB_PASSWORD` | `eventsuggestion` |
| `openai.api-key` | `OPENAI_API_KEY` | *(empty)* |
| `openai.model` | — | `gpt-4o` |
| `app.jwt.secret` | `JWT_SECRET` | dev default |
| `app.jwt.expiration-ms` | — | `86400000` (24 h) |

---

## Changelog

### 0.0.6
- Added `PUT /api/suggestions/{id}/snooze` to hide a pending suggestion from the notification feed until a given date; the notifications query now filters on `snoozedUntil` (see `SuggestionRepository.findActiveNotifications`).
- Added `PUT /api/suggestions/{id}/rating` to record a 1-5 star rating on an accepted suggestion once its date has passed.
- New migration `V2__suggestion_snooze_and_rating.sql` adds `snoozed_until` and `rating` columns to `suggestions`.

### 0.0.5
- `GlobalExceptionHandler` now returns RFC 7807 `ProblemDetail` responses (`application/problem+json`) instead of the previous ad-hoc `ErrorResponse` record and raw `Map` for validation errors; field-level validation errors are now nested under an `errors` property.
- Added `SuggestionIntegrationTest`, a full-stack test covering every `SuggestionController` endpoint (list, filter, notifications, generate, feedback, mark-read) including negative cases for invalid status, missing status, unknown suggestion id, and cross-customer ownership isolation.

### 0.0.4
- Added a unit test suite covering `CustomerService`, `SuggestionService`, `OpenAIService`, `JwtUtil`, `JwtAuthFilter`, and `UserDetailsServiceImpl`, plus a full-stack `SecurityIntegrationTest` that verifies JWT enforcement end-to-end against the existing H2/Flyway test profile.
- Added a CircleCI pipeline (`.circleci/config.yml`) that runs `mvnw test` and `mvnw package` on every push, with Maven dependency caching and JUnit test result reporting.

### 0.0.3
- Fixed `JwtUtil` deriving its signing key through a redundant Base64 encode/decode round trip; it now uses the secret's raw UTF-8 bytes directly.
- Fixed `OpenAIService.isApiKeyMissing()`, which checked a field that was never null and so never detected a missing key. The `openai.api-key` default was also changed from the placeholder `test` to empty, matching this table.
- `GlobalExceptionHandler`'s catch-all handler now logs unhandled exceptions instead of swallowing them silently.