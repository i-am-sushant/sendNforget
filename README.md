# sendNforget

A distributed notification pipeline with a producer (ingestion-service), a consumer (worker-service), and a simple dashboard UI. RabbitMQ handles messaging and PostgreSQL stores job status.

## Project Structure

```
/infra               Docker Compose for Postgres, RabbitMQ, Redis
/ingestion-service   Spring Boot producer API (HTTP -> RabbitMQ)
/worker-service      Spring Boot consumer (RabbitMQ -> Postgres + status API)
/dashboard           Static HTML/JS dashboard
```

## Prerequisites

- Java 21
- Maven 3.9+
- Docker Desktop (for infra)

## Infrastructure

Start the local dependencies:

```
docker compose -f infra/docker-compose.yml up -d
```

Services exposed:
- Postgres: localhost:5432 (db: sendnforget, user: admin, pass: password)
- RabbitMQ: localhost:7673 (AMQP), http://localhost:17673 (UI)
- Redis: localhost:6379

## Services

### Ingestion Service (Producer)

**Location:** ingestion-service

**Config:** [ingestion-service/src/main/resources/application.yml](ingestion-service/src/main/resources/application.yml)

- Port: 8080
- RabbitMQ host/port: localhost:7673

**Run:**

```
cd ingestion-service
mvn spring-boot:run
```

**Endpoint:**

- POST http://localhost:8080/api/v1/notify

Example body:

```json
{
  "clientId": "c1",
  "recipient": "user@example.com",
  "message": "hello"
}
```

Returns 202 Accepted with a generated `trackingId`.

### Worker Service (Consumer + DB)

**Location:** worker-service

**Config:** [worker-service/src/main/resources/application.yml](worker-service/src/main/resources/application.yml)

- Port: 8081
- Postgres: jdbc:postgresql://localhost:5432/sendnforget
- RabbitMQ: localhost:7673
- Retry: max attempts 3, initial 2s, multiplier 2
- Timezone: UTC (for JDBC)

**Run:**

```
cd worker-service
mvn spring-boot:run
```

**Endpoint:**

- GET http://localhost:8081/api/v1/jobs

Returns all job logs in Postgres. `@CrossOrigin` is enabled so the dashboard can fetch.

**Email delivery (Gmail SMTP):**

The worker sends real emails using Spring Mail with Gmail SMTP. Configure these env vars:

- `GMAIL_SMTP_USER` (e.g., your Gmail address)
- `GMAIL_SMTP_PASSWORD` (app password recommended)

Email subject template: `[sendNforget][clientId] Notification`.

## Dashboard

**Location:** [dashboard/index.html](dashboard/index.html)

- Bootstrap 5 UI
- Polls the worker API every 3 seconds
- Refresh button to manually reload
- Form to submit notifications to ingestion API (clientId, recipient, message)

Open the file in a browser.

**API base URL overrides:**

The dashboard supports same-origin paths when behind a reverse proxy:

- Ingestion: `/api/v1/notify`
- Worker: `/api/v1/jobs`

For local development, defaults are:

- Ingestion: `http://localhost:8080`
- Worker: `http://localhost:8081`

Override using query params:

- `?apiBase=https://your-domain`
- `?jobsBase=https://your-domain`

You can also set `window.SNF_API_BASE` / `window.SNF_JOBS_BASE` before the script runs (e.g., via a reverse proxy injecting a small script block).

## End-to-End Test

1. Start Docker Compose.
2. Run ingestion-service.
3. Run worker-service.
4. POST a notification to ingestion-service.
5. Open dashboard and watch the job status update.

## Architecture

**High-level flow**

Client → Ingestion Service → RabbitMQ → Worker Service → Postgres → Dashboard

**Key behaviors**
- Ingestion generates `trackingId` and enqueues message.
- Worker consumes, simulates work, randomly fails (30%) to trigger retry.
- Job status transitions: PROCESSING → SENT or PROCESSING → FAILED.

## Troubleshooting

- If worker service fails with timezone errors, ensure JVM timezone is UTC (already set in code) and Postgres is running.
- If RabbitMQ connection fails, verify Docker Compose is up and port 7673 is mapped.
- If dashboard shows CORS errors, confirm worker is running and `@CrossOrigin` is present.

## Future Enhancements

- Dead-letter queue for failed messages after retries.
- Auth for APIs and dashboard.
- Redis-backed caching or rate limiting.
- Observability: logs, metrics, tracing.

## Docker & CI/CD

Dockerfiles are included for both services:

- [ingestion-service/Dockerfile](ingestion-service/Dockerfile)
- [worker-service/Dockerfile](worker-service/Dockerfile)

GitHub Actions workflows:

- CI (build + test): [.github/workflows/ci.yml](.github/workflows/ci.yml)
- Docker build & push to GHCR: [.github/workflows/docker.yml](.github/workflows/docker.yml)

Images are published to:

- `ghcr.io/<owner>/<repo>-ingestion-service`
- `ghcr.io/<owner>/<repo>-worker-service`

## DigitalOcean Droplet Deployment (Example)

1. Create a Docker-enabled droplet and install Docker + Docker Compose.
2. Log in to GHCR on the droplet:

  - `echo $GITHUB_TOKEN | docker login ghcr.io -u <github-username> --password-stdin`

3. Pull the images:

  - `docker pull ghcr.io/<owner>/<repo>-ingestion-service:latest`
  - `docker pull ghcr.io/<owner>/<repo>-worker-service:latest`

4. Start infra (Postgres + RabbitMQ) using [infra/docker-compose.yml](infra/docker-compose.yml).
5. Run services with env vars, e.g.:

  - `GMAIL_SMTP_USER=...`
  - `GMAIL_SMTP_PASSWORD=...`
  - `SPRING_DATASOURCE_URL=jdbc:postgresql://<db-host>:5432/sendnforget`
  - `SPRING_RABBITMQ_HOST=<rabbit-host>`

6. Put a reverse proxy (e.g., Nginx) in front so:

  - `/` serves the dashboard
  - `/api/v1/notify` routes to ingestion-service
  - `/api/v1/jobs` routes to worker-service
