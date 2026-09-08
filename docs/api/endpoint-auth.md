---
id: endpoint-auth
title: Registration, sign-in, session refresh
type: api_endpoints
status: active
services:
  - server-common
  - server
  - server-native
contract_source:
  - "mani-kotlin-fullstack::shared AuthResource, UserResource"
parent_feature: feature-auth
---

# API: registration, sign-in, session refresh

> **The complete route reference for signing in.** The shapes of the paths and bodies live in the
> `@Resource` and DTO classes of [shared](../services/shared.md), named in `contract_source`; what
> is here is the status codes and the auth tiers, because in the code those are scattered across
> handlers.
>
> The product has no generated schema: the smiley4 annotations do not survive Kotlin/Native, and
> `swagger()` was never wired into the routing. So there is no "in the schema?" column — this
> document *is* the reference.

## Routes — all of them

| Method and path | Tier | Purpose |
|---|---|---|
| `POST /users` | open | registration |
| `POST /auth` | open | sign in with a name and a password; returns a token pair |
| `POST /auth/refresh` | open (it presents a refresh token itself) | exchange a refresh token for a new pair |
| `GET /users/current` | — | **not implemented**, see "Quirks" |

There is one more way into the app — `POST /demo`: it also returns `Tokens`, but it belongs to the
sandbox and is covered in [endpoint-demo](endpoint-demo.md).

The "Bearer access" tier is `authenticate(jwtConfig.name)` around a route; the provider demands a
token carrying the claim `kind = "access"` (`server-common/.../security/ManiAuth.kt:32`).

## Handlers

| Route | Handler |
|---|---|
| `POST /users` | `server-common/.../feature/user/UserRouting.kt:15` |
| `POST /auth` | `server-common/.../feature/auth/AuthRouting.kt:14` |
| `POST /auth/refresh` | `server-common/.../feature/auth/AuthRouting.kt:24` |
| the Bearer header check | `server-common/.../security/ManiAuth.kt` |
| issuing and verifying the token | `server-common/.../security/TokenService.kt` |
| sign-in and refresh | `server-common/.../feature/auth/data/AuthService.kt` |
| the registration rules | `server-common/.../feature/user/Credentials.kt` |

## Request and response bodies

Not copied — the fields change, the path does not:

| What | Class |
|---|---|
| body of `POST /users` and `POST /auth` | `shared/.../feature/auth/LoginParams.kt` |
| body of `POST /auth/refresh` | `shared/.../feature/auth/RefreshParams.kt` |
| response of `POST /auth` and `/auth/refresh` | `shared/.../feature/auth/Tokens.kt` |

## Responses

### `POST /users`

| Condition | Status | Body |
|---|---|---|
| created | `201` | empty |
| name shorter than 3 or longer than 32 characters | `400` | `Name must be 3 to 32 characters long` |
| name contains anything but `a–z`, `0–9`, `-`, `_` | `400` | `Name may contain lowercase letters, digits, hyphen and underscore only` |
| name starts with `demo-` | `400` | `Names starting with "demo-" are reserved for the demo` |
| password shorter than 8 characters | `400` | `Password must be at least 8 characters long` |
| the name is taken | `400` | `User already exist` |
| storage did not write the record | `500` | empty |

The form check runs **before** going to the database: there is no reason to pay for an input refusal
with a query.

### `POST /auth`

| Condition | Status | Body |
|---|---|---|
| the pair matched | `200` | `Tokens` |
| no such user **or** the wrong password | `404` | empty |

The `404` on a wrong password is not a slip: different answers would say which names are taken.

The registration rules are **not** applied at sign-in: names created before those rules do not obey
them, and enforcing the rules here would have evicted existing users.

### `POST /auth/refresh`

| Condition | Status | Body |
|---|---|---|
| the token is accepted | `200` | a new `Tokens` pair |
| signature mismatch, wrong kind, expired token, token absent from the database, claim name not matching the owner | `401` | empty |

A signature is not enough: the presented refresh token also has to **be in the database**, otherwise
a once-revoked token would keep working until it expired. A successful refresh burns the old token —
`tokenRepository.removeToken(...)` runs before the new pair is issued.

### Common to every route

| Condition | Status | Body |
|---|---|---|
| the body did not parse, or a path parameter did not | `400` | `Malformed request` |
| an unhandled exception | `500` | empty (a line goes to the server's stdout) |

Installed by `StatusPages` in `server-common/.../ManiApp.kt:86`. A request cancelled by the client
does not count as a failure and does not turn into a 500.

## Quirks

* **`GET /users/current` is declared but not implemented.** `UserResource.CurrentUserResource` is in
  the contract (`shared/.../feature/user/UserResource.kt:9`), there is no handler in
  `UserRouting.kt`, and nobody calls it. On the native build the path falls through to the
  static-file route and gets `index.html`; on the JVM build it gets whatever `staticResources`
  returns. Verified 2026-09-08.
* **A token with no `kind` claim is accepted as a refresh token.** A compatibility window with a
  removal date — §1.3 of [research-architecture](../research/research-architecture.md).
* **The `401` from the Bearer provider carries the text** `Token is not valid or has expired` — the
  same one the previous `ktor-server-auth-jwt` produced.
