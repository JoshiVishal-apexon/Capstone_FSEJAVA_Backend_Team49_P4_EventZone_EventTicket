# EventZone — Auth APIs

Spring Boot 3.3 backend trimmed down to the five authentication endpoints.

| # | API | Method | Path | Auth | Success |
|---|-----|--------|------|------|---------|
| 1 | Login as Admin | POST | `/api/auth/login` | none | 200 |
| 2 | Login as Organiser | POST | `/api/auth/login` | none | 200 |
| 3 | Login as Attendee | POST | `/api/auth/login` | none | 200 |
| 4 | Register | POST | `/api/auth/register` | none | 201 |
| 5 | Logout | POST | `/api/auth/logout` | Bearer token | 204 |

Login is one endpoint for all three roles — the role comes from the stored user
record and is returned in the response and embedded in the JWT.

## Run

Requires JDK 17. Maven is used from `PATH`, or auto-downloaded by the scripts.

```powershell
.\scripts\run.ps1              # http://localhost:8080
.\scripts\test.ps1             # JUnit 5 + Mockito unit tests
.\scripts\smoke-test.ps1       # hits all five endpoints against a running app
```

Or directly:

```bash
mvn spring-boot:run
mvn test
```

The default `dev` profile uses a file-backed H2 database at `./data/eventzonedb.mv.db`,
so no external database is needed. Delete the `data/` folder to reset.

## Seeded accounts

`DataSeeder` creates (or normalizes on every boot) one account per role.
Password for all three: `Password@123`

| Role | Email |
|------|-------|
| ADMIN | admin@eventzone.com |
| ORGANISER | organiser1@eventzone.com |
| ATTENDEE | attendee1@eventzone.com |

## Requests and responses

### 1–3. Login — `POST /api/auth/login`

```json
{ "email": "admin@eventzone.com", "password": "Password@123" }
```

```json
{
  "token": "eyJhbGciOiJIUzM4NCJ9...",
  "name": "Admin User",
  "role": "ADMIN",
  "email": "admin@eventzone.com"
}
```

Swap the email for `organiser1@eventzone.com` (role `ORGANISER`) or
`attendee1@eventzone.com` (role `ATTENDEE`). Bad credentials return 401.

### 4. Register — `POST /api/auth/register`

```json
{ "email": "new.user@eventzone.com", "password": "Password@123", "name": "New Attendee" }
```

201 Created:

```json
{
  "id": "1cf39c09-a6bc-464f-8a9d-e34cef4b4313",
  "email": "new.user@eventzone.com",
  "name": "New Attendee",
  "role": "ATTENDEE"
}
```

Self-registration always creates an `ATTENDEE`. Admin and organiser accounts are
provisioned by the seeder, not through this endpoint. A duplicate email returns
409; a password shorter than 8 characters returns 400.

### 5. Logout — `POST /api/auth/logout`

Send `Authorization: Bearer <token>`. Returns 204 No Content. JWT auth is
stateless, so there is no server-side session to invalidate — the endpoint
exists so the client has a symmetrical call to make while discarding its token.

## curl

```bash
# Login (admin / organiser / attendee)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@eventzone.com","password":"Password@123"}'

# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"new.user@eventzone.com","password":"Password@123","name":"New Attendee"}'

# Logout
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer <token>"
```

## Errors

All failures share one shape:

```json
{
  "timestamp": "2026-09-05T08:36:49Z",
  "path": "/api/auth/login",
  "error": "UNAUTHORIZED",
  "message": "Invalid email or password"
}
```

| Status | error | When |
|--------|-------|------|
| 400 | `VALIDATION_ERROR` | missing/invalid field, password under 8 chars, malformed JSON |
| 401 | `UNAUTHORIZED` | wrong email or password; missing/invalid token on a protected route |
| 403 | `FORBIDDEN` | authenticated but not permitted |
| 409 | `CONFLICT` | registering an email that already exists |
| 500 | `INTERNAL_ERROR` | unexpected server error |

## Project layout

```
src/main/java/com/eventzone/
  EventzoneApplication.java
  config/        CorsConfig, DataSeeder
  controller/    AuthController          -> the five endpoints
  dto/auth/      LoginRequest, RegisterRequest, AuthResponse, UserResponse
  dto/common/    ErrorResponse
  entity/        User
  exception/     ApiException, ConflictException, UnauthorizedException, GlobalExceptionHandler
  repository/    UserRepository
  security/      SecurityConfig, JwtUtil, JwtAuthFilter, CustomUserDetailsService,
                 EventZoneUserPrincipal, JwtAuthenticationEntryPoint, JwtAccessDeniedHandler
  service/       AuthService
src/test/java/com/eventzone/service/AuthServiceTest.java
```

## Other endpoints

Swagger UI at `/swagger-ui.html`, OpenAPI JSON at `/v3/api-docs`, the H2 console
at `/h2-console`, and actuator health/metrics at `/actuator/health`,
`/actuator/metrics`, `/actuator/prometheus`. Everything else requires a valid JWT.

## Configuration

| Env var | Default | Purpose |
|---------|---------|---------|
| `JWT_SECRET` | dev-only fallback in `application.yml` | HS256 signing key — override in any real deployment |
| `JWT_EXPIRATION_MS` | `86400000` (24h) | token lifetime |
| `SERVER_PORT` / `--server.port` | `8080` | HTTP port |

CORS allows `http://localhost:4200` (the Angular dev server) — see `CorsConfig`.

`postman_collection.json` imports the five requests with tests and token capture.
