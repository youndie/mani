---
id: screen-welcome
title: Welcome screen
type: client_screen
platform: [android, ios, desktop, web]
status: active
entry:
  all: "the ManiScreen.Welcome route; the graph's start destination when there is no refresh token"
parent_feature: feature-auth
calls_api:
  - endpoint-auth
  - endpoint-demo
  - endpoint-health
source: composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/welcome/
---

# Screen: welcome

## 0a. Code anchors

| What | File |
|---|---|
| View model and state | `composeApp/.../feature/welcome/WelcomeViewModel.kt` (`WelcomeUiState` is in the same file) |
| Composition | `composeApp/.../feature/welcome/WelcomeComponent.kt` |
| Entering the sandbox | `composeApp/.../feature/auth/domain/StartDemoUseCase.kt` |
| The build string | `composeApp/.../feature/health/domain/GetHealthUseCase.kt` |
| Golden | `composeApp/src/desktopTest/snapshots/screens_welcome_wide.png` |

## 0. Entry point and visibility

* **Entry point:** the navigation graph's start destination when storage holds no refresh token
  (`ManiAppNavHost.kt:52`). Signing out and session expiry also lead here.
* **Shown to:** unauthenticated visitors only.

## 1. Screen states

The fields of `WelcomeUiState`:

* **Ordinary** — `loading = false`: the heading, a sample forecast as a chart, the "Try the demo"
  button, and links to sign in and sign up.
* **`loading = true`** — the sandbox is being created: the button is in its loading state.
* **`errorMessage != null`** — the sandbox could not be created; the text is taken from the server
  as is (for example `The demo is full right now, try again later`).
* **`server`** — a string like `ktor · kotlin/native · 1.4.2`; empty until `/health` answers.
* **`success = true`** — the visitor is in, and the screen moves on to the main one.

## 2. API integration

| Call | Contract | Endpoint document |
|---|---|---|
| `GET /health` | `HealthResource` | [endpoint-health](../api/endpoint-health.md) |
| `POST /demo` | `DemoResource` | [endpoint-demo](../api/endpoint-demo.md) |

## 3. Initialisation

No input parameters.

| Call | When | Result |
|---|---|---|
| `GET /health` | when the view model is created | fills in `server` |

A failing `/health` is **silent**: the build string is an ornament on the welcome screen, and its
absence must not get in the way of entering the demo.

## 4. UI elements

### 4.1. The sample forecast (chart)

Drawn from the `DemoSeed` demo data set — **before anyone has signed in** — by the simulation from
[shared](../services/shared.md). The reference day is pinned rather than taken from the clock:
otherwise the chart's range would drift every day, taking the golden screenshot with it
(`TransactionsOperations.kt:29`).

### 4.2. "Try the demo"

The main path of the welcome screen: the visitor has nothing to type.

| Case | Handling | Screen state |
|---|---|---|
| `201` | tokens into storage | `success = true` → move to the main screen |
| `503` / `500` / network | the server's text into `errorMessage` | `loading = false` |

### 4.3. The build string

`ktor · <build> · <version>` — whatever `/health` returns, not a hard-coded string. This is where a
visitor can see that the native binary served the request.

### 4.4. Sign in and sign up

Text links to [screen-auth-form](screen-auth-form.md).

## 5. Navigation

* "Try the demo" succeeds ──▶ `screen-main` (the welcome screen leaves the stack and the graph's
  start destination moves)
* "Sign in" ──▶ `screen-auth-form` (Login)
* "Sign up" ──▶ `screen-auth-form` (Signup)

There is no back arrow here: `Welcome` is a root screen (`ManiScreen.kt:20`).
