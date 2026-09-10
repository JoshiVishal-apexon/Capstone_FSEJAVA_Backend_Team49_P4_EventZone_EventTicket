# EventZone — Backend API

Spring Boot 3 / Java 17 backend exposing the EventZone event catalogue and authentication endpoints.

## Endpoints

### Public (no token required)

| Method | Path                 | Description                                                                 |
|--------|----------------------|-----------------------------------------------------------------------------|
| GET    | `/api/categories`    | List all event categories.                                                  |
| GET    | `/api/events`        | List active events. Optional `?category=<name>` filter (case-insensitive).  |
| GET    | `/api/events/{id}`   | Get one event with its description, organiser and ticket categories.        |

### Authentication

| Method | Path                  | Description                                                          |
|--------|-----------------------|----------------------------------------------------------------------|
| POST   | `/api/auth/register`  | Register a new account. Always created with the `ATTENDEE` role.      |
| POST   | `/api/auth/login`     | Log in as admin, organiser or attendee. Returns a JWT.               |
| POST   | `/api/auth/logout`    | Returns `204`. JWT is stateless, so the client just discards it.     |

`/api/auth/login` is the same endpoint for all three roles — the role in the response comes from
the account being logged in with.

## Running

```powershell
.\scripts\run.ps1        # or: mvn spring-boot:run
```

The API listens on <http://localhost:8080>. It uses a file-based H2 database at `./data/eventzonedb.mv.db`
under the default `dev` profile, so no external services are needed.

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- H2 console: <http://localhost:8080/h2-console> (JDBC URL `jdbc:h2:file:./data/eventzonedb`, user `sa`, empty password)

## Seed data

`DataSeeder` populates the database on first startup only (it skips seeding if the users table is
non-empty), creating 4 categories, 6 active events with General/VIP ticket categories, and these
accounts — all with the password `Password@123`:

| Role      | Email                  |
|-----------|------------------------|
| ADMIN     | admin@eventzone.com    |
| ORGANISER | skyline@eventzone.com  |
| ORGANISER | nova@eventzone.com     |
| ATTENDEE  | attendee1@eventzone.com |

To reseed from scratch, delete the `data/` directory and restart.

## Tests

```powershell
.\scripts\test.ps1       # or: mvn test
```

Unit tests (JUnit 5 + Mockito) cover `AuthService`, `BookingService`, `CategoryService`, `EventService`, `OrganiserService`, `TicketCategoryService`, plus controller and monitoring smoke checks such as `ControllerCoverageTest` and `MonitoringControllerTest`.

## Error responses

All handled errors return a consistent payload:

```json
{
  "timestamp": "2026-09-07T11:41:57Z",
  "path": "/api/auth/login",
  "error": "UNAUTHORIZED",
  "message": "Invalid email or password"
}
```

## Postman

Import `postman_collection.json`. Requests are grouped into **Public** and **Auth**; the login
requests save their JWT into collection variables and "List active events" saves the first event's
id into `{{eventId}}` for "Get event detail".
