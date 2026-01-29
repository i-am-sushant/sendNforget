# sendNforget

> A **distributed notification pipeline** demonstrating microservices architecture, event-driven design, and asynchronous processing with Spring Boot, RabbitMQ, PostgreSQL, and Gmail SMTP.

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-green)](https://spring.io/projects/spring-boot)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3-orange)](https://www.rabbitmq.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-blue)](https://docs.docker.com/compose/)

---

## 📋 Table of Contents

- [Overview](#-overview)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
  - [Prerequisites](#prerequisites)
  - [Infrastructure Setup](#infrastructure-setup)
  - [Running the Services](#running-the-services)
- [API Reference](#-api-reference)
- [Dashboard](#-dashboard)
- [Key Technical Concepts](#-key-technical-concepts)
- [Data Flow](#-data-flow)
- [Configuration Reference](#-configuration-reference)
- [Docker & CI/CD](#-docker--cicd)
- [Production Deployment](#-production-deployment)
- [Troubleshooting](#-troubleshooting)
- [Future Enhancements](#-future-enhancements)

---

## 🎯 Overview

**sendNforget** is a fire-and-forget notification system that:

1. **Accepts** notification requests via REST API
2. **Queues** them asynchronously in RabbitMQ
3. **Processes** with automatic retry on failure
4. **Delivers** actual emails via Gmail SMTP
5. **Tracks** job status in PostgreSQL
6. **Displays** real-time status in a web dashboard

This project demonstrates production-ready patterns for building scalable, resilient distributed systems.

---

## 🏗️ Architecture

```
┌─────────────┐     HTTP POST      ┌───────────────────┐
│   Client    │ ─────────────────► │  Ingestion        │
│  (Dashboard │     202 Accepted   │  Service          │
│   or API)   │ ◄───────────────── │  (Port 8080)      │
└─────────────┘                    └────────┬──────────┘
                                            │ Publish
                                            ▼
                               ┌────────────────────────┐
                               │      RabbitMQ          │
                               │   snf_tasks_queue      │
                               │   (Durable Queue)      │
                               └────────────┬───────────┘
                                            │ Consume
                                            ▼
                               ┌────────────────────────┐
                               │   Worker Service       │
                               │   (Port 8081)          │
                               │                        │
                               │   • Retry: 3 attempts  │
                               │   • Backoff: 2s, 4s    │
                               │   • Email via SMTP     │
                               └────────┬───────────────┘
                                        │
                         ┌──────────────┴────────────────┐
                         ▼                               ▼
              ┌─────────────────┐             ┌─────────────────┐
              │   PostgreSQL    │             │   Gmail SMTP    │
              │   (job_logs)    │             │   (Delivery)    │
              └────────┬────────┘             └─────────────────┘
                       │
                       │ GET /api/v1/jobs
                       ▼
              ┌─────────────────┐
              │   Dashboard     │
              │   (Bootstrap 5) │
              └─────────────────┘
```

---

## 🛠️ Tech Stack

| Component | Technology | Purpose |
|-----------|------------|---------|
| **Ingestion API** | Spring Boot 4.0, Java 21 | REST API, message producer |
| **Worker** | Spring Boot 4.0, Spring Data JPA | Message consumer, email sender |
| **Message Broker** | RabbitMQ 3 | Async messaging, retry handling |
| **Database** | PostgreSQL 16 | Job status persistence |
| **Cache** | Redis 7 | Rate limiting (future) |
| **Email** | Gmail SMTP | Actual email delivery |
| **Frontend** | HTML5, Bootstrap 5, Vanilla JS | Dashboard UI |
| **Containerization** | Docker, Docker Compose | Local dev & production |
| **CI/CD** | GitHub Actions | Build, test, deploy |

---

## 📁 Project Structure

```
sendNforget/
├── infra/                          # Infrastructure
│   ├── docker-compose.yml          # Local dev (Postgres, RabbitMQ, Redis)
│   └── deploy/
│       └── docker-compose.yml      # Production deployment
│
├── ingestion-service/              # Producer Microservice
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/sendnforget/ingestion/
│       ├── IngestionServiceApplication.java
│       ├── config/RabbitConfig.java
│       ├── controller/IngestionController.java
│       └── model/NotificationRequest.java
│
├── worker-service/                 # Consumer Microservice
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/sendnforget/worker/
│       ├── WorkerServiceApplication.java
│       ├── config/RabbitConfig.java
│       ├── consumer/WorkerConsumer.java
│       ├── controller/JobStatusController.java
│       ├── model/
│       │   ├── JobLog.java
│       │   └── NotificationRequest.java
│       └── repository/JobLogRepository.java
│
└── dashboard/                      # Frontend
    └── index.html
```

---

## 🚀 Getting Started

### Prerequisites

- **Java 21** (JDK)
- **Maven 3.9+**
- **Docker Desktop** (for infrastructure)

### Infrastructure Setup

Start PostgreSQL, RabbitMQ, and Redis:

```bash
docker compose -f infra/docker-compose.yml up -d
```

**Services exposed:**

| Service | Port | Credentials |
|---------|------|-------------|
| PostgreSQL | `localhost:5432` | db: `sendnforget`, user: `admin`, pass: `password` |
| RabbitMQ (AMQP) | `localhost:7673` | user: `guest`, pass: `guest` |
| RabbitMQ (UI) | `http://localhost:17673` | user: `guest`, pass: `guest` |
| Redis | `localhost:6379` | - |

### Running the Services

**Terminal 1 - Ingestion Service:**
```bash
cd ingestion-service
mvn spring-boot:run
```

**Terminal 2 - Worker Service:**
```bash
# Set Gmail credentials (required for email delivery)
export GMAIL_SMTP_USER=your-email@gmail.com
export GMAIL_SMTP_PASSWORD=your-app-password

cd worker-service
mvn spring-boot:run
```

**Terminal 3 - Dashboard:**
```bash
# Open in browser or use Live Server
open dashboard/index.html
```

---

## 📡 API Reference

### Ingestion Service (Port 8080)

#### `POST /api/v1/notify`

Submit a notification for async processing.

**Request:**
```json
{
  "clientId": "acme-corp",
  "recipient": "user@example.com",
  "message": "Your order has shipped!"
}
```

**Response (202 Accepted):**
```json
{
  "trackingId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "QUEUED"
}
```

### Worker Service (Port 8081)

#### `GET /api/v1/jobs`

Retrieve all job logs.

**Response:**
```json
[
  {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "status": "SENT",
    "recipient": "user@example.com",
    "retryCount": 1,
    "createdAt": "2026-01-29T10:30:00Z"
  }
]
```

**Status Values:** `PROCESSING` | `SENT` | `FAILED`

---

## 🖥️ Dashboard

**Location:** [dashboard/index.html](dashboard/index.html)

**Features:**
- Bootstrap 5 responsive UI
- Auto-refresh every 3 seconds
- Form to submit test notifications
- Status badges: 🟢 SENT | 🔴 FAILED | ⚪ PROCESSING

**Running locally:** Open the HTML file directly or use VS Code Live Server.

**API Base URL Configuration:**

| Environment | Ingestion API | Jobs API |
|-------------|---------------|----------|
| Local | `http://localhost:8080` | `http://localhost:8081` |
| Production | Same origin (`/api/v1/...`) | Same origin |

Override via query params: `?apiBase=https://...&jobsBase=https://...`

---

## 🎓 Key Technical Concepts

### 1. Event-Driven Architecture
The system uses RabbitMQ as a message broker to **decouple** producers from consumers. Services communicate asynchronously through events (messages), enabling independent scaling and deployment.

### 2. Fire-and-Forget Pattern
The API returns `202 Accepted` immediately without waiting for processing. The client receives a `trackingId` to check status later. This pattern maximizes throughput and responsiveness.

### 3. Producer-Consumer Pattern
```
Ingestion (Producer) → Queue → Worker (Consumer)
```
Multiple workers can consume from the same queue for horizontal scaling.

### 4. Retry with Exponential Backoff
```yaml
retry:
  enabled: true
  max-attempts: 3
  initial-interval: 2000ms   # First retry after 2s
  multiplier: 2.0            # Second retry after 4s
```
Prevents overwhelming failing services while recovering from transient errors.

### 5. Message Serialization
Uses Jackson's `JacksonJsonMessageConverter` for automatic JSON serialization/deserialization of messages.

### 6. JPA Entity Lifecycle
Job status transitions: `PROCESSING → SENT` or `PROCESSING → FAILED`

### 7. Multi-Stage Docker Build
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build  # Build stage
FROM eclipse-temurin:21-jre                  # Runtime stage (smaller)
```

---

## 🔄 Data Flow

```
1. Client POSTs to /api/v1/notify
   │
   ▼
2. Ingestion Service generates trackingId (UUID)
   │
   ▼
3. Message published to RabbitMQ (snf_tasks_queue)
   │
   ▼
4. Worker consumes message
   │
   ├── Saves JobLog (status: PROCESSING)
   ├── Simulates work (2s delay)
   ├── 30% chance: throws exception → retry
   ├── Sends email via SMTP
   └── Updates JobLog (status: SENT/FAILED)
   │
   ▼
5. Dashboard polls GET /api/v1/jobs → displays status
```

---

## ⚙️ Configuration Reference

### Ingestion Service (`application.yml`)

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8080` | HTTP port |
| `spring.rabbitmq.host` | `localhost` | RabbitMQ host |
| `spring.rabbitmq.port` | `7673` | RabbitMQ AMQP port |

### Worker Service (`application.yml`)

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8081` | HTTP port |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/sendnforget` | Postgres URL |
| `spring.rabbitmq.listener.simple.retry.max-attempts` | `3` | Max retry attempts |
| `spring.mail.host` | `smtp.gmail.com` | SMTP server |
| `GMAIL_SMTP_USER` | (env var) | Gmail address |
| `GMAIL_SMTP_PASSWORD` | (env var) | Gmail app password |

---

## 🐳 Docker & CI/CD

### Dockerfiles

Both services use multi-stage builds:

| Service | Dockerfile | Image Size |
|---------|------------|------------|
| Ingestion | [ingestion-service/Dockerfile](ingestion-service/Dockerfile) | ~200MB |
| Worker | [worker-service/Dockerfile](worker-service/Dockerfile) | ~200MB |

### GitHub Actions Workflows

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| CI | Push/PR to main | Build, test |
| Docker | Push to main | Build & push images to GHCR |
| Deploy | Manual | SSH deploy to production |

### Container Registry

Images published to GitHub Container Registry:
- `ghcr.io/<owner>/<repo>-ingestion-service:main`
- `ghcr.io/<owner>/<repo>-worker-service:main`

---

## 🌐 Production Deployment

### DigitalOcean Droplet Example

**Architecture:**
```
Internet → Nginx (TLS) → localhost:8080 (Ingestion)
                       → localhost:8081 (Worker)
                       → /var/www/.../dashboard (Static)
```

### Production Layout

| Path | Content |
|------|---------|
| `/opt/sendnforget/docker-compose.yml` | Production compose |
| `/opt/sendnforget/.env` | Environment secrets |
| `/var/www/sendnforget/dashboard` | Static dashboard |

### Environment Variables (`.env`)

```bash
GHCR_REPOSITORY=owner/repo
GMAIL_SMTP_USER=your-email@gmail.com
GMAIL_SMTP_PASSWORD=your-app-password
```

### Production Compose

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

### GitHub Actions Deploy Secrets

| Secret | Description |
|--------|-------------|
| `DEPLOY_HOST` | Server IP/hostname |
| `DEPLOY_USER` | SSH username |
| `DEPLOY_SSH_KEY` | Private SSH key |
| `DEPLOY_PORT` | SSH port |
| `DEPLOY_COMPOSE_PATH` | `/opt/sendnforget` |

### Deploy Commands

`ash
docker compose pull
docker compose up -d
`

---

## 🔧 Troubleshooting

| Issue | Solution |
|-------|----------|
| Worker timezone errors | JVM timezone is set to UTC in code; ensure Postgres is running |
| RabbitMQ connection refused | Verify Docker Compose is up and port 7673 is mapped |
| Dashboard CORS errors | Confirm worker is running and `@CrossOrigin` is present |
| Email not sending | Check `GMAIL_SMTP_USER` and `GMAIL_SMTP_PASSWORD` env vars |
| Port conflicts | Ensure ports 5432, 7673, 8080, 8081 are not in use |

---

## 🔮 Future Enhancements

| Feature | Description | Tech |
|---------|-------------|------|
| **Rate Limiting** | 5 requests/min per clientId | Redis |
| **Idempotency** | Deduplicate by requestId | Redis + TTL |
| **Priority Queues** | VIP treatment for urgent messages | RabbitMQ priority |
| **Search & Filter** | Find jobs by recipient/status | JPA queries |
| **Dead-Letter Queue** | Handle permanent failures | RabbitMQ DLX |
| **Authentication** | API key or JWT | Spring Security |
| **Observability** | Logs, metrics, tracing | Prometheus, Grafana, Zipkin |

---

## 📜 License

This project is licensed under the terms in the [LICENSE](LICENSE) file.