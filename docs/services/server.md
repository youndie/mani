---
id: server
title: ":server — the JVM build"
type: service
repo_url: https://github.com/youndie/mani-kotlin-fullstack
module: ":server"
tech_stack: [Kotlin/JVM, Ktor CIO, MongoDB Kotlin Driver, Koin]
owner: unassigned
depends_on:
  - server-common
  - shared
  - MongoDB
publishes:
  - a local image via `publishImageToLocalRegistry`
---

# :server — the JVM build

## 1. Responsibility

The JVM build of the server. All it owns is **storage and serving static files**: implementations of
the ports on the official MongoDB driver, plus `staticResources` out of the jar. Everything else
comes from [server-common](server-common.md).

This is the development build — the only one that compiles **on macOS**, where the native target
cannot be linked at all. The deployed instance does not run it; it runs
[server-native](server-native.md).

## 2. API contracts

The same as [server-common](server-common.md) — the routes are shared. It adds no routes of its
own beyond serving static files.

## 2a. Code anchors

| File | What is there |
|---|---|
| `server/src/main/kotlin/io/github/youndie/mani/Application.kt` | entry point: `EngineMain`, and the order the application is assembled in |
| `server/src/main/kotlin/io/github/youndie/mani/Routing.kt` | `maniApiRouting()` plus serving the wasm app |
| `server/src/main/kotlin/io/github/youndie/mani/MongoStorageModule.kt` | storage wiring: client, database, repositories, `StorageHealth` |
| `server/src/main/kotlin/io/github/youndie/mani/feature/*/data/` | the ports implemented on the official driver |
| `server/src/main/kotlin/io/github/youndie/mani/feature/*/data/*Db.kt` | the document shape: `@BsonId val id: ObjectId`, `amount: java.math.BigDecimal` |
| `server/src/main/kotlin/io/github/youndie/mani/utilz/wasmJsApp.kt` | static files out of the jar's resources |
| `server/src/main/resources/application.conf` | read only by `EngineMain`, and only for the port |
| `server/src/test/kotlin/ManiTestServer.kt` | the shared harness for this build's tests |

## 3. How it is built

**`application.conf` survives for one thing — the port.** `EngineMain` reads it itself, and that is
a JVM-only mechanism. Everything else comes from ENV under the same names as the native build
(`ManiConfig.fromEnv()`), so the two builds are configured **identically** rather than differently.

**The document shape in the database matches the native build's, but is arrived at differently.**
Here it is set by the official driver's codecs: `@BsonId val id: ObjectId` gives `_id` as an
`ObjectId`, and the driver writes `java.math.BigDecimal` as `decimal128`. On the native build the
same thing is done by our own serializers (`StringAsBsonObjectId`, `BigDecimalAsBsonDecimal128`). A
divergence here breaks nothing loudly — the query simply fails to find existing documents — which is
why it is guarded by tests that look at the **raw document** rather than at the result of `find`.

## 4. Dependencies

| Kind | Name | What for |
|---|---|---|
| Module | [server-common](server-common.md) | the whole server except storage |
| Database | MongoDB | `MONGO_HOST`, `MONGO_DATABASE` |
| Library | MongoDB Kotlin Driver (official) | database access |
| Library | Ktor CIO + `ktor-server-*` (JVM) | HTTP, static files, logging |
| Library | `koin-logger-slf4j`, logback | logs (JVM-only; the native build has none) |

## 5. Infrastructure and deploy

**It does not reach the deployed instance.** That runs the image from
[server-native](server-native.md).

A local image:

```bash
./gradlew publishImageToLocalRegistry
docker compose up -d
```

`GET /health` on this build answers `{"build": "jvm", ...}` — that field is how you tell which build
answered.

## 6. Local setup

The sequence from `README.md`: build the image, bring it up next to MongoDB, open
[http://localhost:8080/](http://localhost:8080/) and press **Try the demo**.

Tests:

```bash
./gradlew :server:test
```

## 7. Configuration

The same variables as [server-common](server-common.md) (`ManiConfig.fromEnv()`), plus the port from
`server/src/main/resources/application.conf`, which `EngineMain` reads.

## 8. Quirks

* **`docker-compose.yaml` does not set `JWT_SECRET`.** That is not an oversight: without the
  variable the server signs with a random per-process secret and says so on stdout. Restarting the
  container logs everyone out — expected behaviour for a local instance, not a breakage.
* **Development mode is off by default in this build.** CORS is installed only when
  `MANI_DEVELOPMENT=true`.
