# Convo Backend

Spring Boot (Java 21) backend for **Convo**. This repository owns authentication, WebRTC call signalling, and meeting lifecycle — it never touches media (video/audio/file bytes go directly between browsers, or through a TURN relay).

Other repositories:

- Frontend: https://github.com/Capstone-Project-BUET-CSE-21/convo-frontend
- Audio watermarking service: https://github.com/Capstone-Project-BUET-CSE-21/convo-audio-watermarking
- File sharing / provenance service: convo-file-sharing (sibling repo)

---

## Project overview

- **Auth** — signup/login (BCrypt password hashing), JWT issuance and validation.
- **Signalling** — a single authenticated WebSocket endpoint (`/ws`) that relays WebRTC offers/answers/ICE candidates, chat messages, and mute/camera state between peers in a room. Pure relay: no media passes through this service.
- **Meeting lifecycle** — persists `Meeting`/`MeetingUser` rows (who joined which meeting, when).
- **TURN/STUN credential hand-out** — a REST endpoint that returns ICE server credentials to authenticated clients.
- **Internal service-to-service API** — lets `convo-file-sharing` and `convo-audio-watermark` check meeting participation without holding a copy of this service's data, authenticated by a shared key rather than a user JWT.

---

## Project structure

```
backend/
├── mvnw, mvnw.cmd, pom.xml
├── Dockerfile (repo root)
├── src/
│   ├── main/java/com/convo/backend/
│   │   ├── BackendApplication.java        # entry point; local-dev .env -> system property bridge
│   │   ├── auth/
│   │   │   ├── config/JwtProperties.java
│   │   │   ├── controller/AuthController.java     # /api/backend/auth/**
│   │   │   ├── dto/
│   │   │   ├── entity/User.java
│   │   │   ├── repository/UserRepository.java
│   │   │   ├── security/JwtAuthenticationFilter.java
│   │   │   └── service/{AuthService,JwtService}.java
│   │   ├── config/
│   │   │   ├── InternalServiceProperties.java
│   │   │   ├── TurnCredentialProperties.java
│   │   │   └── WebAndSecurityConfig.java  # Spring Security filter chain + CORS
│   │   ├── signalling/
│   │   │   ├── config/WebSocketConfig.java        # registers the /ws handler
│   │   │   ├── controller/
│   │   │   │   ├── InternalMeetingController.java # /api/backend/internal/** (service-key auth)
│   │   │   │   ├── MeetingEntryController.java     # /api/backend/meeting-entry (JWT auth)
│   │   │   │   └── ServerCredentialController.java # /api/backend/credentials (JWT auth)
│   │   │   ├── entity/{Meeting,MeetingUser}.java
│   │   │   ├── repository/
│   │   │   ├── service/{MeetingLifecycleService,ServerCredentialService}.java
│   │   │   └── websocket/SignalingHandler.java     # the /ws protocol implementation
│   │   └── user/
│   │       ├── controller/UserController.java      # /api/backend/users/** (JWT auth)
│   │       └── service/UserLookupService.java
│   ├── main/resources/application.properties
│   └── test/java/com/convo/backend/BackendApplicationTests.java
└── target/
```

---

## Endpoints

| Method | Path | Auth | What it does |
|---|---|---|---|
| POST | `/api/backend/auth/signup` | none | Create an account, returns a JWT |
| POST | `/api/backend/auth/login` | none | Returns a JWT |
| GET | `/api/backend/auth/me` | JWT | Caller's own profile |
| GET | `/api/backend/users/{id}` | JWT | Public display info for any user id |
| POST | `/api/backend/users/batch` | JWT | Resolve many user ids in one call |
| POST | `/api/backend/meeting-entry` | JWT | Create/join a meeting (REST side of meeting lifecycle) |
| GET | `/api/backend/credentials` | JWT | STUN/TURN ICE server list |
| GET | `/api/backend/internal/meetings/{code}/participants` | `X-Internal-Service-Key` header | Server-to-server: list everyone who's ever joined a meeting |
| WS | `/ws?token=<jwt>` | JWT (query param) | Signalling: offer/answer/ICE relay, room membership, chat |
| GET | `/actuator/health`, `/actuator/info` | none | Health checks (backs the Docker `HEALTHCHECK`) |

---

## Required environment variables

| Var | Purpose |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASS` | Postgres connection |
| `JWT_SECRET` | Signs/verifies login tokens — must match the value `convo-file-sharing` and `convo-audio-watermark` are configured with |
| `JWT_EXPIRATION_MS` | Token lifetime in ms (optional, defaults to 24h) |
| `INTERNAL_SERVICE_KEY` | Authenticates the internal participants API — must match the value the other two services send |
| `TURN_USERNAME`, `TURN_CREDENTIAL` | metered.ca TURN relay credentials, handed to clients for ICE |
| `PORT` | Optional, defaults to 8080 |

All of the above except `PORT` and `JWT_EXPIRATION_MS` are required — the app fails fast at startup with a clear error if any is unset. Locally, put them in a `.env` file in `backend/` (gitignored); in production (Render), set them as real platform environment variables.

---

## Running locally

```
cd backend
./mvnw spring-boot:run       # or mvnw.cmd on Windows
```

Starts on `http://localhost:8080`.

Run tests:

```
./mvnw clean test
```

---

## Technologies

| Component | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.1 |
| Security | Spring Security, JWT (jjwt) |
| Real-time | Spring WebSocket |
| Persistence | Spring Data JPA / Hibernate, PostgreSQL |
| Deployment | Docker (multi-stage build) on Render |
