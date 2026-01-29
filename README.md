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

This deployment assumes Nginx terminates TLS and proxies:

- `/api/v1/notify` → `http://127.0.0.1:8080`
- `/api/v1/jobs` → `http://127.0.0.1:8081`

### Production layout (Droplet)

- Production compose file: `/opt/sendnforget/docker-compose.yml` (copy from [infra/deploy/docker-compose.yml](infra/deploy/docker-compose.yml))
- Production env file: `/opt/sendnforget/.env`
- Static dashboard: `/var/www/sendnforget/dashboard` (served by Nginx)

Local infra for development remains in [infra/docker-compose.yml](infra/docker-compose.yml).

### Server .env keys (production)

Only these keys are required in `/opt/sendnforget/.env`:

- `GHCR_REPOSITORY=<owner>/<repo>`
- `GMAIL_SMTP_USER=<gmail address>`
- `GMAIL_SMTP_PASSWORD=<gmail app password>`

### Production compose example (apps + infra)

`/opt/sendnforget/docker-compose.yml` runs Postgres, RabbitMQ, and both app services. Apps are bound to localhost ports for Nginx, while dependencies stay internal to the compose network.

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_USER: admin
      POSTGRES_PASSWORD: password
      POSTGRES_DB: sendnforget
    volumes:
      - snf_pg_data:/var/lib/postgresql/data

  rabbitmq:
    image: rabbitmq:3-management
    environment:
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest

  ingestion-service:
    image: ghcr.io/${GHCR_REPOSITORY}-ingestion-service:latest
    depends_on:
      - rabbitmq
    ports:
      - "127.0.0.1:8080:8080"
    environment:
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_PORT: 5672

  worker-service:
    image: ghcr.io/${GHCR_REPOSITORY}-worker-service:latest
    depends_on:
      - postgres
      - rabbitmq
    ports:
      - "127.0.0.1:8081:8081"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/sendnforget
      SPRING_DATASOURCE_USERNAME: admin
      SPRING_DATASOURCE_PASSWORD: password
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_PORT: 5672
      GMAIL_SMTP_USER: ${GMAIL_SMTP_USER}
      GMAIL_SMTP_PASSWORD: ${GMAIL_SMTP_PASSWORD}

volumes:
  snf_pg_data:
```

### GitHub Actions deploy secrets

Set these secrets in GitHub Actions for the SSH deploy job:

- `DEPLOY_HOST`
- `DEPLOY_USER`
- `DEPLOY_SSH_KEY`
- `DEPLOY_PORT`
- `DEPLOY_COMPOSE_PATH` (set to `/opt/sendnforget`)

### Deploy flow

The deploy workflow is manual-only (run it via GitHub Actions → “Run workflow”).

Inputs:

- `deploy_dashboard` (default: false) — syncs the static dashboard to `/var/www/sendnforget/dashboard`.
- `clean_repo` (default: false) — runs `git clean -fd` after resetting the repo.

When `deploy_dashboard` or `clean_repo` is true, the droplet keeps a public HTTPS clone at `/opt/sendnforget/repo` and runs:

- `git fetch origin main`
- `git reset --hard origin/main`
- (optional) `git clean -fd`

When `deploy_dashboard` is true, it also runs:

- `rsync -av --delete /opt/sendnforget/repo/dashboard/ /var/www/sendnforget/dashboard/`

Containers are always deployed after image build/push:

- `docker compose pull`
- `docker compose up -d`

No GHCR login is required because the images are public.
