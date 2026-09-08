---
id: screen-main
title: Main screen — the forecast and the ledger
type: client_screen
platform: [android, ios, desktop, web]
status: active
entry:
  all: "the ManiScreen.Main route; the graph's start destination when a refresh token exists"
parent_feature: feature-transactions
calls_api:
  - endpoint-transactions
  - endpoint-categories
  - endpoint-demo
source: composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/main/
---

# Screen: main

This is what everything else was written for: at the top, the answer to "when will the money run
out"; below it the chart; then the ledger of rules with the balance at the end of each day.

## 0a. Code anchors

| What | File |
|---|---|
| View model | `composeApp/.../feature/main/MainViewModel.kt` |
| Screen state | `composeApp/.../feature/main/ui/MainUiState.kt` |
| Hero state | `composeApp/.../feature/main/ui/ForecastUiState.kt` |
| Composition and filters | `composeApp/.../feature/main/ui/MainComponent.kt` (`FiltersState` is in the same file, line 225) |
| The hero | `composeApp/.../feature/main/ui/ForecastHero.kt` |
| "Server unreachable" | `composeApp/.../feature/main/ui/ServerUnreachable.kt` |
| The retry schedule | `composeApp/.../feature/main/ui/RetrySchedule.kt` |
| The simulation | `shared/.../feature/transaction/TransactionsOperations.kt` |

## 0. Entry point and visibility

* **Entry point:** the graph's start destination when a refresh token exists; a successful sign-in,
  registration→sign-in and the demo all lead here too.
* **Shown to:** authenticated users only.
* There is no back arrow: `Main` is a root screen. There used to be one and it lied — behind it was
  the welcome screen, which can no longer be returned to.

## 1. Screen states

The fields of `MainUiState`; the hero is a hierarchy of its own, `ForecastUiState`, rather than a
set of optional fields:

**The hero (`forecast`):**

| State | What it shows |
|---|---|
| `Loading` | the data has not arrived yet |
| `Empty` | there are no rules — nothing to forecast |
| `RunsOut` | "the money runs out on ...", how many days are left, today's balance, the lowest point |
| `Steady` | the balance does not go negative inside the horizon — the balance itself is shown |

`Steady` is a separate state rather than `runsOutOn = null`: what needs showing is different.

**The screen as a whole:**

* `loading` — loading is in progress (the ledger under a shimmer);
* `transactions` — the ledger, grouped by day; `dayBalances` — the balance at the end of each day,
  computed **over the whole simulation** rather than over the filtered ledger;
* `selectedTransactions` + `showDeleteDialog` — selection mode and the delete confirmation;
* `showProfile` — the profile popup (sign-out lives in it);
* `filtersState` — the filters (see §4.3);
* `showingCacheFrom != null` — there is no network and what is shown is the last known list, taken
  at that time;
* `unreachable != null` — the server did not answer and there is nothing to show;
* `errorMessage` — other failures, as a snackbar.

The last two are **different** screens, not different values of one field.

## 2. API integration

| Call | Contract | Endpoint document |
|---|---|---|
| `GET /transactions` | `TransactionResource` | [endpoint-transactions](../api/endpoint-transactions.md) |
| `DELETE /transactions/{id}` | `TransactionResource.ById` | [endpoint-transactions](../api/endpoint-transactions.md) |
| `GET /categories` | `CategoryResource` | [endpoint-categories](../api/endpoint-categories.md) (the category filter) |
| `POST /demo/seed` | `DemoResource.Seed` | [endpoint-demo](../api/endpoint-demo.md) (the button on the empty screen) |

## 3. Initialisation

No input parameters.

| Call | When | Result |
|---|---|---|
| `GET /transactions` | once, when the view model is created | the ledger, the chart, the hero |
| `GET /categories` | on open | populates the filter |

There is no reload on return to the screen: `ON_START` only adds the profile action to the app bar
(`MainComponent.kt:109`). After the first load the screen follows the repository's `StateFlow`, and
a re-request happens only through `RetrySchedule` — the countdown or the "Try again" button.

The currency **does not go to the network**: `GetCurrentCurrencyUseCase` reads it out of local
`Settings`, where the default always wins — see
[endpoint-currencies](../api/endpoint-currencies.md).

| Case | Handling | Screen state |
|---|---|---|
| `200`, records present | simulation, grouping by day | ledger + `RunsOut`/`Steady` |
| `200`, empty | — | empty ledger + `Empty` |
| failure, cache present | the cache is shown | `showingCacheFrom` filled in |
| failure, no cache | — | `unreachable` filled in, with a cause and a retry |
| `401`, could not be renewed | the `expired` event | the graph moves to the welcome screen |

## 4. UI elements

### 4.1. The forecast hero

The screen's headline is **the date the money runs out**. Before the redesign this was five lines of
monospaced text in a single `AnnotatedString`, with the important part last, looking like a line of
debug output.

`runsOutOn` is a day without a year: the year is obvious from "in so many days", and space in a
headline is expensive.

### 4.2. The chart

The same vendored `compose-charts` as on the welcome screen. The marker for the day the balance
crosses zero is drawn **inside the library's canvas**, where the plot geometry is known — hence the
local changes in the vendored code.

### 4.3. The filters

`FiltersState`: `upcoming` (the `Upcoming` / `Past` chips), `category`, plus `loading` for the
shimmer.

Note: the filter changes the **ledger** but not `dayBalances` — the balance is computed over the
whole simulation. Otherwise hiding some rules would change a balance that it does not change.

### 4.4. The ledger

Grouped by day, each day carrying the balance at its end. A long press turns on selection; the
selected entries are deleted through a confirmation (`showDeleteDialog`).

### 4.5. The empty screen

There are no rules yet — the hero is in the `Empty` state, and populating the account with the demo
data set is offered (`MainViewModel.onFillWithDemoDataClicked` → `POST /demo/seed`). There is no
need to create a second account just to see what a populated app looks like. A failure shows up in
`errorMessage`.

### 4.6. The profile menu

`showProfile`; sign-out lives in it. Signing out moves to the welcome screen by an **explicit
transition** (`MainViewModel.loggedOut`) rather than by the graph resetting when the token
disappears, as it used to.

### 4.7. "Server unreachable"

Shown in place of the content when there is neither anything fresh nor anything stored. It carries a
machine-readable cause (`HTTP 503 · api.mani.kotlin.website · 11:42:07`), a "Try again" button and
a countdown to the automatic retry. A separate line says "Your rules are safe" — without it "could
not load" reads as "the data is gone", when all that went is the connection.

## 5. Navigation

* a rule in the ledger ──▶ `screen-transaction-form` (edit, `TransactionRoute(id)`)
* "+" ──▶ `screen-transaction-form` (create)
* "History" ──▶ `screen-history`
* sign out ──▶ `screen-welcome` (the stack is cleared)
* the session expired ──▶ `screen-welcome`
