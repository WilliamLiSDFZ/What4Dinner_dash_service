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

## API index

Every endpoint below links to its full section. All require a Bearer token except `/v1/health`.

### Recipe

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | [`/v1/recipe`](#get-v1recipe--list-my-familys-recipes) | Every recipe in my family, with favorite / like state |
| `POST` | [`/v1/recipe`](#post-v1recipe--create-a-recipe) | Create a recipe with steps, ingredients and images |
| `GET` | [`/v1/recipe/{recipeId}`](#get-v1reciperecipeid--recipe-detail) | Full recipe: images, steps, ingredients |
| `POST` | [`/v1/recipe/{recipeId}/image`](#post-v1reciperecipeidimage--attach-photos-to-a-recipe) | Attach user photos to a recipe |
| `DELETE` | [`/v1/recipe/{recipeId}`](#delete-v1reciperecipeid--delete-a-recipe) | Delete a recipe and everything under it |

### Favorite

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | [`/v1/favorite`](#get-v1favorite--list-my-favorites) | Recipes I have favorited |
| `PATCH` | [`/v1/favorite/{recipeId}`](#patch-v1favoriterecipeid--set-favorite-status) | Favorite / unfavorite a recipe |

### Like

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | [`/v1/like/{recipeId}`](#get-v1likerecipeid--read-like-status) | Like count + whether I liked it |
| `PATCH` | [`/v1/like/{recipeId}`](#patch-v1likerecipeid--like-or-unlike-a-recipe) | Like / unlike a recipe |

### Ingredient

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | [`/v1/ingredient`](#get-v1ingredient--list-my-familys-ingredients) | My family's ingredients |
| `POST` | [`/v1/ingredient`](#post-v1ingredient--add-an-ingredient) | Add an ingredient |
| `DELETE` | [`/v1/ingredient/{ingredientId}`](#delete-v1ingredientingredientid--remove-an-ingredient) | Remove an ingredient |

### Image

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | [`/v1/image/upload-url`](#post-v1imageupload-url--get-a-signed-upload-url) | Signed URL to upload an image to GCS |

### User

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | [`/v1/user/me`](#get-v1userme--my-profile) | My profile |

### Family

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | [`/v1/family`](#get-v1family--my-family) | My family, members, background image |

### Setting

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | [`/v1/setting`](#get-v1setting--read-settings) | Read settings, grouped by scope |
| `PATCH` | [`/v1/setting`](#patch-v1setting--update-settings) | Update settings (partial) |

### Health

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | [`/v1/health`](#get-v1health--liveness-probe) | Liveness probe (public) |
## Endpoints

### `GET /v1/recipe` — list my family's recipes

_Authenticated._ Returns summaries of every recipe in the caller's **family** (resolved from the JWT `sub` claim → `users.family_id`), including ones uploaded by relatives.

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
    "status": "done",
    "favorited": true,
    "liked": false,
    "likeCount": 12
  }
]
```

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID | Recipe id |
| `title` | string | Recipe name |
| `description` | string \| null | Short description / notes |
| `status` | string | `pending` or `done` |
| `favorited` | boolean | Whether **you** have favorited it |
| `liked` | boolean | Whether **you** have liked it |
| `likeCount` | int | Total likes across **all** users |

`favorited` and `liked` are **your own** state; `likeCount` is the global total. A row can therefore read `liked: false` with a non-zero `likeCount` — the same semantics as `GET /v1/like/{recipeId}`, which stays available for refreshing a single recipe.

Returns an empty array `[]` if the family has no recipes.

**Errors**

| Status | When |
|--------|------|
| `401 Unauthorized` | No / invalid token |

**Example**

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8082/api/v1/recipe
```

### `POST /v1/recipe` — create a recipe

_Authenticated._ Creates a recipe together with its ordered steps and each step's ingredients, **in a single transaction** — if any part is rejected, nothing is written.

The owning `user_id` comes from the JWT `sub` claim and `family_id` from that user's family; neither is accepted from the request.

**Request**

```
POST /api/v1/recipe
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "title": "西红柿炒鸡蛋",
  "description": "家常快手菜",
  "prepTimeMinutes": 5,
  "cookTimeMinutes": 10,
  "isPublic": false,
  "steps": [
    {
      "instruction": "鸡蛋打散炒熟",
      "isOptional": false,
      "imageKeys": ["family/596162e9-…/step/ab12….jpg"],
      "ingredients": [
        { "ingredientId": "0822ac43-…", "amount": 2, "unit": "个", "prepNote": "打散" }
      ]
    },
    {
      "instruction": "加西红柿翻炒",
      "ingredients": [
        { "ingredientId": "dedaabbb-…", "amountText": "两个", "isOptional": true }
      ]
    }
  ]
}
```

| Field | Type | Notes |
|-------|------|-------|
| `title` | string | **Required.** Trimmed before storing |
| `description` | string \| null | Optional |
| `prepTimeMinutes` / `cookTimeMinutes` | int \| null | Optional. Must not be negative |
| `isPublic` | boolean \| null | Optional, defaults `false` |
| `steps` | array \| null | Optional. May be omitted or `[]` for a header-only recipe |
| `steps[].instruction` | string \| null | |
| `steps[].isOptional` | boolean \| null | Defaults `false` |
| `steps[].imageKeys` | array \| null | Optional object keys from `POST /v1/image/upload-url`. Any number per step |
| `steps[].ingredients[].ingredientId` | UUID | **Required** in each entry. Must already exist **in the caller's family** |
| `steps[].ingredients[].amount` | number \| null | Must not be negative |
| `steps[].ingredients[].amountText` | string \| null | Free text, e.g. `"两个"` |
| `steps[].ingredients[].unit` | string \| null | |
| `steps[].ingredients[].isOptional` | boolean \| null | Defaults `false` |
| `steps[].ingredients[].prepNote` | string \| null | |

**Step ordering is positional.** `step_order` is assigned from each step's index in the array (1-based); clients never send it.

**Ingredients must pre-exist.** Create them first with `POST /v1/ingredient`; this endpoint never creates them implicitly. An id that does not exist — or belongs to another family — is a `400`.

**Step images are optional and validated.** Upload each image first via `POST /v1/image/upload-url` with `"purpose": "step"`, then pass the returned `objectName` values in `imageKeys`. A step may carry any number of images; omit the field or send `[]` for none. Ordering is insertion order — the table has no explicit ordering column.

Because `imageKeys` is the one place a client supplies a storage key rather than the server generating it, each key must start with `family/{your familyId}/` and must not contain `..`; anything else is a `400`. This prevents attaching another family's object to your recipe. Keys are checked for ownership and shape, **not** existence — a key whose object was never uploaded is accepted.

**`status` is always `done`.** `pending` is reserved for the AI generation pipeline.

**The flat ingredient list is derived, not sent.** The server writes one `recipe_ingredients` row per *distinct* ingredient used across all steps. That row is marked optional only when **every** occurrence of it was optional — an ingredient that is required in any step counts as required for the recipe.

**Response** `201 Created`

```json
{
  "id": "e952e2e4-4e02-4802-8de1-81bb4fbfa150",
  "title": "西红柿炒鸡蛋",
  "description": "家常快手菜",
  "status": "done",
  "favorited": false,
  "liked": false,
  "likeCount": 0
}
```

Same shape as the `GET /v1/recipe` items — a freshly created recipe is naturally unfavorited, unliked, and at zero likes. There is no recipe-detail endpoint yet, so the nested structure is not echoed back — use the returned `id`.

**Errors**

| Status | When |
|--------|------|
| `400 Bad Request` | Missing body, blank `title`, negative time or `amount`, missing `ingredientId`, an `ingredientId` not in the caller's family, or an `imageKey` that is blank, over 1024 chars, or not under `family/{your familyId}/` |
| `401 Unauthorized` | No / invalid token |

**Example**

```bash
curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"番茄炒蛋","steps":[{"instruction":"炒","ingredients":[{"ingredientId":"0822ac43-…","amount":2}]}]}' \
  http://localhost:8082/api/v1/recipe
```

---

### `GET /v1/recipe/{recipeId}` — recipe detail

_Authenticated._ Returns one recipe in full: header, the caller's favorite/like state, and the ordered steps with their ingredients and images. **Family-scoped**, like `DELETE`.

**Response** `200 OK`

```json
{
  "id": "bff83bb2-…",
  "title": "西红柿炒鸡蛋",
  "description": "家常快手菜",
  "prepTimeMinutes": 5,
  "cookTimeMinutes": 10,
  "status": "done",
  "isPublic": false,
  "createdAt": "2026-08-25T22:20:24",
  "updatedAt": "2026-08-25T22:20:24",
  "favorited": true,
  "liked": true,
  "likeCount": 1,
  "images": [
    { "id": "…", "url": "https://storage.googleapis.com/…&X-Goog-Signature=…",
      "isPrimary": true, "displayOrder": 0 }
  ],
  "steps": [
    {
      "id": "a1b2…",
      "stepOrder": 1,
      "instruction": "鸡蛋打散炒熟",
      "isOptional": false,
      "ingredients": [
        { "ingredientId": "0822ac43-…", "name": "鸡蛋", "amount": 2,
          "amountText": null, "unit": "个", "isOptional": false, "prepNote": "打散" }
      ],
      "images": ["https://storage.googleapis.com/…&X-Goog-Signature=…"]
    },
    {
      "id": "c3d4…",
      "stepOrder": 2,
      "instruction": "加西红柿翻炒",
      "isOptional": true,
      "ingredients": [
        { "ingredientId": "dedaabbb-…", "name": "西红柿", "amount": null,
          "amountText": "两个", "unit": null, "isOptional": true, "prepNote": null }
      ],
      "images": []
    }
  ]
}
```

| Field | Type | Notes |
|-------|------|-------|
| `favorited` / `liked` | boolean | **Your own** state |
| `likeCount` | int | Total across **all** users |
| `images` | array | **Recipe-level** photos, ordered by `displayOrder`. Distinct from `steps[].images` |
| `steps` | array | Ordered by `stepOrder` |
| `steps[].id` | UUID | Step id |
| `steps[].ingredients[].name` | string | Joined from the ingredient, so no second call is needed |
| `steps[].images` | array | **Signed GET URLs**, not object keys — empty when the step has none |

**Images are short-lived signed URLs.** The bucket enforces public-access prevention, so a raw object key cannot be rendered by a browser. They expire per `gcs.signed-url-minutes` (default 15) — fetch the detail fresh rather than caching the URLs. If storage is unconfigured, `images` comes back empty rather than failing the whole response.

**Errors**

| Status | When |
|--------|------|
| `400 Bad Request` | `recipeId` is not a UUID |
| `401 Unauthorized` | No / invalid token |
| `404 Not Found` | No such recipe **in the caller's family** |

**Example**

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8082/api/v1/recipe/bff83bb2-4574-4a1d-89d2-5b8f1c86a155
```

---

### `POST /v1/recipe/{recipeId}/image` — attach photos to a recipe

_Authenticated._ Attaches user-uploaded photos to the recipe itself. Family-scoped.

Upload each file first via `POST /v1/image/upload-url` with `"purpose": "recipe"`, then send the returned `objectName` values here.

> **This is not the AI-input path.** Rows are written to `recipe_images` with `source = 'user'`. `recipe_raw_images` — reserved for the original recipe screenshots the AI pipeline analyses — is never touched by this endpoint.

**Request**

```
POST /api/v1/recipe/e952e2e4-…/image
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "imageKeys": [
    "family/596162e9-…/recipe/ab12….jpg",
    "family/596162e9-…/recipe/cd34….jpg"
  ],
  "primaryIndex": 0
}
```

| Field | Type | Notes |
|-------|------|-------|
| `imageKeys` | array | **Required**, non-empty. Object keys under `family/{your familyId}/`, max 512 chars each |
| `primaryIndex` | int \| null | Optional 0-based index selecting the cover. Omit it and no image becomes the cover; any existing cover is left alone |

**Setting a cover replaces the old one.** A recipe may have at most one cover (`uk_recipe_image_primary`), so the previous cover is demoted to a normal image in the same transaction — it is **not** deleted. `displayOrder` continues from the recipe's current maximum, so repeated calls append.

Stored rows always get `source = 'user'`, `status = 'done'`, and `uploaded_by` = the caller. The AI-only columns stay null.

**Response** `201 Created` — every image on the recipe afterwards, ordered by `displayOrder`:

```json
[
  { "id": "…", "url": "https://storage.googleapis.com/…&X-Goog-Signature=…",
    "isPrimary": true, "displayOrder": 0 },
  { "id": "…", "url": "https://…", "isPrimary": false, "displayOrder": 1 }
]
```

`url` is a short-lived signed GET URL, not the object key — the bucket is non-public, and the key is never exposed.

**Errors**

| Status | When |
|--------|------|
| `400 Bad Request` | Missing body, empty `imageKeys`, a key that is blank / over 512 chars / not under `family/{your familyId}/` or containing `..`, or a `primaryIndex` out of range |
| `401 Unauthorized` | No / invalid token |
| `404 Not Found` | No such recipe **in the caller's family** |

**Example**

```bash
curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"imageKeys":["family/596162e9-…/recipe/ab12….jpg"],"primaryIndex":0}' \
  http://localhost:8082/api/v1/recipe/e952e2e4-…/image
```

---

### `DELETE /v1/recipe/{recipeId}` — delete a recipe

_Authenticated._ Deletes a recipe belonging to the caller's family.

**Family-scoped, not uploader-scoped:** any member may delete any of the family's recipes, including one a relative uploaded. Note the asymmetry with `GET /v1/recipe`, which lists only recipes *you* uploaded — so a member can delete a recipe that does not appear in their own list.

Everything belonging to the recipe goes with it via `ON DELETE CASCADE`: steps, step ingredients, step images, recipe ingredients, tags, favorites, likes, images, raw images, and shopping-list entries.

**Response** `204 No Content`

**Errors**

| Status | When |
|--------|------|
| `400 Bad Request` | `recipeId` is not a UUID |
| `401 Unauthorized` | No / invalid token |
| `404 Not Found` | No such recipe **in the caller's family** — this is also what refuses a cross-family delete, without revealing whether that recipe exists |

> The GCS objects behind any image keys are **not** deleted; only the database rows are.

**Example**

```bash
curl -X DELETE -H "Authorization: Bearer $TOKEN" \
  http://localhost:8082/api/v1/recipe/e952e2e4-4e02-4802-8de1-81bb4fbfa150
```

---

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
    "status": "done",
    "favorited": true,
    "liked": false,
    "likeCount": 12
  }
]
```

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID | Recipe id |
| `title` | string | Recipe name |
| `description` | string \| null | Short description / notes |
| `status` | string | `pending` or `done` |
| `favorited` | boolean | Whether **you** have favorited it |
| `liked` | boolean | Whether **you** have liked it |
| `likeCount` | int | Total likes across **all** users |

`favorited` is always `true` here by definition. Returns an empty array `[]` if the user has no favorites. A favorited recipe owned by another user is still returned — favorites are not scoped to the recipe's owner.

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

### `GET /v1/like/{recipeId}` — read like status

_Authenticated._ Returns how many likes a recipe has in total, and whether the calling user (JWT `sub`) is one of them.

**Response** `200 OK`

```json
{
  "recipeId": "b6a1f2c0-0d3e-4f1a-9c2b-1a2b3c4d5e6f",
  "liked": false,
  "likeCount": 12
}
```

| Field | Type | Notes |
|-------|------|-------|
| `recipeId` | UUID | The recipe |
| `liked` | boolean | **This user's** own state |
| `likeCount` | int | Total across **all** users |

Note the two fields answer different questions: `liked` is personal, `likeCount` is global. A recipe can have `liked: false` with a non-zero `likeCount`.

**Errors**

| Status | When |
|--------|------|
| `400 Bad Request` | `recipeId` is not a UUID |
| `401 Unauthorized` | No / invalid token |
| `404 Not Found` | No recipe with that id |

---

### `PATCH /v1/like/{recipeId}` — like or unlike a recipe

_Authenticated._ Sets whether the calling user likes the recipe.

Sets an explicit desired state rather than toggling, so it is **idempotent** — repeating it, or a double-tapped button, lands on the same result and can never double-count (the table's composite primary key plus `ON CONFLICT DO NOTHING` guarantee it). Any existing recipe may be liked regardless of owner, including your own.

**Request**

```
PATCH /api/v1/like/b6a1f2c0-0d3e-4f1a-9c2b-1a2b3c4d5e6f
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "liked": true
}
```

| Parameter | In | Type | Notes |
|-----------|----|------|-------|
| `recipeId` | path | UUID | Recipe to like / unlike |
| `liked` | body | boolean | **Required.** `true` likes, `false` unlikes |

**Response** `200 OK` — same shape as the `GET`, with the count already refreshed, so no follow-up request is needed:

```json
{
  "recipeId": "b6a1f2c0-0d3e-4f1a-9c2b-1a2b3c4d5e6f",
  "liked": true,
  "likeCount": 13
}
```

**Errors**

| Status | When |
|--------|------|
| `400 Bad Request` | Body missing, `liked` absent or null, or `recipeId` is not a UUID |
| `401 Unauthorized` | No / invalid token |
| `404 Not Found` | No recipe with that id |

**Example**

```bash
curl -X PATCH \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"liked":true}' \
  http://localhost:8082/api/v1/like/b6a1f2c0-0d3e-4f1a-9c2b-1a2b3c4d5e6f
```

---

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
| `purpose` | `recipe`, `recipe-raw`, `family-background`, `ingredient`, `step` |
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

### `GET /v1/health` — liveness probe

_**Public** — the only endpoint that needs no token._ Used by the Docker healthcheck.

**Response** `200 OK`

```json
{ "status": "UP" }
```

```bash
curl http://localhost:8082/api/v1/health
```

## Planned endpoints

The following controllers exist as stubs and have no endpoints implemented yet:

| Base path | Area |
|-----------|------|
| `/v1/shopping-list` | Shopping lists |

## Conventions

- Controllers return `ResponseEntity<T>`.
- Request/response bodies are JSON.
- User-owned resources are scoped by the JWT `sub` claim — never by a user id supplied in the request.
