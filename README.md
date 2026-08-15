# mani

![Static Badge](https://img.shields.io/badge/Android-green)
![Static Badge](https://img.shields.io/badge/iOS-black)
![Static Badge](https://img.shields.io/badge/Desktop-blue)
![Static Badge](https://img.shields.io/badge/Browser(WASM)-orange)
![Static Badge](https://img.shields.io/badge/Server(JVM)-red)
![Static Badge](https://img.shields.io/badge/Server(Kotlin%2FNative)-blueviolet)

[![Tests](https://github.com/youndie/mani/actions/workflows/main.yml/badge.svg)](https://github.com/youndie/mani/actions/workflows/main.yml)
[![Desktop](https://github.com/youndie/mani/actions/workflows/build_desktop.yml/badge.svg)](https://github.com/youndie/mani/actions/workflows/build_desktop.yml)
[![Android](https://github.com/youndie/mani/actions/workflows/build_android.yml/badge.svg)](https://github.com/youndie/mani/actions/workflows/build_android.yml)
[![license](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

A budget planner written end to end in Kotlin: [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)
clients for Android, iOS, desktop and the browser, a [Ktor](https://ktor.io/) server that compiles
both to the JVM and to a native Linux binary, and one shared module holding the API contract for
all of them.

![Screenshot](/composeApp/src/desktopTest/snapshots/screens_welcome_wide.png?raw=true "screenshot")

<sup>Not a copy of a screenshot — the file above *is* one of the goldens the screenshot tests
compare against. Re-recording them redraws this picture too, so it cannot quietly drift away from
the interface it advertises.</sup>

> **This is a demo project.** It exists to show what a full Kotlin Multiplatform stack looks like
> when every part of it is real — the same `@Resource` classes route requests on the server and
> build URLs on the client, the same code signs a token in both server builds. It is not a
> product: there is no email confirmation, no password recovery, no rate limiting, and passwords
> are hashed with salted SHA-256 rather than a slow KDF. The public instance is a playground —
> please do not keep anything you would miss in it.

Live instance: **[mani.kotlin.website](https://mani.kotlin.website)**

## Its own dependencies

Two of the moving parts are written for this project and used from here first:

- **[mongkn](https://github.com/youndie/mongkn)** — a MongoDB driver for Kotlin/Native, over the
  C driver via cinterop. It exists because there is no official one, and the native server build
  has to talk to the same database as the JVM one, in the same document shape.
- **[viddik](https://github.com/youndie/viddik)** — screenshot testing for Compose Multiplatform.
  The pictures in `composeApp/src/desktopTest/snapshots`, including the one at the top of this
  file, are recorded and compared by it.

Neither is a showcase bolted on for the README: the demo would not run without the first and the
redesign could not be checked without the second.

## What is where

| Module | What it is | Targets |
|---|---|---|
| `:shared` | API contract: resources, model, serializers | android, ios, jvm, wasmJs, linuxX64 |
| `:composeApp` | the app itself — one UI for every platform | android, ios, desktop, wasmJs |
| `:server-common` | server code: routes, storage ports, config, auth | jvm, linuxX64 |
| `:server` | JVM build of the server | jvm |
| `:server-native` | Kotlin/Native build — the image the demo runs | linuxX64 |
| `:androidApp`, `:iosApp` | thin platform launchers | |

The chart is not a dependency: [compose-charts](https://github.com/ehsannarmani/ComposeCharts) is
vendored under `composeApp/src/commonMain/kotlin/ir/ehsannarmani/compose_charts` and carries local
changes — the marker for the day the balance crosses zero is drawn inside its canvas, where the
plot geometry is known.

## Running locally

No source edits required — the web client talks to whatever origin served it.

1. Build the JVM server image:

   ```bash
   ./gradlew publishImageToLocalRegistry
   ```

2. Start it next to MongoDB:

   ```bash
   docker compose up -d
   ```

3. Open [http://localhost:8080/](http://localhost:8080/) and press **Try the demo** — that
   creates a sandbox account with a ready dataset, no sign-up.

The desktop and mobile clients cannot read the page they came from, so they fall back to the
address compiled into `shared/.../mani/Constants.kt`. Desktop takes an override from the
environment:

```bash
MANI_SERVER=http://localhost:8080 ./gradlew :composeApp:run
```

### Clients

```bash
./gradlew installDebug          # Android
```

```bash
./gradlew :composeApp:run       # desktop
```

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun   # browser
```

For iOS, open `iosApp/iosApp.xcodeproj` in Xcode, or use
[Fleet](https://www.jetbrains.com/help/kotlin-multiplatform-dev/fleet.html).

## Tests

```bash
./gradlew :shared:jvmTest :server-common:jvmTest :server:test :composeApp:desktopTest
```

The same set runs on every pull request.

Native server tests need a real `mongod`, because what they look for does not raise errors — an
`_id` written as a string or an amount written as text simply matches nothing:

```bash
docker run -d --name mani-mongo -p 27017:27017 mongo:8
```

```bash
./gradlew :server-native:linuxX64Test :server-native:linuxX64ReleaseTest
```

The release run is not optional. Kotlin/Native omits type-cast checks in release builds, so code
that fails with a catchable exception in debug can reach undefined behaviour in release — and the
binary that ships is the release one.

Screen layouts are checked separately, against screenshots recorded by
[viddik](https://github.com/youndie/viddik):

```bash
./gradlew :composeApp:screenshotTest
```

```bash
VIDDIK_RECORD_MODE=true ./gradlew :composeApp:screenshotTest --rerun-tasks
```

Style is checked by [ktlint](https://github.com/pinterest/ktlint) against `.editorconfig`, which
holds every deviation with a reason next to it. It runs on every pull request, and locally as:

```bash
./gradlew ktlintCheck
```

```bash
./gradlew ktlintFormat
```

**Record and verify these on Linux.** The goldens in `composeApp/src/desktopTest/snapshots` were
recorded there, and the same code on macOS renders text differently enough to differ by 1–4% of
the pixels — far past any tolerance worth keeping. That is also why this task is not part of the
pull-request workflow: a golden that only reproduces on one operating system does not belong in a
check that gates merges.

## Two server builds

The server code lives in `:server-common` and is compiled twice.

`:server` is the JVM build, on the official MongoDB driver. It is the one to run on macOS, where
the native target cannot be linked at all.

`:server-native` is the Kotlin/Native binary the demo instance runs. It talks to MongoDB through
[mongkn](https://github.com/youndie/mongkn) — our own binding over the C driver, because there is
no official Kotlin/Native one — and serves the wasm frontend from a directory instead of the
classpath, since `staticResources` does not exist outside the JVM.

Which build is answering is not a matter of trust: `GET /health` reports it live —
`{"build": "kotlin/native", "version": "1.4.2", "uptimeSeconds": …}` — and the welcome screen
prints that same response instead of a hard-coded string.

Routing, dependency injection, configuration, token issuing and password hashing are **shared**.
Both builds sign and verify JWTs with the same code, so a token issued by one is accepted by the
other, and both write documents in the same shape: `_id` as `ObjectId`, amounts as `decimal128`.

The native build is `linuxX64` only, because that is the single target mongkn publishes. It needs
the C driver on the build machine:

```bash
sudo apt-get install -y libmongoc-dev libbson-dev
```

```bash
./gradlew :server-native:linkReleaseExecutableLinuxX64 :composeApp:wasmJsBrowserDistribution
```

```bash
docker build -f server-native/Dockerfile -t mani-native .
```

Measured on that image: 87 ms from start to the first answered request, 42 MiB resident,
13 MB binary.

### Configuration

Both builds read the environment, with the same names:

| | |
|---|---|
| `PORT` | 8080 |
| `MONGO_HOST`, `MONGO_DATABASE` | `localhost`, `mani` |
| `JWT_SECRET`, `JWT_AUDIENCE`, `JWT_ISSUER`, `JWT_EXPIRATION_SECONDS` | |
| `MANI_WEB_ROOT` | directory with the wasm bundle; unset — no frontend, API only |
| `MANI_DEVELOPMENT` | `true` enables CORS |

