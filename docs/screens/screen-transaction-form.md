---
id: screen-transaction-form
title: Rule form (create and edit)
type: client_screen
platform: [android, ios, desktop, web]
status: active
entry:
  all: "the ManiScreen.Add route (create) and TransactionRoute(id) (edit)"
parent_feature: feature-transactions
calls_api:
  - endpoint-transactions
  - endpoint-categories
source: composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/ui/
---

# Screen: rule form

One document for two routes: create and edit are one composition and one base view model, differing
in whether the original record is loaded and in the call made on save.

| Route | View model | Heading | Request on save |
|---|---|---|---|
| `Add` | `AddTransactionViewModel` | `New rule` | `POST /transactions` |
| `TransactionRoute(id)` | `EditTransactionViewModel` | `Edit rule` | `PATCH /transactions/{id}` |

## 0a. Code anchors

| What | File |
|---|---|
| The shared view model | `composeApp/.../feature/transaction/ui/BaseTransactionViewModel.kt` |
| Create / edit | `.../ui/AddTransactionViewModel.kt`, `.../ui/EditTransactionViewModel.kt` |
| State | `composeApp/.../feature/transaction/ui/model/TransactionUiState.kt` |
| Composition | `composeApp/.../feature/transaction/ui/component/TransactionComponent.kt` |
| Date picking | `.../ui/component/TransactionDatePicker.kt` |
| Amount formatting while typing | `.../ui/utils/CurrencyVisualTransformation.kt` |
| Period captions | `.../ui/model/PeriodStringResource.kt` |
| The server's rules | `server-common/.../feature/transaction/Rules.kt` |

## 0. Entry point and visibility

* **Entry point:** "+" on the main screen (create); tapping a rule in the main or history ledger
  (edit).
* **Shown to:** authenticated users only.

**Input parameters (edit):**

| Parameter | Type | Where from |
|---|---|---|
| `id` | `String` | `TransactionRoute` in the navigation arguments |

## 1. Screen states

The fields of `TransactionUiState`:

* `amount`, `income`, `period`, `comment`, `date`, `until`, `category` — the input itself;
* `periods` — the four from the design (`OneTime`, `TwoWeek`, `Month`, `Year`), the rest behind
  "More"; `periodsExpanded` means the list differs from the default;
* `valid` — the amount has no error, is not empty, and a date has been chosen;
* `amountError` — under the field itself: `this is not an amount` or `an amount is required` (zero);
* `futureInformation` — what this rule will do in the future;
* `runsOutShift` — **how far the run-out day moves**, with a `worse` flag;
* `loading` — the save is in flight;
* `errorMessage` — the server's refusal;
* `success` — saved, the screen closes;
* `edit` — edit mode.

## 2. API integration

| Call | Contract | Endpoint document |
|---|---|---|
| `POST /transactions` | `TransactionResource` | [endpoint-transactions](../api/endpoint-transactions.md) |
| `PATCH /transactions/{id}` | `TransactionResource.ById` | [endpoint-transactions](../api/endpoint-transactions.md) |
| `GET /categories`, `POST /categories`, `DELETE /categories/{id}` | `CategoryResource` | [endpoint-categories](../api/endpoint-categories.md) |

## 3. Initialisation

| Call | When | Result |
|---|---|---|
| reading the record from the list already loaded | edit | fills the form through `TransactionUiState(transaction, currency)` |
| `GET /categories` | always | the category chips |
| `GetCurrentCurrencyUseCase` | always | amount formatting — **from local `Settings`, not from the network** ([endpoint-currencies](../api/endpoint-currencies.md)) |

There is no "one rule by id" route — the record is taken from the list.

## 4. UI elements

### 4.1. Amount

The input is formatted as it is typed (`CurrencyVisualTransformation`). The error is shown **under
the field** rather than in a general message at the bottom: a disabled button with no explanation
left a person guessing what was expected of them. Zero is refused here too — a rule for zero moves
nothing in the forecast.

### 4.2. Income or expense

Expense by default: those are entered more often. The sign comes from **this flag**, not from the
amount typed.

### 4.3. Period

Four chips from the design plus "More" with the rest: `Day`, `Week`, `ThreeMonth`, `HalfYear`.

### 4.4. The `date` and `until` fields

`date` is required: a rule without one does not expand into a calendar, and the "Create" button with
only an amount typed invited people to save something that cannot be saved. `until` is optional; the
server does not accept a pair where it precedes the start date.

### 4.5. Category

Chips; created and deleted right here. The name must be non-empty and at most 64 characters — the
server checks that.

### 4.6. The shift preview

`runsOutShift` is what makes this different from an ordinary record form: it says **how much sooner
the money will run out**, before anything is saved. `worse` separates "sooner" from "later": two
different pieces of news, and only the first should be marked in red. Income carries no worsening
mark.

### 4.7. The save button

Enabled when `valid`.

| Case | Handling | Screen state |
|---|---|---|
| `201` / `200` | the shared `StateFlow` already carries the change; nothing is re-fetched | `success = true`, the screen closes |
| `400` | the server's text into `errorMessage` | `loading = false` |
| anything else | the failure text | `loading = false` |

The change is written into the repository's `StateFlow` **before** the request and rolled back if it
throws, so a failed save visibly reverts rather than leaving a stale row behind.

## 5. Navigation

* saved ──▶ back (`popBackStack`), to the screen it was opened from
* "back" ──▶ the same, without saving
