---
id: server-native
title: ":server-native — the native binary, the deployed image"
type: service
repo_url: https://github.com/youndie/mani-kotlin-fullstack
module: ":server-native"
tech_stack: [Kotlin/Native linuxX64, Ktor CIO, mongkn, libmongoc, Koin]
owner: unassigned
depends_on:
  - server-common
  - shared
  - MongoDB
  - mongkn
publishes:
  - "ghcr.io/youndie/mani-kotlin-fullstack:<mani.version>.<CI run number>"
---

# :server-native — the native binary, the deployed image

## 1. Responsibility

The `linuxX64` build of the server — **the one that runs on the deployed instance**. What it owns:
storage on [mongkn](https://github.com/youndie/mongkn), serving static files by hand, and the
`main()` entry point. Everything else comes from [server-common](server-common.md).

There is exactly one target, and not by choice: that is what mongkn publishes. On macOS the module
compiles but does not link, which is why development on a Mac goes through [server](server.md).

## 2. API contracts

The same as [server-common](server-common.md), plus the static-file route `GET /{path...}`,
registered **last** — it catches everything left over and therefore does not intercept the API.

## 2a. Code anchors

| File | What is there |
|---|---|
| `server-native/src/linuxX64Main/kotlin/io/github/youndie/mani/Main.kt` | `main()` and `Application.maniModule(config)` — the assembly the tests bring up too |
| `server-native/src/linuxX64Main/kotlin/io/github/youndie/mani/MongknStorageModule.kt` | storage wiring on mongkn |
| `server-native/src/linuxX64Main/kotlin/io/github/youndie/mani/db/DbModel.kt` | the document shape: `StringAsBsonObjectId`, `BigDecimalAsBsonDecimal128` |
| `server-native/src/linuxX64Main/kotlin/io/github/youndie/mani/feature/*/data/` | the port implementations |
| `server-native/src/linuxX64Main/kotlin/io/github/youndie/mani/web/WebAssets.kt` | scanning the static directory at startup |
| `server-native/src/linuxX64Main/kotlin/io/github/youndie/mani/web/WebRoutes.kt` | serving files, ETag, `Cache-Control`, the pre-built `.gz` |
| `server-native/Dockerfile` | the image: static build stage + runtime |
| `server-native/src/linuxX64Test/kotlin/io/github/youndie/mani/TestMongo.kt` | the harness for tests that work in databases of their own |

## 3. How it is built

**The binary is linked outside and only copied into the image.** Linking Kotlin/Native inside docker
would run without the Gradle cache and take minutes on every image build. Hence the two-step deploy
in `deploy.yml`: first `linkReleaseExecutableLinuxX64` and `wasmJsBrowserDistribution`, then
`docker build`.

**The base image version is tied to the build machine.** The binary is linked against whichever
`libmongoc` was installed at link time; on Ubuntu 24.04 that is 1.26. The soname
(`libmongoc-1.0.so.0`) is shared across branches, so a substitution is **caught neither by the build
nor by startup** — it shows up as a missing symbol on the first call into Mongo. The CI runner, the
deploy runner and the `FROM` in the Dockerfile must be the same distribution version; change one,
change both.

**The static directory is read once at startup** rather than per request: the files are baked into
the image and do not change over the life of the process. An empty `MANI_WEB_ROOT` means a server
with no frontend, which is convenient for bringing it up in tests.

**On-the-fly compression is impossible here:** `ktor-server-compression` is published for the JVM
only. Instead, the image holds a ready `.gz` next to each file, compressed once at build time
(`Dockerfile`, stage `web`), and that is what is served when the client accepts gzip.

**`Cache-Control: immutable` is set by file name, not by extension.** The rule ".wasm means
immutable" is wrong: next to `6e23e5428398b92da386.wasm` the bundle holds `skiko.wasm` under a
constant name. What is checked is that the name is at least 16 hexadecimal digits — that is what
webpack gives precisely to the files that are rebuilt under a new name on any edit
(`WebRoutes.kt:34`, test `WebCachingTest`).

**There is no logger.** `koin-logger-slf4j` is JVM-only, and so is `CallLogging`. Diagnostics go
through `println` to stdout, which in a container is the log.

## 4. Dependencies

| Kind | Name | What for |
|---|---|---|
| Module | [server-common](server-common.md) | the whole server except storage |
| Database | MongoDB | `MONGO_HOST`, `MONGO_DATABASE` |
| Library | [mongkn](https://github.com/youndie/mongkn) | a MongoDB driver for Kotlin/Native (ours; there is no official one) |
| System | `libmongoc-dev`, `libbson-dev` at build time; `libmongoc-1.0-0t64` in the image | the C driver mongkn works over |
| Library | Ktor CIO | HTTP |

## 5. Infrastructure and deploy

* **Image:** `ghcr.io/youndie/mani-kotlin-fullstack:<mani.version>.<run number>`
* **Manifest:** `.k8s-templates/deployment.yaml` (a template; `envsubst` substitutes the version and
  the run number, the result goes to `.k8s/` and is applied with `kubectl apply`)
* **Liveness:** `GET /health` — **does not touch** the database, deliberately: a liveness probe that
  depends on the database turns its outage into a restart of every pod
* **Readiness:** `GET /health/ready` — does ask the database: a pod that cannot see it should not
  receive traffic. No `initialDelaySeconds` is needed, the binary answers 87 ms after startup
* **Resources:** requests `50m`/`64Mi`, limits `1`/`128Mi`
* **Deploy:** `.github/workflows/deploy.yml`, on the completion of a green `Test` run on `main`,
  plus a manual `workflow_dispatch`

Measured on the built image: 87 ms from start to the first answered request, 42 MiB at rest, 45 MiB
peak, a 213 MB image and a 13 MB binary. **Without load and on one replica.**

## 6. Local setup

Linux only. The C driver is required:

```bash
sudo apt-get install -y libmongoc-dev libbson-dev
```

```bash
./gradlew :server-native:linkReleaseExecutableLinuxX64 :composeApp:wasmJsBrowserDistribution
```

```bash
docker build -f server-native/Dockerfile -t mani-native .
```

The tests need a real `mongod` — what they look for raises no errors:

```bash
docker run -d --name mani-mongo -p 27017:27017 mongo:8
```

```bash
./gradlew :server-native:linuxX64Test :server-native:linuxX64ReleaseTest
```

**The release run is not optional.** Kotlin/Native omits type-cast checks in release builds, and
code that fails with a catchable exception in debug reaches undefined behaviour in release. The
binary that ships in the image is the release one.

## 7. Configuration

The same variables as [server-common](server-common.md). The image sets `MANI_WEB_ROOT` and `PORT`
(`Dockerfile`); the manifest sets `MONGO_HOST` and `JWT_SECRET` from the `mani-backend` secret.

## 8. Quirks

* **`ca-certificates` cannot be found in the image by `ldd`.** The package is installed on a line of
  its own because it is not a library but a set of root certificates, and `ubuntu:24.04` has none at
  all. mani makes no outbound https calls today, but the first one would otherwise look like a
  silent failure rather than a missing package.
* **Escaping the static root is cut off by a check for `..`** (`WebRoutes.kt:57`), while an unknown
  path returns `index.html` — this is an SPA, and the app routes from there.
