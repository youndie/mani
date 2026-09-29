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
| `server-native/src/linuxX64Main/kotlin/io/github/youndie/mani/web/WebRoutes.kt` | serving files: choosing `.br`/`.gz`/as is, ETag, `Vary` |
| `server-common/src/commonMain/kotlin/io/github/youndie/mani/web/WebCaching.kt` | the `Cache-Control` rule, shared with [server](server.md) |
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
only. Instead, the image holds a ready `.br` (brotli, quality 11) and `.gz` (gzip -9) next to each
file, compressed once at build time (`Dockerfile`, stage `web`). The route sends brotli if the
client accepts it, then gzip, then the file as is; an encoding refused with `q=0` is not sent, and
every response carries `Vary: Accept-Encoding`. Each representation has its own ETag.

**Every file is compressed, not a list of extensions.** The list that was here (js, mjs, wasm,
html, css, json, svg) missed the Compose fonts: four `.ttf` files, 984 KB together, all fetched
before the first paint, went out uncompressed up to and including 1.4.3. A compressed copy is kept
only when it is at least 10% smaller than the original, so formats that are compressed already
(png, woff2) drop out by themselves. Measured on the bundle built from `main` on 2026-09-27, in
`ubuntu:24.04`:

| File | as is | gzip -9 | brotli -q 11 |
|---|---|---|---|
| `JetBrainsMono-Regular.ttf` | 273 900 | 128 663 | 105 432 |
| four fonts together | 984 244 | 476 797 | 380 085 |
| `bfa5198fb2fe683c613a.wasm` | 8 640 316 | 3 328 940 | 2 618 182 |
| `c7e0bbc920b8739dd350.wasm` | 5 927 757 | 1 815 285 | 1 348 810 |
| `mani.js` | 542 431 | 101 883 | 82 944 |

**`Cache-Control: immutable` is set by file name, not by extension.** The rule ".wasm means
immutable" is wrong: next to `6e23e5428398b92da386.wasm` the bundle holds `skiko.wasm` under a
constant name. What is checked is that the name is at least 16 hexadecimal digits — that is what
webpack gives precisely to the files that are rebuilt under a new name on any edit
(`server-common/.../web/WebCaching.kt:26`, test `WebCachingTest`). Everything else is `no-cache`
with an ETag: the browser keeps the file and revalidates it, and an unchanged file costs a `304`
of about 300 bytes.

**Compose resources are revalidated, not given a freshness lifetime.** Their paths
(`composeResources/<package>/font/...`) do not change with their content, so `immutable` is out.
A `max-age` is out too, and not only for fonts: next to them lies
`composeResources/<package>/values/strings.commonMain.cvr`, which the generated accessors read **by
byte offset and length** (`ResourceItem(..., 91, 30)`). A fresh `mani.js` over a cached `.cvr` would
read the wrong bytes. What revalidation costs was measured on the deployed instance on 2026-09-27:
on a repeat load all five resources come back `304`, in parallel, in one round trip.

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
* **Resources:** requests `50m`/`64Mi`, limits `1`/`256Mi`
* **Grace period:** `terminationGracePeriodSeconds: 30`, declared rather than left to the default —
  `Main.kt` hands the same number to kore, which refuses to start if its stages do not fit inside it
* **Deploy:** `.github/workflows/deploy.yml`, on the completion of a green `Test` run on `main`,
  plus a manual `workflow_dispatch`

Measured on the built image: 87 ms from start to the first answered request, a 213 MB image and a
13 MB binary.

### Memory

The resident set follows the **thread count**, not the live heap: the Kotlin/Native allocator keeps
a 256 KiB page per block-size class *per thread*, a thread holds it for as long as it lives, and
`Dispatchers.IO` grows threads under concurrency. No GC setting bounds that — these are pages, not
objects. `server-native/build.gradle.kts` therefore sets `binaryOption("fixedBlockPageSize", "16")`.

Release binary, 50 concurrent clients on `/health/ready` (every request is a ping into Mongo), two
runs per variant:

| | at rest | peak |
|---|---|---|
| default | 33 MB | 491–495 MB |
| `fixedBlockPageSize=16` | 19 MB | 109–141 MB |

The limit used to be `128Mi` and was raised to `256Mi` on the strength of that table: the old number
came from a measurement **at rest**, which is by definition the one measurement that cannot see the
thing a limit exists for. At `128Mi` the pod would be killed either way — many times over on the
default, and on the peak even with the option.

What the table does not say: `/health/ready` is the cheapest route there is, real requests do more
work, and fifty concurrent clients against a single replica is a burst rather than a Tuesday. The
limit is set from the highest peak observed, not the average.

### Koin without a scope per call

Koin is wired with kore's `installKoreKoin { … }` (`kore-koin`), **not** `install(Koin)`. koin-ktor's
plugin opens a Koin scope for every call; on Kotlin/Native each scope owns a stately `Lock`, which on
Linux is a `pthread_mutex_t` in a cinterop `Arena` that nothing ever frees. Every request — a probe,
a 404 — left 16 + 48 bytes of malloc behind for good: on another service of the same shape that was
154 MB of a 176 MB resident set after four days (kore B-65). The JVM and Apple targets do not leak.

`get`/`inject` in routes are unchanged; what is gone is `call.scope`, which nothing here uses. The JVM
`server` module still installs the plugin, where it costs nothing.

### Shutdown

`SIGTERM` is handled by [kore](https://github.com/youndie/kore), not by the engine alone:

```
announce  readiness goes false (5 s) — the orchestrator drops the pod from endpoints
drain     the engine stops accepting; in-flight requests finish; new ones get 503 + Connection: close
pools     the Mongo client closes — after the drain, never before
exit      inside the grace period
```

**Why this is not the engine's own `stop()`:** `EmbeddedServer.stop` runs its steps in the *opposite*
order on Kotlin/Native and on the JVM. Identical source, opposite order, and nothing says so — while
this is the build that gets deployed and the JVM is where most of the shared code is tested.

Before this, **nothing closed the Mongo pool at all** — not `ApplicationStopping`, not an `onClose`
on the Koin definition. The process simply died on `SIGTERM` with calls still inside the C driver.
The platform's reversed order did not bite only because there was nothing to order.

The order is pinned by `ShutdownOrderTest`; the transcript is printed to stdout on every stop, so a
stop can be read after the fact rather than inferred.

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
* **Escaping the static root is cut off by a check for `..`** (`WebRoutes.kt:48`), while an unknown
  path returns `index.html` — this is an SPA, and the app routes from there.
