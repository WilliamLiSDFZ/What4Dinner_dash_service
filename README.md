# What4Dinner Dash Service

Backend service for **What4Dinner**, a recipe / dinner-planning app. Provides a JWT-secured REST API over a PostgreSQL store, with recipes, ingredients, tags, favorites, likes, and vector embeddings for semantic search.

> Early-stage: the domain schema is complete and the first authenticated endpoint (`GET /v1/recipe`) is live. Most feature controllers are still stubs.

## Tech stack

- **Java 21**, **Spring Boot 4.0.x**, Maven (wrapper included)
- **Spring Data JDBC** over **PostgreSQL** (with the `pgvector` extension)
- **Spring Security** as an OAuth2 **resource server** validating RS256 JWTs
- **Redis** (wired, not yet used)
- **Lombok**

## Prerequisites

- JDK 21
- A reachable PostgreSQL database with the **pgvector** extension available
- Redis (for features that will use it)
- An RSA keypair for JWT verification (see [JWT keys](#jwt-keys))

## Setup

### Configuration

Runtime config lives in `src/main/resources/application.yaml`; `application-example.yaml` is the committed template. The datasource reads environment variables (with in-file defaults):

| Variable | Purpose | Default |
|----------|---------|---------|
| `SPRING_DATASOURCE_URL` | JDBC URL | `jdbc:postgresql://…:5432/w4d` |
| `SPRING_DATASOURCE_USERNAME` | DB user | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | DB password | — |

Prefer overriding these via environment rather than editing the file.

The schema in `src/main/resources/database.sql` runs on every startup (`spring.sql.init.mode: always`) and is idempotent (`CREATE … IF NOT EXISTS`). It also runs `CREATE EXTENSION IF NOT EXISTS vector`, which requires pgvector to be installed on the server.

### JWT keys

The app verifies (and can mint) JWTs using an RSA keypair loaded from the classpath:

```yaml
jwt:
  private-key: classpath:keys/private.pem
  public-key: classpath:keys/public.pem
  expiration-minutes: 60
```

Place `private.pem` (PKCS#8) and `public.pem` (X.509) under `src/main/resources/keys/`. **The app will not start without them.** To generate a dev keypair:

```bash
mkdir -p src/main/resources/keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out src/main/resources/keys/private.pem
openssl rsa -pubout -in src/main/resources/keys/private.pem -out src/main/resources/keys/public.pem
```

Do not commit real private keys.

## Build & run

```bash
./mvnw clean package        # build
./mvnw spring-boot:run      # run locally (port 8082)
```

The API is served under `http://localhost:8082/api` (port `8082`, context path `/api`).

## Tests

```bash
./mvnw test                                                   # all tests
./mvnw test -Dtest=What4DinnerDashServiceApplicationTests     # single class
./mvnw test -Dtest=What4DinnerDashServiceApplicationTests#contextLoads  # single method
```

Note: the `@SpringBootTest` context test loads the full application context, so it requires the JWT keys and a reachable PostgreSQL. Without them it fails for environmental reasons, not code defects.

## API

See [`API.md`](./API.md). The first endpoint:

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8082/api/v1/recipe
```

## Project structure

```
src/main/java/today/what4dinner/what4dinner_dash_service/
├── config/       JwtConfig (JwtEncoder/Decoder), SecurityConfig, SpringConfig
├── controller/   REST controllers (versioned under /v1)
├── service/      Service interfaces + Impl (RecipeService, JWTService)
├── repository/   Spring Data JDBC repositories + Redis repository
├── model/        Persistence aggregates (e.g. Recipe)
└── dto/          API DTOs (e.g. RecipeSummary)
src/main/resources/
├── application.yaml          active config
├── application-example.yaml  config template
└── database.sql              schema (runs on startup)
```

## Notes for contributors

- Persistence is **Spring Data JDBC**, not JPA — use aggregate mapping and explicit SQL via `@Query`, not entity lazy-loading.
- Controllers return `ResponseEntity<T>`.
- User-owned resources are scoped by the JWT `sub` claim (the user's UUID), never by a user id from the request.
- See [`CLAUDE.md`](./CLAUDE.md) for deeper architecture and known issues.
