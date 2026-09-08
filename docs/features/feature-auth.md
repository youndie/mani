---
id: feature-auth
title: Sign-in, session and the way in
type: feature
status: active
owner: unassigned
involved_services:
  - shared
  - server-common
  - server
  - server-native
  - composeApp
client_entries:
  - screen-welcome
  - screen-auth-form
api:
  - endpoint-auth
  - endpoint-demo
tags: [auth, session]
---

# Sign-in, session and the way in

## 1. Overview

There are three ways into the app: **create an account**, **sign in to an existing one**, and — in
one click — **open a sandbox**, where the server creates a throwaway user with a ready data set for
you. The third is the main path for the welcome screen: a demonstration product that demands a
registration shows less of itself than it is worth.

From there all three paths are the same. The server issues a token pair, the client puts it in the
platform's store, and after that the app no longer remembers which way the visitor came in. The
access token travels on every request and lives an hour; the refresh token sits in the database,
lives a month, and is **burned on every exchange**.

## 2. Business rules

* Registration accepts a name of 3–32 characters from `a–z`, `0–9`, `-`, `_`; a password of at least
  8 characters.
* Names starting with `demo-` are reserved: that prefix is how the sweep tells a sandbox from a real
  account.
* **Sign-in does not apply the registration rules.** Names created before those rules do not obey
  them, and enforcing them at sign-in would mean the rule evicted existing users.
* A wrong password and a non-existent user produce **the same** `404`: different answers would say
  which names are taken.
* Exchanging a refresh token requires the token to be signed, unexpired, of kind `refresh` **and
  present in the database**. A signature is not enough: otherwise a once-revoked token would work
  until it expired.
* A successful exchange deletes the old token and issues a new pair.
* A refresh token cannot be presented in place of an access token, or the other way round.
* A token with no `kind` claim is accepted **only** as a refresh token — a temporary compatibility
  window, §1.3 of [research-architecture](../research/research-architecture.md).
* The password is stored as `sha256(hex(salt) + password)`, and compared in constant time.
* A sandbox is issued while there is room; when there is none, `503` with a text for the welcome
  screen rather than `500`.

## 3. Flow

```
welcome ──POST /demo──────────▶ tokens ──▶ main screen
Signup  ──POST /users──▶ 201 ──▶ the Login screen (the person types the same thing again)
Login   ──POST /auth──────────▶ tokens ──▶ main screen

any request ──401──▶ Ktor plugin ──POST /auth/refresh──┬─ 200 ──▶ the request is retried
                                                       ├─ 401 ──▶ expire() ──▶ welcome screen
                                                       └─ 5xx ──▶ failure, session left intact
```

Registration issues **no** tokens: `POST /users` answers `201` with an empty body. In response to
that the client sends the person to the sign-in screen rather than signing them in — see "Quirks".

## 4. Code anchors

| Service | Code |
|---|---|
| shared | `shared/src/commonMain/kotlin/io/github/youndie/mani/feature/auth/` — resources and DTOs |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/auth/` — routes and `AuthService` |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/security/` — `TokenService`, the Bearer provider |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/user/Credentials.kt` — the registration rules |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/demo/` — the sandbox and its sweep |
| server | `server/src/main/kotlin/io/github/youndie/mani/feature/user/data/MongoUserRepository.kt` |
| server-native | `server-native/src/linuxX64Main/kotlin/io/github/youndie/mani/feature/user/data/MongknUserRepository.kt` |
| composeApp | `composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/auth/` — token storage, use cases, the screen |
| composeApp | `composeApp/src/commonMain/kotlin/io/github/youndie/mani/data/RefreshSession.kt` — the token exchange |

## 5. Scenarios (BDD)

### Scenario: register, sign in, and make the first protected request

* **Given:** the name is not in the database.
* **When:** `POST /users` with acceptable data, then `POST /auth` with the same, then a protected
  request carrying the access token received.
* **Then:** `201`, then `200` with a token pair, then the protected route answers with data.
* **Automated:** `ManiApiTest`

### Scenario: registration refuses what it cannot accept

* **Given:** a name shorter than three characters, or with disallowed characters, or with the
  `demo-` prefix, or a password shorter than eight characters.
* **When:** `POST /users`.
* **Then:** `400` and a text saying what to fix — for example
  `Name must be 3 to 32 characters long`.
* **And:** no query reaches the database: the check runs before storage is touched.
* **Automated:** `ManiApiTest`

### Scenario: a refresh exchange returns a new pair and burns the old one

* **Given:** a valid token pair.
* **When:** `POST /auth/refresh` with the old refresh token, then again with the same one.
* **Then:** the first exchange gives `200` and a **different** pair; the second gives `401`, because
  the old token is no longer in the database.
* **Automated:** `ManiApiTest`

### Scenario: the token kinds are not interchangeable

* **Given:** a token pair.
* **When:** the refresh token is presented to a protected route, and the access token to
  `/auth/refresh`.
* **Then:** `401` in both cases.
* **Automated:** `ManiApiTest`

### Scenario: a protected route without a token

* **Given:** a request with no `Authorization` header, or with a corrupt one, or with an expired
  token.
* **When:** it reaches `/transactions`.
* **Then:** `401` with the body `Token is not valid or has expired`.
* **Automated:** `ManiApiTest`

### Scenario: the password is stored hashed

* **Given:** a freshly registered user.
* **When:** the raw user document is read.
* **Then:** the password is not there in the clear: `hash` and `salt` are.
* **Automated:** `MongknStorageTest`

### Scenario: entering through the sandbox issues the same kind of tokens

* **Given:** there is room.
* **When:** `POST /demo` with no body.
* **Then:** `201` with a token pair indistinguishable from one issued by an ordinary sign-in: from
  there on the app does not know which way the visitor came in.
* **And:** the sandbox itself — its contents, its ceiling and its sweep — is covered in
  [feature-demo-sandbox](feature-demo-sandbox.md).
* **Automated:** `DemoRoutingTest`

### Scenario: the server rejected the refresh — the session is over

* **Given:** a client holding a stored refresh token the server no longer accepts.
* **When:** any request gets a `401` and the plugin goes to refresh the session.
* **Then:** the tokens are cleared, the `expired` event is raised, and the screen goes to the
  welcome screen — rather than showing "server unreachable" with a countdown.
* **Automated:** `RefreshSessionTest`

### Scenario: the server fell over — that does not end the session

* **Given:** the same client.
* **When:** `/auth/refresh` answers `500`.
* **Then:** the tokens **stay** where they are, the original request comes back as a failure, and
  nobody is thrown out to the welcome screen.
* **Automated:** `RefreshSessionTest`

### Scenario: there is nothing to refresh with

* **Given:** the refresh token is absent from storage, or empty.
* **When:** the plugin tries to refresh the session.
* **Then:** **no request to the server happens at all**.
* **Automated:** `RefreshSessionTest`

### Scenario: signing out

* **Given:** a signed-in user.
* **When:** sign-out is pressed.
* **Then:** the tokens are cleared, the cached rule list is dropped, and the screen is the welcome
  screen.
* **Automated:** `LogoutUseCaseTest`

### Scenario: a route somebody forgot to protect

* **Given:** a route registered outside `authenticate` that asks who the current user is.
* **When:** a request reaches it.
* **Then:** the handler fails loudly rather than substituting an empty string as the record's owner.
* **Automated:** `UnprotectedRouteTest`

## 6. Out of scope

* Email confirmation, password recovery and password change — the product has none of them.
* Rate limiting on sign-in attempts — none.
* Signing out does not revoke the refresh token on the server: the client clears its own storage
  while the token in the database lives out its month. See "Quirks".
* The sandbox lifecycle — what is seeded, how long it lives, when it is swept —
  [feature-demo-sandbox](feature-demo-sandbox.md).

## 7. Quirks

* **After registering, a person types the same thing a second time.** On success `SignupComponent`
  calls `onSuccess()`, and the graph turns that into a move to the `Login` screen
  (`composeApp/.../navigation/ManiAppNavHost.kt:125`) — there is no automatic sign-in even though
  the pair has just been typed and is known to be good. There is no hint and no prefilled field
  either.
* **Signing out tells the server nothing.** `LogoutUseCase` clears the tokens and the cache locally;
  there is no revocation route, and the refresh token stays in the database until it expires. If a
  device is lost, signing out on it closes nobody else's access.
* **`GET /users/current` is declared in the contract and implemented by nothing** — see
  [endpoint-auth](../api/endpoint-auth.md).
* **Without `JWT_SECRET` a server restart logs everyone out**: the secret is random per process.
  That is a visible price, not a breakage; the line on stdout says why.
* **In the browser the tokens live in `localStorage`** and are readable by any script on the page. A
  single-page app without cookie sessions has no alternative; the other platforms keep them in their
  own store.
* **Passwords are hashed with SHA-256 without iterations.** An inherited format: changing it
  requires migrating the existing records and making the format self-describing. Risk 1 in
  [research-architecture](../research/research-architecture.md).
