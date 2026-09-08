---
id: research-architecture
title: mani — architecture research
type: research
status: active
date: 2026-09-08
---

# Research: how mani is built, and why this way

mani is a budget planner written end to end in Kotlin: a Compose Multiplatform client for Android,
iOS, desktop and the browser; a Ktor server that compiles both to JVM bytecode and to a native
linuxX64 binary; and one contract module shared by all of them. What separates it from the
neighbouring KMP demonstrations is not the list of targets but that the sharing here is **not
decorative**: the same `@Resource` classes route the request on the server and build the URL on the
client, the same code signs a token in both builds, and `GET /health` says which of them answered.

The product declares itself a demonstration: there is no email confirmation, no password recovery
and no rate limiting, and passwords are hashed with salted SHA-256 rather than a slow KDF
(`README.md`, the "This is a demo project" callout). That shapes what is called a risk below and
what is called an accepted price.

This document records **verified facts** (what was actually read in this code), **decisions taken**
and **risks**. Anything unverified is marked as a hypothesis and says where it will be settled.

It is the entry point of the documentation: the layers say what the system does, this file says why
it is built that way. It lives here permanently and is amended at the point of divergence rather
than rewritten.

---

## 1. Verified facts

### 1.1 The server is compiled twice from one source

| Fact | Where verified |
|---|---|
| Eight modules: `:shared`, `:composeApp`, `:server-common`, `:server`, `:server-native`, `:androidApp`, `:iosApp`, `:baselineprofile` | `settings.gradle.kts` |
| Routes, configuration, token issuing and verification, and hashing live in `:server-common` (jvm + linuxX64) | `server-common/src/commonMain/kotlin/io/github/youndie/mani/ManiApp.kt` |
| Storage is the only thing each build brings of its own | `ManiApp.kt:48` (`coreModule`), `server/src/main/kotlin/io/github/youndie/mani/MongoStorageModule.kt`, `server-native/src/linuxX64Main/kotlin/io/github/youndie/mani/MongknStorageModule.kt` |
| The storage ports are declared as interfaces in the common source set | `server-common/.../feature/user/data/UserRepository.kt`, `.../feature/transaction/data/TransactionRepository.kt`, `.../feature/health/StorageHealth.kt` |
| The native build is `linuxX64` only, because that is the single target mongkn publishes | `README.md`, section "Two server builds" |

**Consequence 1.** Everything that is not a call into the database has to live in `commonMain`.
Every item moved into `expect/actual` without need is two implementations that will diverge
silently: the compiler checks the signature and does not check the behaviour.

**Consequence 2.** "It compiled and went green on the JVM" says nothing about what will reach the
deployed instance. Hence the separate `test-native` job in CI (`.github/workflows/main.yml`), and
hence the requirement to run the release set and not only the debug one (§1.5).

### 1.2 Tokens are issued and verified by one body of code on both builds

| Fact | Where verified |
|---|---|
| `TokenService` is an ordinary class in `commonMain` over `cryptography-kotlin`, with no `expect/actual` | `server-common/.../security/TokenService.kt:49` |
| Algorithm `HS256`, `sub` always `"Authentication"` — that is how `com.auth0:java-jwt` signed | `TokenService.kt:170`, `TokenService.kt:174` |
| The payload carries `sub`, `aud`, `iss`, `id`, `username`, `exp`, `jti`, `kind` | `TokenService.kt:78` |
| `aud`/`iss` are parsed both as a string and as an array of strings (RFC 7519) | `TokenService.kt:163` |
| Compatibility with the old format is checked with a token signed by `java-jwt` | `server-common/src/jvmTest/.../LegacyTokenCompatibilityTest.kt` |
| The Bearer header check is our own rather than `ktor-server-auth-jwt`, which is JVM-only | `server-common/.../security/ManiAuth.kt:22` |

**Consequence.** A token issued by the JVM build is accepted by the native one and the other way
round. That is not a convenience but a condition of deployment: the instance can be switched
between builds without logging everyone out.

**Why not `expect/actual`.** It is written in the class's own KDoc and worth repeating: two
signing implementations diverge not at build time but on the day a token from one is refused by the
other. Having no native build at all would have been cheaper than that mistake.

### 1.3 Telling access from refresh rests on the `kind` claim

| Fact | Where verified |
|---|---|
| `kind` is written into the token and checked on verification | `TokenService.kt:93`, `TokenService.kt:146` |
| The Bearer provider demands `TokenKind.Access` specifically | `ManiAuth.kt:32` |
| `/auth/refresh` demands `TokenKind.Refresh` **and** the token's presence in the database | `server-common/.../feature/auth/data/AuthService.kt:36` |
| Refresh lives a month, access lives `JWT_EXPIRATION_SECONDS`, 3600 by default | `AuthService.kt:64`, `server-common/.../config/ManiConfig.kt:43` |
| `jti` of 16 random bytes — without it two tokens issued in the same second are byte-identical | `TokenService.kt:92` |

**Consequence.** While the kind was not in the token, a refresh token was accepted everywhere an
access token was expected — that is, the access token's lifetime meant nothing. Refresh rotation
without `jti` was equally illusory: `exp` in a JWT is stored in seconds and the other claims match,
so the "new" token equalled the old one.

**A temporary allowance with a removal date.** A token with no `kind` claim is accepted as a
refresh token (`TokenService.kt:146`). This is a deliberate compatibility window: such tokens are
in the deployed instance's database, and refusing them would log out everyone who signed in before
the rollout. It opens no forgery — `/auth/refresh` also checks the presented token against the
database, and access tokens never get there. **It is removed** once the last refresh token issued
before the rollout expires, i.e. a month after the first deployment of the build carrying `kind`.
The branch to delete is `kind == null && expect == TokenKind.Refresh` in `TokenService.verify`.

### 1.4 The password hash format reproduces the previous JVM implementation

| Fact | Where verified |
|---|---|
| `sha256(hex(salt) + password)`, all in hex; salt from a CSPRNG | `server-common/.../feature/auth/data/hashing/HashingService.kt:33` |
| The comparison runs in constant time | `HashingService.kt:42`, `server-common/.../security/Base64Url.kt:78` (`constantTimeEquals`) |
| The format is checked against a record taken from the previous `commons-codec` implementation | `server-common/src/commonTest/.../security/HashingServiceTest.kt` |

**Consequence.** The chief requirement on this class is not its design but a byte-for-byte match
with what already sits in the deployed instance's database. Any deviation — a different
concatenation order, a base64 salt, a different hex case — would mean that no existing user can
sign in any more.

**This is inheritance, not a decision.** SHA-256 without iterations is not what human-chosen
passwords should be hashed with. See risk 1.

### 1.5 The native build needs its own checks and its own pairing with the image

| Fact | Where verified |
|---|---|
| CI runs `:server-common:linuxX64Test`, `:server-native:linuxX64Test` **and** `:server-native:linuxX64ReleaseTest` | `.github/workflows/main.yml`, job `test-native` |
| The runner is pinned to `ubuntu-24.04` in both the tests and the deploy | `.github/workflows/main.yml`, `.github/workflows/deploy.yml` |
| The image base is `ubuntu:24.04`, the runtime package `libmongoc-1.0-0t64` | `server-native/Dockerfile` |
| The binary is linked outside and only copied into the image | `server-native/Dockerfile`, step `Build native server and frontend` in `deploy.yml` |
| The native tests need a real `mongod`: what they look for raises no errors | `README.md`, section "Tests"; `server-native/src/linuxX64Test/.../TestMongo.kt` |

**Consequence 1.** The release run is mandatory rather than desirable: Kotlin/Native omits
type-cast checks in release builds, and code that fails with a catchable exception in debug reaches
undefined behaviour in release. The comment in `main.yml` names the occasion when this already
happened: `/auth/refresh` answered 500 on the deployed instance while CI was green.

**Consequence 2.** The runner's distribution version and the image base are a **pair**. The soname
`libmongoc-1.0.so.0` is shared across branches, so a substitution is caught neither by the build nor
by startup: it shows up as a missing symbol on the first call into Mongo. Change one, change both.

### 1.6 Database compatibility rests on two serializers

| Fact | Where verified |
|---|---|
| `_id` is written as an `ObjectId`, amounts as `decimal128` | JVM: `server/.../feature/transaction/data/TransactionDb.kt`, `.../feature/user/data/UserDb.kt` (`@BsonId` + `java.math.BigDecimal`); native: `server-native/.../db/DbModel.kt:38` (`StringAsBsonObjectId`, `BigDecimalAsBsonDecimal128`) |
| Checked by tests that look at the **raw document** rather than at the result of `find` | tests `user id lands in mongo as ObjectId`, `transaction amount lands in mongo as decimal128` (`server-native/src/linuxX64Test/.../MongknStorageTest.kt`) |
| Categories live inside the user document, not next to the transaction | `server-common/.../feature/transaction/data/TransactionRepository.kt:9`, test `categories live inside the user document` |

**Consequence.** A divergence here breaks nothing loudly: the query simply fails to find existing
documents. That is why the oracle is the raw document — a test that goes through `find` would pass
on a diverged format too, because it would write and read the same wrong way.

### 1.7 Configuration comes from the environment, identically on both builds

| Fact | Where verified |
|---|---|
| Everything is read from ENV; `readEnv` is configuration's only `expect/actual` | `server-common/.../config/ManiConfig.kt:118` |
| The names are the ones that stood in `application.conf` as `${?...}` and in `.k8s-templates/deployment.yaml` | `ManiConfig.kt:8` (KDoc), `server/src/main/resources/application.conf` |
| `JWT_SECRET` unset means a random per-process secret plus a line on stdout | `ManiConfig.kt:66` |
| The Mongo user and password are escaped before being put into the connection string | `ManiConfig.kt:98` |
| The JVM build's port comes to `EngineMain` from `application.conf`, everything else from ENV | `server/src/main/kotlin/io/github/youndie/mani/Application.kt:20` |

**Consequence.** HOCON is read by JVM-only Ktor code, so it is unavailable to the native build. The
move to ENV was not merely forced: the names coincided with the ones already in place, so the
configuration of the two builds **converges** rather than diverging.

### 1.8 One product version for everything

| Fact | Where verified |
|---|---|
| `mani.version` in `gradle.properties` is the single source | `gradle.properties` |
| The `/health` response names the same number | `server-common/.../feature/health/HealthRouting.kt:62` (`MANI_VERSION`) |
| The image tag is `<mani.version>.<CI run number>` | `.github/workflows/deploy.yml`, steps `Read the product version` and `Build and push image` |

**Consequence.** There used to be four numbers, and the server's answer named one while the tag of
the image it was started from named another. Now the `/health` answer locates the image. The run
number is appended as a suffix rather than replacing the version.

### 1.9 Client: the navigation start destination is read once

| Fact | Where verified |
|---|---|
| The start destination is computed inside `remember` and is **not** subscribed to the token | `composeApp/.../navigation/ManiAppNavHost.kt:52` |
| Session expiry arrives as an **event** (`TokenRepository.expired`), not as state | `composeApp/.../feature/auth/data/TokenRepository.kt:20`, `TokenRepositoryCommon.kt:22` |
| The event has no replay (`replay = 0`) | `TokenRepositoryCommon.kt:22` |
| The "signed in" and "signed out" transitions are made explicitly | `ManiAppNavHost.kt:82`, `ManiAppNavHost.kt:92`, `ManiAppNavHost.kt:106` |

**Consequence.** Subscribing navigation to the token itself rebuilds the graph, and a new graph
resets navigation to its own start destination — so every arrival of a token silently threw the
screen somewhere else. While the start destination was computed from that same token this looked
like working navigation; the moment it was fixed, signing in started returning to the welcome
screen. The same goes for `replay`: a replayed event would reach every new subscriber and throw the
person back to the welcome screen on the next recomposition.

### 1.10 The client shows the last known list when there is no network

| Fact | Where verified |
|---|---|
| The transaction cache lives in the same `Settings` as the tokens | `composeApp/.../feature/transaction/data/TransactionsCache.kt` |
| Both the list and the **timestamp** of when it was taken are stored | `TransactionsCache.kt:33` |
| A broken or absent cache is a reason to go to the network, not to crash | `TransactionsCache.kt:44` |
| The screen state distinguishes "showing the cache" from "nothing to show" | `composeApp/.../feature/main/ui/MainUiState.kt:24`, `MainUiState.kt:26` |
| The "server unreachable" screen prints a machine-readable cause: code, host, time | `composeApp/.../feature/main/ui/ServerUnreachable.kt:30` |

**Consequence.** Rules are not an event feed: yesterday's list is still correct today. Showing it
with a timestamp is more honest than showing nothing. And an error message without a code and a
host does not distinguish "my wifi" from "everything is down over there".

---

## 2. Decisions

### D1. The server is compiled twice from one source rather than rewritten

First idea: since the native build exists for fast startup and small memory, write it separately.

Decision: `:server-common` compiles to both targets, and the difference is confined to the storage
module.

Why:

- only the places that have two implementations can diverge; confining them to one module confined
  the risk to one module;
- the JVM build remains the only one that compiles on macOS (`README.md`) — without it, development
  on a Mac would need a Linux machine for every run;
- the price: any library with no Kotlin/Native artefact is evicted from the common source set —
  that is how `ktor-server-auth-jwt`, `ktor-server-compression`, `CallLogging`, HOCON and Koin's
  slf4j logger left.

### D2. Our own JWT parsing instead of `expect/actual` over two libraries

First idea: `java-jwt` on the JVM, something native on linuxX64, a common interface on top.

Decision: one implementation in `commonMain` over `cryptography-kotlin`.

Why:

- with two implementations the signature, the claim set and the verification rules diverge
  **silently** — visible not at build time but on the day one build's token is refused by the other;
- compatibility with the old format was required anyway (the database holds tokens from
  `java-jwt`), and checking it against two implementations would have meant doing it twice;
- `cryptography-kotlin` takes the `-prebuilt` OpenSSL provider, so `libssl-dev` is not needed on the
  build machine;
- the price: JWT parsing is written by hand, including `aud`/`iss` as string-or-array
  (`TokenService.kt:163`). `java-jwt` stayed in `jvmTest` as the reference.

### D3. Passwords stay on SHA-256; migration is a separate task

Decision: leave the format alone and write the shortcoming down.

Why: changing the hash only makes sense together with migrating the existing records, and for that
the format has to be made self-describing (today it carries neither the algorithm name nor an
iteration count). It cannot be changed quietly — existing users would stop being able to sign in.
See risk 1.

### D4. Configuration from ENV rather than from HOCON

Decision: `ManiConfig.fromEnv()` in the common source set, with `readEnv` as the only
`expect/actual`.

Why: Ktor's HOCON reader is JVM-only. The alternative — a HOCON parser of our own for native —
would have cost more than moving a dozen keys, and the names already matched the ones in the k8s
manifest.

### D5. Without `JWT_SECRET`, a random secret rather than a refusal to start

First idea, and the most honest one: fail if the variable is not set.

Decision: generate a random per-process secret and **say so** on stdout.

Why:

- the previous default was the string `secret`, printed in the source, and the `docker-compose.yaml`
  from the README never set the variable — so everyone who brought the stack up by the instructions
  signed tokens with a value anybody could read;
- failing would have broken `docker compose up` from the README for a benefit a local instance does
  not have;
- a random secret keeps the instance working and makes the price visible: a restart logs everyone
  out, and the line in the log says why;
- in production the variable comes from a k8s secret (`.k8s-templates/deployment.yaml`).

### D6. The same server serves the static files; compression happens at image build

Decision: the native build reads the directory once at startup and serves pre-compressed `.gz`
files.

Why:

- `staticResources` does not exist under Kotlin/Native, and `ktor-server-compression` is published
  for the JVM only — there is nothing to compress with per request;
- compression moved into `server-native/Dockerfile`, where it happens once;
- the directory is scanned at startup rather than per request: the files are baked into the image
  and do not change over the life of the process (`server-native/.../Main.kt:42`);
- `Cache-Control: immutable` is set **by file name**, not by extension. The rule ".wasm means
  immutable" looks right and is wrong: next to `6e23e5428398b92da386.wasm` the bundle holds
  `skiko.wasm` under a constant name, and marking it immutable for a year would have produced
  browsers that a Compose update never reaches at all
  (`server-native/.../web/WebRoutes.kt:34`).

### D7. `:shared` holds the contract and nothing else

Decision: the common module holds the `@Resource` classes, the model and the serializers, and
nothing more.

Why: the server address moved out of it into `composeApp/.../Constants.kt` — a server has no use
for its own address, and a module called the contract should be one. The record's owner (`userId`)
is left out for the same reason: it is a server-side notion
(`server-common/.../feature/transaction/data/TransactionRepository.kt:15`).

---

## 3. Risks and open questions

**Risk 1. Passwords are hashed with salted SHA-256 without iterations.** A database leak makes
brute-forcing cheap: one SHA-256 operation per candidate, and the salt only defends against shared
rainbow tables. The machinery for removing it: make the stored format self-describing (algorithm and
parameters next to the hash), then re-hash on the next successful sign-in while still reading the
old format. Until that is done, the limitation is named in `README.md` as a property of the demo
rather than hidden.

**Risk 2. The libmongoc version in the image and on the runner can diverge silently.** The soname is
shared across branches, so a substitution is caught neither by the build nor by startup: it shows up
as a missing symbol on the first call into Mongo — that is, on the deployed instance, not in CI.
The machinery: both versions are pinned by an explicit `ubuntu-24.04` in `main.yml` and
`deploy.yml` and by `FROM ubuntu:24.04` in `server-native/Dockerfile`, and this is recorded in a
comment in the Dockerfile itself. **What is missing today:** a check that compares the three places
with each other; it would catch an edit to one of them.

**Risk 3. In the browser the tokens live in `localStorage`.** Any script on the page can read them.
Named in `README.md`; a single-page app without cookie sessions has no alternative, and changing
this means changing the session model, not the storage. Desktop, Android and iOS keep the tokens in
the platform's own store.

**Open question 1. What to do about `Category.default` when a category has been deleted.** Building
a `Transaction` substitutes the category by `categoryId`, and falls back to `Category.default` when
it is not found (`TransactionRepository.kt:38`). This is a silent degradation: a deleted category
looks like "Default" rather than like an error. Hypothesis: for this product that is the right
behaviour — the record matters more than the label — but it has never been verified, and there is
neither a test for the path nor a decision in writing.

**Open question 2. The measurements were taken at a single point.** 87 ms to the first answer,
42 MiB at rest, 45 MiB peak, a 213 MB image and a 13 MB binary — measured on the built image
**without load and on one replica** (`README.md`, `.k8s-templates/deployment.yaml`). Nothing was
measured under traffic, and `limits.memory: 128Mi` in the manifest rests on the at-rest figure.
Where it will be settled: the first run with real load on the deployed instance.

---

## 4. What happens next

This repository does not keep an order of work in its documentation — the working backlog was
deliberately moved out, commit `b00fafe`. The nearest thing worth closing out of the above: removing
the temporary allowance on the `kind` claim (§1.3). It has a date, and it is the one thing in the
code that is supposed to disappear on its own.
