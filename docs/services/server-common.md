---
id: server-common
title: ":server-common — the server, minus storage"
type: service
repo_url: https://github.com/youndie/mani-kotlin-fullstack
module: ":server-common"
tech_stack: [Kotlin Multiplatform, Ktor server, Koin, cryptography-kotlin]
owner: unassigned
depends_on:
  - shared
publishes:
  - klib/jar consumed inside the build (nothing is published externally)
---

# :server-common — the server, minus storage

## 1. Responsibility

**The whole server except the calls into the database.** Routes, input validation, token issuing and
verification, password hashing, configuration, the Ktor plugins, the shape of the DI graph. It
compiles to `jvm` and `linuxX64`, and what lives here is executed by both builds as **one body of
code**.

What is not here: the storage implementations. The official MongoDB driver exists only on the JVM,
mongkn only on linuxX64, and they share no document type. So the common source set declares only the
ports — the `UserRepository`, `TokenRepository`, `TransactionRepository`, `CategoryRepository` and
`StorageHealth` interfaces — and the implementations are brought in by [server](server.md) and
[server-native](server-native.md).

The rule this follows from: **everything that is not a call into the database has to be shared.**
Every superfluous `expect/actual` pair is two implementations that will diverge silently.

## 2. API contracts

Paths and DTOs live in [shared](shared.md). The api layer breaks them down by status code:
[endpoint-transactions](../api/endpoint-transactions.md),
[endpoint-categories](../api/endpoint-categories.md), [endpoint-auth](../api/endpoint-auth.md),
[endpoint-demo](../api/endpoint-demo.md), [endpoint-health](../api/endpoint-health.md),
[endpoint-currencies](../api/endpoint-currencies.md).

Auth tiers:

| Tier | How it is applied | Which routes |
|---|---|---|
| open | outside `authenticate` | `POST /auth`, `POST /auth/refresh`, `POST /users`, `POST /demo`, `GET /health`, `GET /health/ready`, `GET /currencies` |
| Bearer access token | `authenticate(jwtConfig.name)` | `/transactions`, `/transactions/{id}`, `/categories`, `/categories/{id}`, `POST /demo/seed` |

Guarded by the tests `protected routes require a valid token` and
`a refresh token opens no door and an access token refreshes nothing`.

## 2a. Code anchors

| File | What is there |
|---|---|
| `server-common/src/commonMain/kotlin/io/github/youndie/mani/ManiApp.kt` | the DI contents (`coreModule`), the plugins (`configureManiPlugins`), token verification (`configureManiAuth`), route assembly (`maniApiRouting`) |
| `server-common/src/commonMain/kotlin/io/github/youndie/mani/config/ManiConfig.kt` | all configuration from ENV + `expect fun readEnv` |
| `server-common/src/commonMain/kotlin/io/github/youndie/mani/security/TokenService.kt` | issuing and verifying JWTs |
| `server-common/src/commonMain/kotlin/io/github/youndie/mani/security/ManiAuth.kt` | our own Bearer provider instead of `ktor-server-auth-jwt` |
| `server-common/src/commonMain/kotlin/io/github/youndie/mani/security/Base64Url.kt` | base64url, hex, constant-time comparison |
| `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/` | one directory per subject area: `<X>Routing.kt` + `data/` with the ports |
| `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/Rules.kt` | what makes a rule and a category acceptable |
| `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/user/Credentials.kt` | the registration rules |
| `server-common/src/jvmMain/`, `server-common/src/linuxX64Main/` | exactly two `actual`s: `readEnv` and `serverBuildKind` |

## 3. How it is built

**The order the application is assembled in is fixed, and that is not a matter of style.**
`configureManiPlugins` → `Koin` → `configureManiAuth(config, get<TokenService>())` → routes. Token
verification is installed **after** Koin deliberately: `TokenService` is taken out of the graph
rather than constructed a second time on the same secret — otherwise the secret would have to be
threaded to a second place. Both builds repeat this order (`server/.../Application.kt`,
`server-native/.../Main.kt`).

**`StatusPages` instead of a logger.** Neither build has a logger in the common source set
(`CallLogging` is JVM-only), so a failure is printed with `println` to stdout, which in a container
is the log. Without this the native build answered 500 and left no trace. How causes are sorted:

* `CancellationException` is rethrown — a request the client abandoned is neither a failure nor
  a 500;
* `BadRequestException`, `IllegalArgumentException`, `SerializationException` → `400 Malformed
  request` under one generic text: the details would say more about how the server is built than
  anyone needs;
* everything else → 500 plus a line on stdout.

**`Json` without `isLenient`.** The relaxation let bodies arrive without quotes, which means the
server took to guessing what the sender meant. Our clients write JSON with a serializer, so the
relaxation served only whoever bypasses them.

**CORS only in development mode.** On the deployed instance the same server serves the frontend,
so there is no reason to allow foreign origins (`MANI_DEVELOPMENT`).

**Validation sits on the server, not only in the form.** The client's form prevents half of this
anyway, and the rule still lives here: a form is a convenience, not a boundary, and behind it is
open HTTP. The refusal texts are returned outward and shown to a person, which is why they are in
English and say what to fix.

## 4. Dependencies

| Kind | Name | What for |
|---|---|---|
| Module | [shared](shared.md) | resources and DTOs |
| Library | Ktor server (`resources`, `content-negotiation`, `status-pages`, `cors`, `auth`) | HTTP |
| Library | Koin | DI, the shared shape of the graph |
| Library | `cryptography-kotlin` (`-prebuilt`) | HMAC-SHA256 and SHA-256 on both builds; OpenSSL on native, so `libssl-dev` is not needed on the build machine |
| Library | `com.auth0:java-jwt` — **`jvmTest` only** | the reference for token format compatibility |

## 5. Infrastructure and deploy

None of its own: it is deployed inside [server-native](server-native.md) (the deployed instance) and
[server](server.md) (locally).

## 6. Local setup

```bash
./gradlew :server-common:jvmTest
```

The native set runs on Linux and needs no `mongod` — these tests do not go to the database:

```bash
./gradlew :server-common:linuxX64Test
```

## 7. Configuration

Every key is declared in one place, `ManiConfig.fromEnv()`. The list with its defaults is
deliberately not duplicated here: it is in `config/ManiConfig.kt` and in `README.md`, section
"Configuration".

Two are worth knowing:

| Key | What happens if it is unset |
|---|---|
| `JWT_SECRET` | a random per-process secret plus a warning on stdout; a restart logs everyone out |
| `MANI_WEB_ROOT` | no frontend is served, the API only |

## 8. Quirks

* **A token with no `kind` claim is accepted as a refresh token.** A temporary compatibility window
  with a removal date — see §1.3 in
  [research-architecture](../research/research-architecture.md). The one thing in the code that is
  supposed to disappear on its own.
* **`currentUserId()` throws `error()` rather than answering 401.** Inside `authenticate` that
  branch is dead: a request without a principal never gets there. A missing principal here means an
  **unprotected route**, that is a wiring mistake, and it has to be loud. The previous version
  returned an empty string, which would have gone into storage as the owner. The trap is guarded by
  the test `a route outside authenticate cannot ask who is calling`.
* **`credentialsProblem` validates registration only.** Sign-in has to accept a name whatever it
  looks like: names created before these rules do not obey them, and enforcing the rules at sign-in
  would have evicted existing users.
* **Names starting with the demo sandbox prefix are reserved.** Otherwise one could create an
  account that the sandbox sweep carries off a day later.
* **`UserResource.CurrentUserResource` (`/users/current`) is implemented by nothing.** The class is
  declared in the contract (`shared/.../feature/user/UserResource.kt:9`), there is no handler in
  `UserRouting.kt`, and no client calls it — searching the whole tree for `CurrentUserResource`
  returns a single occurrence, the declaration itself. So `/users/current` returns whatever any
  unknown path returns. The contract promises more than the server does; verified 2026-09-08.
