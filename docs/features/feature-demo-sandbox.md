---
id: feature-demo-sandbox
title: The demo sandbox
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
  - screen-welcome
  - screen-main
api:
  - endpoint-demo
tags: [demo, onboarding]
---

# The demo sandbox

## 1. Overview

A visitor to the welcome screen presses **"Try the demo"** and lands in a populated app: seven
rules, five categories, two months of history behind them, and a forecast in which the money really
does run out. They typed neither a name nor a password: the server created a **throwaway user** for
them and returned tokens.

This is not a shared account, and the difference matters. Shared means anyone edits and deletes
anyone else's data; that is exactly how the demo once arrived at entries like "mani minuz −3 $ every
day" and the line "no zero events" instead of a forecast. Here every visitor gets a sandbox of their
own, which disappears after a day.

The same data set is available to someone with a real account: an empty main screen offers to
populate it — there is no need to create a second account just to see what a populated app looks
like.

## 2. Business rules

* A sandbox is an ordinary user whose name starts with `demo-`. Registration **forbids** such names:
  by taking the prefix by hand one could create an account that the sweep carries off a day later.
* The sandbox credentials come from a cryptographic source, not from a counter.
* It lives **one day** (`SANDBOX_LIFETIME_SECONDS`). Nobody looks at a sandbox for longer.
* At most 500 live at once (`MAX_LIVE_SANDBOXES`). The ceiling cuts off a flood, not a visitor:
  live sandboxes on the welcome screen number in the single digits.
* No room — `503` with a text for the welcome screen, not `500`: the server is fine.
* The age is taken **from the identifier itself**: the first four bytes of an `ObjectId` are Unix
  seconds. No separate date field is introduced.
* The sweep runs **when a new sandbox is created**; neither build has a scheduler.
* The sweep deletes the transactions first and the owner second. The reverse order, interrupted
  halfway, would leave transactions with no user — nothing left to find them by and nothing to
  delete them with.
* A failing sweep does **not** cost the visitor their way in: the rubbish waits for the next call.
* The seed is unrolled into transactions by exactly one piece of code (`DemoSeed.transactions`),
  shared between the server and the welcome screen.

## 3. Flow

```
welcome ──POST /demo──▶ sweep() ──▶ is there room?
                                     ├─no───▶ 503 "The demo is full right now, try again later"
                                     └─yes──▶ demo-<random> created
                                              ├─ the seed's categories (first: a rule needs their id)
                                              ├─ the seed's rules
                                              └─ 201 + Tokens ──▶ main screen

empty screen ──POST /demo/seed──▶ the same seed into YOUR OWN account ──▶ 201
```

The categories are created **first**: a seed rule carries only a category name, while a transaction
needs an id, and the id appears at creation time.

## 4. Code anchors

| Service | Code |
|---|---|
| shared | `shared/src/commonMain/kotlin/io/github/youndie/mani/feature/demo/DemoSeed.kt` — the set itself and its unrolling |
| shared | `shared/src/commonMain/kotlin/io/github/youndie/mani/feature/demo/DemoResource.kt` — the paths |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/demo/DemoRouting.kt` — the two routes |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/demo/data/DemoService.kt` — unrolling the sandbox |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/demo/data/DemoSandboxCleaner.kt` — the sweep, the ceiling, the age from the `ObjectId` |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/user/Credentials.kt` — the prefix ban at registration |
| composeApp | `composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/auth/domain/StartDemoUseCase.kt` — entering from the welcome screen |
| composeApp | `composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/demo/domain/SeedDemoDataUseCase.kt` — seeding your own account |
| composeApp | `composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/main/MainViewModel.kt` — `onFillWithDemoDataClicked` |

## 5. Scenarios (BDD)

### Scenario: a visitor gets a sandbox of their own

* **Given:** there is room.
* **When:** `POST /demo` with no body.
* **Then:** `201` with a working token pair; with those, a populated rule list reads back
  immediately.
* **Automated:** `DemoRoutingTest`

### Scenario: two visitors get separate sandboxes

* **Given:** two requests in a row.
* **When:** both call `POST /demo`.
* **Then:** **two different** users are created, and one's edits are invisible to the other.
* **Automated:** `DemoRoutingTest`

### Scenario: there are no sandboxes left

* **Given:** as many live sandboxes as the deployed instance is willing to hold.
* **When:** `POST /demo`.
* **Then:** `503` with the body `The demo is full right now, try again later`, and not one user is
  created.
* **Automated:** `DemoRoutingTest`

### Scenario: the sandbox's rules carry real categories

* **Given:** a freshly created sandbox.
* **When:** its rule list is read.
* **Then:** every rule carries a category with a server-issued id, not the synthetic one from the
  seed.
* **Automated:** `DemoRoutingTest`

### Scenario: an expired sandbox goes away together with its rules

* **Given:** a sandbox older than a day.
* **When:** the next `POST /demo` arrives and the sweep runs.
* **Then:** both its transactions and the user itself are deleted.
* **Automated:** `DemoSandboxCleanerTest`

### Scenario: a fresh sandbox stays

* **Given:** a sandbox younger than a day.
* **When:** the sweep runs.
* **Then:** it is untouched.
* **Automated:** `DemoSandboxCleanerTest`

### Scenario: the sweep looks only for sandboxes

* **Given:** the database holds both real users and sandboxes.
* **When:** the sweep runs.
* **Then:** only names with the `demo-` prefix are queried; the sweep does not see real users at
  all.
* **Automated:** `DemoSandboxCleanerTest`

### Scenario: the age is read out of the identifier

* **Given:** an `ObjectId` with a known creation second.
* **When:** the time is taken out of it.
* **Then:** it matches the value embedded in the first four bytes.
* **And:** an identifier that does not look like an `ObjectId` is left **untouched** by the sweep: a
  foreign key format means the document was not created by this code.
* **Automated:** `DemoSandboxCleanerTest`

### Scenario: the seed produces the picture it exists for

* **Given:** the `DemoSeed` set, unrolled on any day.
* **When:** the simulation is computed.
* **Then:** the balance goes negative **inside the forecast horizon** and not tomorrow; today's
  balance is positive; the history is not empty; every rule has a category.
* **Automated:** `DemoSeedTest`

### Scenario: a person populates their own empty account

* **Given:** a signed-in user with not a single rule.
* **When:** the button on the empty screen is pressed (`POST /demo/seed`).
* **Then:** `201`, and the same set appears in their account.
* **Automated:** `DemoRoutingTest`, `SeedDemoDataUseCaseTest`


## 6. Out of scope

* There is no way to reset a sandbox to its initial state: it is edited like any account.
* There is no way to turn a sandbox into a real account — the name is taken by the prefix, and a
  day later it will be carried off.
* There is no warning that a sandbox is about to disappear.

## 7. Quirks

* **The sweep only runs on a request for a new sandbox.** While nobody visits the welcome screen the
  rubbish sits there. There is no scheduler deliberately: the sweep lives where the rubbish appears.
* **The ceiling is computed from the sweep's result, and "we did not count, so we do not forbid".**
  If `sweep()` failed, the number of live sandboxes is unknown and the sandbox is created anyway: a
  closed welcome screen because of a counting failure is a refusal for a reason that has nothing to
  do with the visitor.
* **The ceiling of 500 is about disk, not about load.** The deployed instance's database has 256 MiB
  of disk and 128 MiB of memory, and `POST /demo` requires neither input nor a sign-in: a loop of
  requests would fill the disk in minutes, and a daily sweep does not save you from that.
* **The seed's dates are offsets in days from "today" rather than constants** — otherwise the set
  would age and one day stop showing a forecast at all.
* **The history starts 60 days before today, and the savings are a one-off income at its start.**
  The model has no notion of a starting balance of its own: the balance is the sum of the rules from
  the earliest date.
* **The seed's rules are not deleted together with the categories.** The sweep deletes the
  transactions and the user; the categories go with the user document, and no separate step for them
  exists or is needed.
