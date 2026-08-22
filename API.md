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

### `GET /v1/ingredient` — list my family's ingredients

_Authenticated._ Returns the ingredients owned by the caller's **family**, newest first. The family is resolved from the JWT `sub` claim (`users.family_id`) — never from the request.

**Response** `200 OK`

```json
[
  {
    "id": "cbe6d09f-20f1-4990-b6ad-4c7443442070",
    "canonicalName": "西红柿",
    "categoryId": null,
    "referencePrice": 0.0,
    "lastPurchase": null
  }
]
```

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID | Ingredient id |
| `canonicalName` | string | Display name |
| `categoryId` | UUID \| null | `categories.id` |
| `referencePrice` | number \| null | Defaults to `0` |
| `lastPurchase` | timestamp \| null | |

Empty array `[]` if the family has none.

---

### `POST /v1/ingredient` — add an ingredient

_Authenticated._ Creates an ingredient in the caller's family.

**Request**

```
POST /api/v1/ingredient
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "name": "西红柿",
  "categoryId": null,
  "referencePrice": 3.5,
  "lastPurchase": "2026-08-20"
}
```

| Field | Type | Notes |
|-------|------|-------|
| `name` | string | **Required.** Trimmed before storing |
| `categoryId` | UUID \| null | Optional; must exist in `categories` |
| `referencePrice` | number \| null | Optional; omitted or `null` stores `0`. Must not be negative |
| `lastPurchase` | string \| null | Optional purchase **date**, `yyyy-MM-dd`. Stored at `00:00:00` |

**Response** `201 Created` — the created ingredient, same shape as the `GET` items.

> **Date in, timestamp out.** `lastPurchase` is *sent* as a plain date (`"2026-08-20"`) but
> *returned* as a timestamp (`"2026-08-20T00:00:00"`), because the underlying column is a
> `timestamp`. The time component is always midnight.

**Errors**

| Status | When |
|--------|------|
| `400 Bad Request` | Missing body, blank `name`, unknown `categoryId`, negative `referencePrice`, or a `lastPurchase` that is not a `yyyy-MM-dd` date |
| `401 Unauthorized` | No / invalid token |
| `409 Conflict` | The family already has an ingredient with that name (case-insensitive) |

> The database has no unique constraint on the name, so the duplicate check is enforced by the application only.

---

### `DELETE /v1/ingredient/{ingredientId}` — remove an ingredient

_Authenticated._ Deletes an ingredient from the caller's family.

**Response** `204 No Content`

**Errors**

| Status | When |
|--------|------|
| `400 Bad Request` | `ingredientId` is not a UUID |
| `401 Unauthorized` | No / invalid token |
| `404 Not Found` | No such ingredient **in the caller's family** (this is also what blocks cross-family deletes) |
| `409 Conflict` | A recipe or step still references it — the message includes the count |

`recipe_ingredients.ingredient_id` and `step_ingredients.ingredient_id` have no `ON DELETE` clause, so Postgres would reject the delete; the `409` reports that up front instead of surfacing a `500`.

---

### `POST /v1/image/upload-url` — get a signed upload URL

_Authenticated._ Returns a short-lived **V4 signed PUT URL** plus the object key to store. The backend never receives the image bytes: the client uploads straight to GCS, then reports `objectName` back when creating the recipe / ingredient it belongs to.

**The object key is always generated server-side.** The client supplies only a `purpose` and a `contentType`, which are used purely as lookup keys against fixed allowlists — neither is ever interpolated into the path. Keys are laid out as `family/{familyId}/{purpose}/{uuid}.{ext}`.

**Request**

```
POST /api/v1/image/upload-url
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "purpose": "recipe",
  "contentType": "image/jpeg"
}
```

| Field | Allowed values |
|-------|----------------|
| `purpose` | `recipe`, `recipe-raw`, `family-background`, `ingredient` |
| `contentType` | `image/jpeg`, `image/png`, `image/webp`, `image/heic` |

**Response** `200 OK`

```json
{
  "objectName": "family/596162e9-.../recipe/f4cd67ea-....jpg",
  "uploadUrl": "https://storage.googleapis.com/...&X-Goog-Signature=...",
  "method": "PUT",
  "requiredHeaders": { "Content-Type": "image/jpeg" },
  "expiresAt": "2026-08-20T10:03:36Z"
}
```

**Uploading — the part that usually breaks.** The signature covers `content-type;host`, so the upload must use `PUT` with **exactly** the `Content-Type` that was requested. A different method, a missing `Content-Type`, or a different value fails with `SignatureDoesNotMatch`. Do not add other headers.

```bash
curl -X PUT \
  -H 'Content-Type: image/jpeg' \
  --data-binary @photo.jpg \
  "<uploadUrl>"
```

Then send `objectName` to whichever endpoint stores it (e.g. `recipe_images.storage_key`, `family.background_image_key`). The URL expires after `gcs.signed-url-minutes` (default 15).

**Errors**

| Status | When |
|--------|------|
| `400 Bad Request` | Missing body, unknown `purpose`, or disallowed `contentType` |
| `401 Unauthorized` | No / invalid token |
| `503 Service Unavailable` | GCS is not configured (`gcs.bucket` unset or credentials missing) |

### `GET /v1/user/me` — my profile

_Authenticated._ Returns the authenticated user's own profile, resolved from the JWT `sub` claim.

**Response** `200 OK`

```json
{
  "id": "019e8ec8-f581-758d-98c0-1bb53c05db2f",
  "familyId": "596162e9-7a53-42fe-bb77-0a15e5618b66",
  "email": "liyuze2004@gmail.com",
  "username": "liyuze2004",
  "activated": false,
  "seenTourVersion": 0,
  "createdAt": "2026-06-03T18:39:55.288133",
  "updatedAt": "2026-08-20T08:34:24.713312"
}
```

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID | User id (the JWT `sub`) |
| `familyId` | UUID | Family this user belongs to |
| `email` | string | |
| `username` | string | |
| `activated` | boolean | |
| `seenTourVersion` | int | |
| `createdAt` / `updatedAt` | timestamp | |

The password hash is never selected by the query and never appears in the response.

**Errors**

| Status | When |
|--------|------|
| `401 Unauthorized` | No / invalid token, or the user row no longer exists |

---

### `GET /v1/family` — my family

_Authenticated._ Returns the family the authenticated user belongs to, with its members. The family is resolved from the JWT `sub` claim (`users.family_id`) — never from the request.

**Response** `200 OK`

```json
{
  "id": "596162e9-7a53-42fe-bb77-0a15e5618b66",
  "familyName": "default family name(please change)",
  "backgroundImageUrl": null,
  "timezone": "America/Los_Angeles",
  "currencyUnit": "USD",
  "createdAt": "2026-08-20T08:40:13.160583",
  "members": [
    {
      "id": "019e8ec8-f581-758d-98c0-1bb53c05db2f",
      "username": "liyuze2004",
      "email": "liyuze2004@gmail.com"
    }
  ]
}
```

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID | Family id |
| `familyName` | string \| null | |
| `backgroundImageUrl` | string \| null | Short-lived **signed GET URL** — see below |
| `timezone` | string | IANA zone id. Change it via `PATCH /v1/setting` |
| `currencyUnit` | string | ISO 4217 code. Change it via `PATCH /v1/setting` |
| `createdAt` | timestamp | |
| `members` | array | Every user in the family, oldest first (`id`, `username`, `email`) |

**About `backgroundImageUrl`.** The bucket has `publicAccessPrevention: ENFORCED`, so objects can never be public and a bare object key would be useless to a client. The server therefore returns a signed GET URL that can be used directly as an `<img src>`. It expires after `gcs.signed-url-minutes` (default 15), so **fetch it fresh rather than caching it**. The raw object key is intentionally not exposed.

It is `null` when the family has no background image, and also `null` — rather than an error — if GCS is unconfigured, so the rest of the family data stays usable.

**Errors**

| Status | When |
|--------|------|
| `401 Unauthorized` | No / invalid token, or the user row no longer exists |
| `404 Not Found` | The family row no longer exists |

### `GET /v1/setting` — read settings

_Authenticated._ Returns the settings visible to the caller, **grouped by scope**.

```json
{
  "family": {
    "timezone": "America/Los_Angeles",
    "currencyUnit": "USD"
  }
}
```

Settings are deliberately grouped rather than flat, so future groups (user, notification, …) can be added as sibling keys without breaking this contract. **Read `settings.family.timezone`, not a top-level `timezone`.**

| Field | Type | Notes |
|-------|------|-------|
| `family.timezone` | string | IANA zone id, e.g. `America/Los_Angeles` |
| `family.currencyUnit` | string | ISO 4217 code, e.g. `USD` |

> The `family` group is stored on the family row, so **it is shared by every member** — one member changing it changes it for the whole household. It is not a per-user preference.

**Errors**

| Status | When |
|--------|------|
| `401 Unauthorized` | No / invalid token, or the user row no longer exists |
| `404 Not Found` | The family row no longer exists |

---

### `PATCH /v1/setting` — update settings

_Authenticated._ Partial update. The request is nested exactly like the response.

**Request**

```
PATCH /api/v1/setting
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "family": { "timezone": "Asia/Shanghai" }
}
```

**Response** `200 OK` — the full settings document after the change:

```json
{
  "family": {
    "timezone": "Asia/Shanghai",
    "currencyUnit": "USD"
  }
}
```

**Partial at both levels.** Omit the `family` group, or omit a field inside it, and those values are left unchanged. `{}` and `{"family":{}}` are both valid no-ops that simply return the current settings.

| Field | Type | Notes |
|-------|------|-------|
| `family.timezone` | string \| null | Must be a valid IANA zone id. **Case-sensitive** — `asia/shanghai` is rejected |
| `family.currencyUnit` | string \| null | Must be a valid ISO 4217 code. Trimmed and upper-cased, so `"cny"` is accepted as `CNY` |

**Errors**

| Status | When |
|--------|------|
| `400 Bad Request` | Invalid timezone or currency code. Validated before any database access |
| `401 Unauthorized` | No / invalid token, or the user row no longer exists |
| `404 Not Found` | The family row no longer exists |

**Example**

```bash
curl -X PATCH \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"family":{"timezone":"Asia/Shanghai","currencyUnit":"CNY"}}' \
  http://localhost:8082/api/v1/setting
```

## Planned endpoints

The following controllers exist as stubs and have no endpoints implemented yet:

| Base path | Area |
|-----------|------|
| `/v1/like` | Recipe likes |
| `/v1/shopping-list` | Shopping lists |

## Conventions

- Controllers return `ResponseEntity<T>`.
- Request/response bodies are JSON.
- User-owned resources are scoped by the JWT `sub` claim — never by a user id supplied in the request.
