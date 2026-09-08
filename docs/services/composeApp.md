---
id: composeApp
title: ":composeApp — the client for four platforms"
type: service
repo_url: https://github.com/youndie/mani-kotlin-fullstack
module: ":composeApp"
tech_stack: [Compose Multiplatform, Kotlin Multiplatform, Ktor client, Koin, androidx.navigation, multiplatform-settings]
owner: unassigned
depends_on:
  - shared
  - server-native
publishes:
  - APK, desktop distribution, wasm bundle (which travels inside the server image)
---

# :composeApp — the client for four platforms

## 1. Responsibility

**The entire interface of the product, one body of code for Android, iOS, desktop and the browser.**
Screens, states, navigation, the network layer, token storage, the cache of the last known list.

There is surprisingly little platform code here — four pairs of files:

| What | Why it is platform-specific |
|---|---|
| `TokenStorageImpl` | every platform has its own secret store; in the browser it is `localStorage` |
| `authModulePlatform` | binds that `TokenStorageImpl` into the graph |
| `ServerConfigPlatform` | where the server address comes from (see §3) |
| the entry point (`main.kt`) | desktop and wasm each start their own way |

`:androidApp` and `:iosApp` are **thin launcher modules** with no logic: all they do is bring up
`App()`. `:baselineprofile` generates the Android baseline profile. None of them has a document of
its own, deliberately: there would be nothing to describe, and an empty document creates the
appearance of coverage.

## 2. API contracts

The client calls the server with **the same `@Resource` classes** the server parses paths with —
they live in [shared](shared.md). The breakdown by status code:
[endpoint-transactions](../api/endpoint-transactions.md),
[endpoint-categories](../api/endpoint-categories.md), [endpoint-auth](../api/endpoint-auth.md),
[endpoint-demo](../api/endpoint-demo.md).

## 2a. Code anchors

| File | What is there |
|---|---|
| `composeApp/src/commonMain/kotlin/io/github/youndie/mani/App.kt` | the root of the application |
| `composeApp/src/commonMain/kotlin/io/github/youndie/mani/appModule.kt` | the list of feature Koin modules |
| `composeApp/src/commonMain/kotlin/io/github/youndie/mani/navigation/` | the graph (`ManiAppNavHost.kt`), the screen list (`ManiScreen.kt`), the back-arrow rule |
| `composeApp/src/commonMain/kotlin/io/github/youndie/mani/data/Network.kt` | `HttpClient`: Resources, ContentNegotiation, the Bearer plugin |
| `composeApp/src/commonMain/kotlin/io/github/youndie/mani/data/RefreshSession.kt` | exchanging a refresh token for a new pair |
| `composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/<feature>/` | `data/` → `domain/` → `ui/`, wiring in `module.kt` |
| `composeApp/src/commonMain/kotlin/io/github/youndie/mani/Constants.kt` | the default server address |
| `composeApp/src/commonMain/kotlin/io/github/youndie/mani/uiState/UiState.kt` | the shared state interfaces: `LoadingState`, `ErrorState`, `DataState` |
| `composeApp/src/desktopTest/kotlin/io/github/youndie/mani/screenshots/Screens.kt` | the screenshot test set |
| `composeApp/src/desktopTest/snapshots/` | the goldens (recorded **on Linux**) |
| `composeApp/src/commonMain/kotlin/ir/ehsannarmani/compose_charts/` | the vendored `compose-charts` with local changes |

Every feature has the same layout: `data/` (repository, data source, cache) → `domain/` (use cases)
→ `ui/` (view model, state, components), plus `module.kt` with the wiring.

## 3. How it is built

**The server address is resolved in three steps.** In the browser the client talks to whichever
origin served the page — which is why running locally needs no source edits. Desktop takes an
override from `MANI_SERVER`. The rest fall back to the default in `Constants.kt`
(`mani.kotlin.website`).

**Token refresh is lifted out of the client configuration into a function of its own.** The body of
`refreshTokens { }` inside `HttpClient` can only be exercised by a live server; `refreshSession()`
is exercised by a mock engine, like everything else in this layer (`RefreshSessionTest`). The
`markAsRefreshTokenRequest()` marker arrives as a parameter because that method exists only inside
the `refreshTokens { }` scope, and without it the plugin would try to refresh the token for the
refresh request itself — that is, loop.

**Refresh failures are sorted by kind, and that is not pedantry:**

| Response to `/auth/refresh` | What the client does |
|---|---|
| `401` | the session is over: tokens are cleared and the `expired` event is raised |
| any other non-2xx | leaves the session **alone**: a 500 on the server does not end it |
| `2xx` | the new pair goes into storage |

Previously a `401` cleared the tokens and the body was read anyway — and a 401 has none. Parsing
failed, and what surfaced was not "the session expired" but a network failure: the "server
unreachable" screen with a countdown, from which there is no path back to the welcome screen.

**The navigation start destination is read once and is not subscribed to the token.** A subscription
rebuilds the graph, and a new graph resets navigation to its own start destination — so every
arrival of a token silently threw the screen somewhere else. Session expiry arrives as an **event**
(`TokenRepository.expired`, `replay = 0`), and the "signed in" / "signed out" transitions are made
explicitly. The details are in §1.9 of
[research-architecture](../research/research-architecture.md).

**With no network, the last known list is shown** together with the time it was taken. Rules are not
an event feed: yesterday's list is still correct today. The screen state distinguishes "showing the
cache" (`showingCacheFrom`) from "nothing to show" (`unreachable`), and those are different screens
rather than different values of one field.

## 4. Dependencies

| Kind | Name | What for |
|---|---|---|
| Module | [shared](shared.md) | resources, model, balance simulation |
| Service | [server-native](server-native.md) | the deployed instance's API |
| Library | Ktor client (`resources`, `auth`, `content-negotiation`, `logging`) | HTTP |
| Library | Koin (`koin-compose`) | DI; screens attach their modules with `rememberKoinModules` |
| Library | `androidx.navigation` (Compose) | the screen graph |
| Library | `multiplatform-settings` | tokens and the list cache |
| Library | `kotlinx.collections.immutable` | screen states stay stable for Compose |
| Library | [viddik](https://github.com/youndie/viddik) | screenshot tests |
| Vendored code | `compose-charts` | the chart; the local change draws the run-out marker inside its canvas |

## 5. Infrastructure and deploy

There is no deployment of its own. The wasm bundle is built during the server deploy
(`:composeApp:wasmJsBrowserDistribution`), compressed while the image is built, and travels inside
the [server-native](server-native.md) image. Android and desktop are built by separate workflows
(`.github/workflows/build_android.yml`, `build_desktop.yml`).

## 6. Local setup

```bash
./gradlew :composeApp:run
```

```bash
MANI_SERVER=http://localhost:8080 ./gradlew :composeApp:run
```

```bash
./gradlew installDebug
```

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

For iOS, open `iosApp/iosApp.xcodeproj` in Xcode.

Tests:

```bash
./gradlew :composeApp:desktopTest
```

```bash
./gradlew :composeApp:viddikVerify
```

```bash
./gradlew :composeApp:viddikRecord
```

## 7. Configuration

| Key | Where it applies | Description |
|---|---|---|
| `MANI_SERVER` | desktop | overrides the server address |
| — | browser | the address comes from the page's origin; there is nothing to configure |
| — | Android, iOS | the default from `Constants.kt` |

## 8. Quirks

* **Screenshot tests do not run on pull requests, and that is a decision.** The goldens were
  recorded on Linux; the same code on macOS renders text differently — 1–4 % of the pixels, far past
  any tolerance worth keeping. A golden that only reproduces on one operating system does not belong
  in a check that gates merges.
* **The screenshot in `README.md` is a golden itself**, not a copy of one. Re-recording the goldens
  redraws the README picture too, so it cannot quietly drift away from the interface.
* **`:composeApp:wasmJsTest` requires ChromeHeadless.** CI has the browser; on a machine without it
  the task fails at launch even though the wasm compilation completes.
* **A local `.editorconfig` rather than the shared one.** The filename rule is disabled (files are
  grouped by meaning: `module.kt` is one feature's DI) and inherited star imports are allowed — 80
  of them across 35 files. The reason is recorded in `gradle.properties`, under the
  `sborka.editorconfig` key.
* **`TransactionUiState.categoriesExpanded` returns a constant `true`.** The property exists, but
  there is no decision behind it — unlike the neighbouring `periodsExpanded`, which compares the
  list against the default.
