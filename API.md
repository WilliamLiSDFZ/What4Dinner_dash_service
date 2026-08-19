# What4Dinner Dash Service — API

HTTP API reference for the What4Dinner backend.

## Base URL

| Environment | Base URL |
|-------------|----------|
| Local       | `http://localhost:8082/api` |

The server runs on port **8082** with a context path of **`/api`** (`server.port` / `server.servlet.context-path`). Every path below is relative to the base URL — e.g. `GET /v1/recipe` is `http://localhost:8082/api/v1/recipe`.

Endpoints are versioned under `/v1`.

## Authentication

The service is a stateless OAuth2 **resource server**. All endpoints require a valid RS256 JWT except where noted as _Public_.

Send the token as a Bearer header:

```
Authorization: Bearer <jwt>
```

Token expectations:

- Signed with the RSA private key; verified against the configured public key (`jwt.public-key`).
- `sub` — the user's UUID (used to scope user-owned resources).
- `email` — the user's email.
- `iss` — `what4dinner-auth`.
- Expiry per `jwt.expiration-minutes` (default 60).

Tokens are minted by the auth service; this service validates them. `JWTService` (`generateToken` / `generateShortTermToken` / `exchangeToken`) can also issue tokens when needed.

Responses:

- `401 Unauthorized` — missing, malformed, expired, or invalid-signature token.
- `403 Forbidden` — authenticated but not permitted.

## Endpoints

### `GET /v1/recipe` — list my recipes

_Authenticated._ Returns summaries of all recipes owned by the authenticated user (resolved from the JWT `sub` claim).

**Request**

```
GET /api/v1/recipe
Authorization: Bearer <jwt>
```

**Response** `200 OK`

```json
[
  {
    "id": "b6a1f2c0-0d3e-4f1a-9c2b-1a2b3c4d5e6f",
    "title": "西红柿炒鸡蛋",
    "description": "家常快手菜",
    "status": "done"
  }
]
```

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID | Recipe id |
| `title` | string | Recipe name |
| `description` | string \| null | Short description / notes |
| `status` | string | `pending` or `done` |

Returns an empty array `[]` if the user has no recipes.

**Errors**

| Status | When |
|--------|------|
| `401 Unauthorized` | No / invalid token |

**Example**

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8082/api/v1/recipe
```

### `GET /v1/favorite` — list my favorites

_Authenticated._ Returns summaries of the recipes the authenticated user has favorited (user resolved from the JWT `sub` claim). Ordered newest favorite first (`favorites.created_at DESC`).

The response uses the same recipe-summary shape as `GET /v1/recipe`.

**Request**

```
GET /api/v1/favorite
Authorization: Bearer <jwt>
```

**Response** `200 OK`

```json
[
  {
    "id": "b6a1f2c0-0d3e-4f1a-9c2b-1a2b3c4d5e6f",
    "title": "西红柿炒鸡蛋",
    "description": "家常快手菜",
    "status": "done"
  }
]
```

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID | Recipe id |
| `title` | string | Recipe name |
| `description` | string \| null | Short description / notes |
| `status` | string | `pending` or `done` |

Returns an empty array `[]` if the user has no favorites. A favorited recipe owned by another user is still returned — favorites are not scoped to the recipe's owner.

**Errors**

| Status | When |
|--------|------|
| `401 Unauthorized` | No / invalid token |

**Example**

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8082/api/v1/favorite
```

### `PATCH /v1/favorite/{recipeId}` — set favorite status

_Authenticated._ Favorites or unfavorites a recipe for the authenticated user (resolved from the JWT `sub` claim).

Sets an explicit desired state rather than toggling, so the call is **idempotent** — repeating it, or a double-tapped button, lands on the same result. Any existing recipe may be favorited regardless of who owns it.

**Request**

```
PATCH /api/v1/favorite/b6a1f2c0-0d3e-4f1a-9c2b-1a2b3c4d5e6f
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "favorited": true
}
```

| Parameter | In | Type | Notes |
|-----------|----|------|-------|
| `recipeId` | path | UUID | Recipe to favorite / unfavorite |
| `favorited` | body | boolean | **Required.** `true` favorites, `false` unfavorites |

**Response** `200 OK`

```json
{
  "recipeId": "b6a1f2c0-0d3e-4f1a-9c2b-1a2b3c4d5e6f",
  "favorited": true
}
```

| Field | Type | Notes |
|-------|------|-------|
| `recipeId` | UUID | The recipe that was updated |
| `favorited` | boolean | Resulting state |

**Errors**

| Status | When |
|--------|------|
| `400 Bad Request` | Body missing, `favorited` absent or null, or `recipeId` is not a UUID |
| `401 Unauthorized` | No / invalid token |
| `404 Not Found` | No recipe with that id |

**Example**

```bash
curl -X PATCH \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"favorited":true}' \
  http://localhost:8082/api/v1/favorite/b6a1f2c0-0d3e-4f1a-9c2b-1a2b3c4d5e6f
```

## Planned endpoints

The following controllers exist as stubs and have no endpoints implemented yet:

| Base path | Area |
|-----------|------|
| `/v1/like` | Recipe likes |
| `/v1/setting` | User settings |
| `/v1/shopping-list` | Shopping lists |

## Conventions

- Controllers return `ResponseEntity<T>`.
- Request/response bodies are JSON.
- User-owned resources are scoped by the JWT `sub` claim — never by a user id supplied in the request.
