---
id: screen-auth-form
title: Credentials form (Login and Signup)
type: client_screen
platform: [android, ios, desktop, web]
status: active
entry:
  all: "the ManiScreen.Login and ManiScreen.Signup routes"
parent_feature: feature-auth
calls_api:
  - endpoint-auth
  - endpoint-demo
source: composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/auth/ui/
---

# Screen: credentials form

One document for two routes. `Login` and `Signup` are **one composition and one view model**: they
differ in the use case bound into the Koin graph, the heading, and the button's caption. Splitting
them into two documents would mean describing the same file twice.

| Route | Use case in the graph | Heading | Button |
|---|---|---|---|
| `Login` | `LoginUseCase` | `Mani` | `Login` |
| `Signup` | `SignupUseCase` | `Sign up` | `Create` |

## 0a. Code anchors

| What | File |
|---|---|
| View model | `composeApp/.../feature/auth/ui/AuthViewModel.kt` |
| View model state | `composeApp/.../feature/auth/ui/model/AuthUiState.kt` |
| Form state | `composeApp/.../feature/auth/ui/model/AuthComponentUiState.kt` |
| The shared composition | `composeApp/.../feature/auth/ui/component/AuthComponentImpl.kt` |
| The route wrappers | `.../component/LoginComponent.kt`, `.../component/SignupComponent.kt` |
| Use cases | `composeApp/.../feature/auth/domain/LoginUseCase.kt`, `SignupUseCase.kt` |

## 0. Entry point and visibility

* **Entry point:** the "Sign in" and "Sign up" links on the [welcome screen](screen-welcome.md);
  Signup carries a link to Login.
* **Shown to:** unauthenticated visitors.
* The top bar is **disabled** on Login (`appBarState.disable()`), and Signup slides in from below.

## 1. Screen states

The fields of `AuthUiState`:

* **Empty form** — two fields, the button enabled.
* **`loading = true`** — the request is away: the button is in its loading state.
* **`demoLoading = true`** — "Try the demo" was pressed (it is here on Login too).
* **`errorMessage != null`** — the text under the fields, behind the test tag `errorMessage`.
* **`success = true`** — move on.

The form does **no** local validation: it all lives on the server, and the person sees the server's
text.

## 2. API integration

| Call | Contract | Endpoint document |
|---|---|---|
| `POST /auth` (Login) | `AuthResource` | [endpoint-auth](../api/endpoint-auth.md) |
| `POST /users` (Signup) | `UserResource` | [endpoint-auth](../api/endpoint-auth.md) |
| `POST /demo` (the demo button on Login) | `DemoResource` | [endpoint-demo](../api/endpoint-demo.md) |

## 3. Initialisation

No input parameters, and no requests on open.

## 4. UI elements

### 4.1. The `username` and `password` fields

Typing clears `errorMessage`: an old refusal should not hang over input that has already been
corrected.

### 4.2. The action button

**Login:**

| Case | Handling | Screen state |
|---|---|---|
| `200` | tokens into storage | `success = true` |
| `404` | `User not found or invalid password` into `errorMessage` (`UserNotFoundException`) | `loading = false` |
| anything else, network failure included | `Network Error` — one text for every remaining case | `loading = false` |

**Signup:**

| Case | Handling | Screen state |
|---|---|---|
| `201` | — | `success = true` → move to Login |
| `400` | **the server's text** into `errorMessage`; if it is blank, `Sign up refused` | `loading = false` |
| `500` | `Server error` | `loading = false` |

The `400` text is taken from the server rather than substituted on the client: any `400` used to
read "User already exist", so somebody who had sent a short password was told the name was taken.

### 4.3. "Try the demo" (Login only)

The same path as on the welcome screen, captioned "your own sandbox — no account, no password".

## 5. Navigation

* Login succeeds ──▶ `screen-main` (the stack is cleared)
* Signup succeeds ──▶ Login, **the same form** — there is no automatic sign-in, the data is typed
  again
* "Sign up" from Login ──▶ Signup
* "Sign in" from Signup ──▶ Login
* "Try the demo" ──▶ `screen-main`
