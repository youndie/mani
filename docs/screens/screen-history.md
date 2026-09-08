---
id: screen-history
title: History — the month's ledger
type: client_screen
platform: [android, ios, desktop, web]
status: active
entry:
  all: "the ManiScreen.History route"
parent_feature: feature-transactions
calls_api:
  - endpoint-transactions
source: composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/ui/component/
---

# Screen: history

The same ledger as on the main screen, but with the current month's total instead of the forecast
ahead.

## 0a. Code anchors

| What | File |
|---|---|
| View model | `composeApp/.../feature/transaction/ui/TransactionsViewModel.kt` |
| State | `composeApp/.../feature/transaction/ui/model/TransactionListUiState.kt` |
| Composition | `composeApp/.../feature/transaction/ui/component/TransactionsListComponent.kt` |
| The rule row | `composeApp/.../feature/transaction/ui/component/TransactionItem.kt` |
| Empty state | `composeApp/.../feature/transaction/ui/component/TransactionsEmpty.kt` |
| Grouping by day | `composeApp/.../feature/transaction/ui/model/TransactionsByDays.kt` |

## 0. Entry point and visibility

* **Entry point:** "History" from the main screen.
* **Shown to:** authenticated users only. The bar's title is `History` and there is a back arrow
  (`History` is not a root screen).

## 1. Screen states

`TransactionListUiState` implements the shared `CommonUiState<TransactionsByDays>` — `load()`,
`showError()`, `showData()` instead of three independent flags:

* `loading` — loading;
* `data` empty — the empty state;
* `data` populated — the ledger by day; `dayBalances` is the same end-of-day balance as on the main
  screen;
* `monthTitle` / `monthChange` / `balanceToday` — "August so far": how much has accumulated this
  month and what today's balance is;
* `selectedTransactions` + `showDeleteDialog` — selection and deletion;
* `showingCacheFrom != null` — the cache is shown, with a timestamp;
* `unreachable != null` — there is nothing to show;
* `errorMessage` — other failures.

## 2. API integration

| Call | Contract | Endpoint document |
|---|---|---|
| `GET /transactions` | `TransactionResource` | [endpoint-transactions](../api/endpoint-transactions.md) |
| `DELETE /transactions/{id}` | `TransactionResource.ById` | [endpoint-transactions](../api/endpoint-transactions.md) |

## 3. Initialisation

No input parameters; the list is requested on open. The responses are handled exactly as in
[screen-main](screen-main.md) §3, including both "no network" branches.

## 4. UI elements

### 4.1. The month's total

`monthTitle` + `monthChange` + `balanceToday`. This is the only thing that distinguishes the screen
from the main ledger: there one looks ahead, here at what has already happened.

### 4.2. The ledger by day

The same grouping and the same `dayBalances` as on the main screen — both computed over the whole
simulation.

### 4.3. Selection and deletion

As on the main screen: a long press turns on selection, and deletion goes through a confirmation.

## 5. Navigation

* a rule ──▶ `screen-transaction-form` (edit)
* "back" ──▶ `screen-main`
