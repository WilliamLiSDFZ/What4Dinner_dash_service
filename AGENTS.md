# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Overview

`What4Dinner_dash_service` is the Spring Boot 4.0.x backend (Java 21, Maven) for **What4Dinner**, a recipe / dinner-planning app. The domain schema is complete and the first authenticated endpoint (`GET /v1/recipe`) is implemented; most feature controllers are still empty stubs. See `README.md` for setup and `API.md` for the HTTP contract.

## Commands

Maven wrapper (`./mvnw`) — no global Maven needed.

- Build: `./mvnw clean package`
- Compile only (fast sanity check): `./mvnw clean compile`
- Run locally: `./mvnw spring-boot:run`  → serves on `http://localhost:8082/api`
- Run all tests: `./mvnw test`
- Single test class: `./mvnw test -Dtest=What4DinnerDashServiceApplicationTests`
- Single test method: `./mvnw test -Dtest=What4DinnerDashServiceApplicationTests#contextLoads`

There is no separate lint step; the build relies on `javac` with `-parameters` (Spring Boot default).

## Running / testing requires external setup

The app **will not start** (and `@SpringBootTest` will fail at context load) without two things present:

1. **RSA keypair** at `src/main/resources/keys/private.pem` + `public.pem` — `JwtConfig` loads these at startup to build the `JwtEncoder`/`JwtDecoder`. Missing keys → bean-creation failure. (README has the `openssl` commands to generate a dev pair.)
2. **A reachable PostgreSQL** with the **pgvector** extension installable — Spring Data JDBC probes the DB at startup for dialect detection (`Failed to obtain JDBC Connection` if unreachable), and `database.sql` runs `CREATE EXTENSION ... vector` on every boot.

When `./mvnw test` fails with a JDBC-connection or key-loading error, it is almost always one of these environmental gaps, not a code defect. Distinguish that from real failures rather than masking it. `./mvnw clean compile` validates code without needing either.

## Architecture

Layered, package-per-concern under `today.what4dinner.what4dinner_dash_service`:

- `controller/` → `service/` (interface + `*Impl`) → `repository/` (Spring Data JDBC) → `model/` aggregates; `dto/` for API shapes; `config/` for cross-cutting beans.

Key cross-file flows:

- **Persistence is Spring Data JDBC, not JPA.** Repositories extend `CrudRepository`; reads use explicit `@Query` SQL projecting straight into Lombok DTO classes (e.g. `RecipeRepository.findSummariesByUserId` → `RecipeSummary`). `model/` aggregates (e.g. `Recipe`) are minimal type tokens — there is no lazy loading or entity graph. Column mapping uses `@Column("snake_case")`; query params use `@Param`.
- **Schema is script-driven, not generated.** `spring.sql.init.mode: always` runs `src/main/resources/database.sql` on every startup. It is idempotent (`CREATE ... IF NOT EXISTS`). The full domain (users, recipes + steps/ingredients/images/tags, favorites, likes, embeddings, activation codes) lives there. The `embeddings.vector VECTOR(1024)` column is for pgvector semantic search.
- **Auth = OAuth2 resource server.** `SecurityConfig` is stateless (no sessions, CSRF disabled), permits `/v1/health/**`, requires auth elsewhere, and validates Bearer JWTs via `oauth2ResourceServer().jwt()` using the `JwtDecoder` from `JwtConfig` (Nimbus / Spring Security OAuth2 JOSE — **not** jjwt). `JWTService`/`JWTServiceImpl` can also mint tokens (`generateToken`, `generateShortTermToken`, `exchangeToken`) for the auth flow.
- **Identity comes from the JWT, never the request.** The `sub` claim is the user's UUID; `email` is a separate claim. Controllers read it via `@AuthenticationPrincipal Jwt jwt` → `UUID.fromString(jwt.getSubject())` and scope user-owned queries by that — see `RecipeController.getMyRecipes`. Do not accept a user id from request params/body.

## Conventions

- **Controllers return `ResponseEntity<T>`** (e.g. `ResponseEntity.ok(...)`).
- **Controllers are versioned**: `@RestController` + class-level `@RequestMapping("/v1/<feature>")`. Note the bean-name pitfall: `@RestController("/v1")` sets the *bean name*, not a path — don't reintroduce it (it caused duplicate-bean collisions previously).
- **Service layer is interface + `Impl`**; the `@Service` impl implements the interface and uses constructor injection.
- Matchers in `SecurityConfig` are relative to the servlet path (after the `/api` context-path), so use `/v1/...`.

## Configuration

- `application.yaml` is active; `application-example.yaml` is the template. Port `8082`, context path `/api`. Datasource reads `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` (PostgreSQL) — override via env, not by editing the file. JWT settings under the `jwt:` key (`private-key`, `public-key`, `expiration-minutes`).
