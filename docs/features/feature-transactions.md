---
id: feature-transactions
title: Budget rules and the forecast
type: feature
status: active
owner: unassigned
involved_services:
  - shared
  - server-common
  - server
  - server-native
  - composeApp
client_entries:
  - screen-main
  - screen-history
  - screen-transaction-form
api:
  - endpoint-transactions
tags: [core, forecast]
---

# Budget rules and the forecast

## 1. Overview

This is the core of the product. A person creates **rules** — "salary, on the 1st, every month",
"subscription, on the 15th, every month", "tickets, 3 March, one-off" — and the app expands them
into a calendar and answers one question: **when will the money run out**.

Everything else follows from that. The ledger is not a spending history but the future mixed in with
the past, carrying the balance at the end of each day. The chart is the same simulation drawn as a
line, with a marker on the day the balance crosses zero. The rule form shows not only fields but
**how far this rule moves the day you run out** — before the rule is saved.

The product speaks of "rules", the code of `Transaction`. A one-off expense is the special case
here: period `OneTime`.

## 2. Business rules

* The amount is strictly greater than zero. The sign comes from the `income` flag, not from the
  amount: a negative amount with `income = false` would add a **plus** to the forecast.
* Zero is not accepted: a rule for zero does not move the forecast, that is, it does not do the one
  thing it is created for.
* The end date cannot be earlier than the start date — such a rule would expand into no day at all
  and from outside would look like a record that vanished.
* A comment is at most 200 characters; a category name at most 64, and not empty.
* A rule is seen and edited **only by its owner**. Somebody else's and non-existent both answer
  `403`.
* On an edit the id is taken **from the path**, not from the body.
* The category is stored in the user document; in the response it is substituted whole. Not found —
  `Category.default` is substituted.
* Validation sits on the server, not only in the form: behind the form is open HTTP.

## 3. Flow

```
screen ──GET /transactions──▶ list ──▶ cache (Settings) ──▶ simulation (:shared) ──▶ ledger, chart, hero
form   ──POST /transactions──▶ 201 ──▶ the shared StateFlow already carries it (optimistically)
form   ──PATCH /transactions/{id}──▶ 200
ledger ──DELETE /transactions/{id}──▶ 200

no network ──▶ is there a cache? ──yes─▶ the ledger, marked "taken at ..."
                                 ──no──▶ the "server unreachable" screen, with a cause and a retry
```

**Writes do not reload the list.** `BaseFlowRepository` puts the change into a shared `StateFlow`
*before* the network call and rolls it back if the call throws
(`composeApp/.../feature/transaction/FlowRepository.kt`). Every screen observing that flow updates
at once, and nothing re-fetches. Two consequences worth knowing: a created rule appears with the
client's temporary id until the server's answer replaces it, and a failed write reverts visibly.

The list is mutated only through `MutableStateFlow.update`, never `data.value += x`: the latter is a
read, an add and a write in three steps, and concurrent calls overwrite each other. Deleting a
selection runs four coroutines at once, so it was precisely deletions that were lost — rows came
back into the list until the next load.

The simulation runs **on the client**, with code from [shared](../services/shared.md): one and the
same `simulate()`/`toChartInternal()` serves the main screen, the history, the rule form and the
chart on the welcome screen — before anyone has signed in.

### The chart

There is **no** `/chart` route on the server: `ChartResponse` is declared in `:shared` but assembled
on the client out of the list already loaded (`GetChartUseCase` → `toChartInternal`). That is why
the chart is also drawn on the welcome screen, where nobody has signed in and there is nothing to
request.

| What | File |
|---|---|
| Use case | `composeApp/.../feature/chart/GetChartUseCase.kt` |
| View model | `composeApp/.../feature/chart/ChartViewModel.kt` |
| Render model | `composeApp/.../feature/chart/ui/model/ChartUi.kt` |
| Component | `composeApp/.../feature/chart/ui/ChartImpl.kt` |
| The computation | `shared/.../feature/transaction/TransactionsOperations.kt` |

Two decisions that are not obvious from the code:

* **An empty chart is not drawn at all.** The flow is filtered on `chart.days.isNotEmpty()`: until
  the first load the screen shows a loading state rather than empty axes.
* **The left edge of the horizon is trimmed in the view model, not in the computation.** The
  computation is shared with the forecast, while "show one month back" is this screen's decision
  (`defaultMinDate`).

## 4. Code anchors

| Service | Code |
|---|---|
| shared | `shared/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/Transaction.kt` — the model |
| shared | `shared/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/TransactionsOperations.kt` — expansion into a calendar and the balance simulation |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/TransactionRouter.kt` — the four routes and the ownership check |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/Rules.kt` — the validation rules |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/data/TransactionRepository.kt` — the port and `TransactionRecord` |
| server | `server/src/main/kotlin/io/github/youndie/mani/feature/transaction/data/` |
| server-native | `server-native/src/linuxX64Main/kotlin/io/github/youndie/mani/feature/transaction/data/` |
| composeApp | `composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/` — repository, cache, use cases, screens |
| composeApp | `composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/main/` — the main screen and the forecast hero |

## 5. Scenarios (BDD)

### Scenario: a rule is created and immediately visible

* **Given:** a signed-in user.
* **When:** `POST /transactions` with an acceptable rule.
* **Then:** `201`, and the body carries the rule with a server-issued `id` and a substituted
  category.
* **And:** `GET /transactions` returns it in the list.
* **Automated:** `ManiApiTest`

### Scenario: a rule is changed and deleted

* **Given:** an existing rule.
* **When:** `PATCH /transactions/{id}`, then `DELETE /transactions/{id}`.
* **Then:** `200` for both; after the delete the record is not in the list.
* **Automated:** `MongknStorageTest`

### Scenario: a rule keeps its category

* **Given:** the user has a category.
* **When:** a rule is created with it and read back.
* **Then:** the response carries the same category, not `Default`.
* **Automated:** `ManiApiTest`

### Scenario: the product refuses a rule it cannot honour

* **Given:** a signed-in user.
* **When:** a rule with amount `0`, or a negative amount, or `until` earlier than `date`, or a
  comment longer than 200 characters.
* **Then:** `400` with a text naming what to fix: `Amount must be greater than zero`,
  `The end date cannot be earlier than the start date`,
  `Comment must be at most 200 characters long`.
* **Automated:** `ManiApiTest`

### Scenario: a stranger's rule cannot be overwritten through the `id` in the body

* **Given:** two users, each with a rule of their own.
* **When:** the first sends `PATCH /transactions/<their own>` with the other's `id` in the body.
* **Then:** the `id` from the body is ignored and their own record is edited; the other's is
  untouched and does not change owner.
* **Automated:** `OwnershipTest`

### Scenario: reaching for a foreign or non-existent rule

* **Given:** an id that does not belong to the user, or does not exist at all.
* **When:** `PATCH` or `DELETE` on it.
* **Then:** `403` — **the same** answer in both cases, so that the code cannot be used to learn
  which ids are taken.
* **Automated:** `OwnershipTest`

### Scenario: a malformed id in the path

* **Given:** an `{id}` that does not parse as an `ObjectId`.
* **When:** any route carrying that path.
* **Then:** `400` with the body `Malformed request` — not `500`.
* **Automated:** `MalformedRequestTest`

### Scenario: an unparseable body

* **Given:** a body that does not parse into a `Transaction`.
* **When:** `POST /transactions`.
* **Then:** `400` with the body `Malformed request`.
* **Automated:** `MalformedRequestTest`

### Scenario: no network, but the list is known

* **Given:** the rule list has been loaded before and the cache is intact.
* **When:** the request for the list does not go through.
* **Then:** the last known list is shown **with a mark saying when it was taken**
  (`showingCacheFrom`), rather than an empty screen.
* **Automated:** `TransactionsCacheTest`

### Scenario: no network and no cache

* **Given:** the cache is empty or corrupt.
* **When:** the request for the list does not go through.
* **Then:** the "Can't reach the server" screen is shown, with a machine-readable cause (code, host,
  time) and a countdown to the automatic retry.
* **Automated:** `TransactionsCacheTest`

### Scenario: the cache says when it was taken

* **Given:** the data came from the cache.
* **When:** the screen state is assembled.
* **Then:** `showingCacheFrom` is filled in with the time it was taken.
* **Automated:** `TransactionsViewModelCacheTest`

### Scenario: the form shows how far the rule moves the run-out day

* **Given:** the new-rule form is open and the app already has rules.
* **When:** an amount and a date for an expense are entered.
* **Then:** below the form it says how much sooner the money runs out, marked as a worsening
  (`RunsOutShift.worse`).
* **And:** for income there is no worsening mark.
* **Automated:** `TransactionViewModelTest`

## 6. Out of scope

* Categories as a subject of their own — [feature-categories](feature-categories.md).
* Currency — [endpoint-currencies](../api/endpoint-currencies.md): the route exists, the client does
  not call it.
* The vendored `compose-charts` as a library — described in
  [composeApp](../services/composeApp.md); what draws the chart is in §3 above.
* The ledger filters (`FiltersState`) — part of the main screen, covered in
  [screen-main](../screens/screen-main.md).

## 7. Quirks

* **`POST /transactions` can answer `404`.** The "created it and could not find it by its own id"
  branch exists (`TransactionRouter.kt:44`), cannot be reproduced by any ordinary path, and is not
  covered by a test. From outside it looks like "nothing happened", even though the record was
  created.
* **A deleted category looks like "Default" rather than like an error.** `toTransaction()`
  substitutes `Category.default` when the `categoryId` is not among the owner's categories. A silent
  degradation; there is no decision about it in writing — open question 1 in
  [research-architecture](../research/research-architecture.md).
* **There is no "one rule by id" route.** The edit screen takes the record out of the list already
  loaded; on a cold open from a link — a real case in the browser — the whole list is fetched first.
* **The server knows the length limits, the client does not.** `MAX_COMMENT_LENGTH = 200` and
  `MAX_CATEGORY_NAME_LENGTH = 64` are private constants in `Rules.kt` and are not part of the
  contract. The form does not stop anyone typing more; a person learns the limit from the refusal
  text.
* **The list arrives whole, without paging.** For a dozen rules that holds, and the cache is built
  on it.
