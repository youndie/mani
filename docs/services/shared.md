---
id: shared
title: ":shared — the wire contract"
type: service
repo_url: https://github.com/youndie/mani-kotlin-fullstack
module: ":shared"
tech_stack: [Kotlin Multiplatform, ktor-resources, kotlinx.serialization, kotlinx.datetime]
owner: unassigned
depends_on: []
publishes:
  - klib/jar consumed inside the build (nothing is published externally)
---

# :shared — the wire contract

## 1. Responsibility

The only thing here is **a description of what the client and the server exchange**: the
`@Resource` classes (which both route the request on the server and build the URL on the client),
the domain model, the serializers, and a handful of pure functions over the model.

Targets: `android`, `ios`, `jvm`, `wasmJs`, `linuxX64` — that is, every target the product has.

What is **deliberately not** here:

* **the server address.** It moved to `composeApp/.../Constants.kt`: a server has no use for its own
  address, and a module called the contract should be one;
* **`userId`.** The record's owner is a server-side notion. The client has no use for it and it is
  not part of the contract; on the server there is `TransactionRecord` for this
  (see [server-common](server-common.md));
* **business rules.** Validation lives on the server (`Rules.kt`, `Credentials.kt`) — behind the
  client's form stands open HTTP.

## 2. API contracts

One resource per subject area:

| Resource | Path |
|---|---|
| `AuthResource`, `AuthResource.Refresh` | `/auth`, `/auth/refresh` |
| `UserResource` | `/users` |
| `TransactionResource`, `TransactionResource.ById` | `/transactions`, `/transactions/{id}` |
| `CategoryResource`, `CategoryResource.ById` | `/categories`, `/categories/{id}` |
| `CurrencyResource` | `/currencies` |
| `DemoResource`, `DemoResource.Seed` | `/demo`, `/demo/seed` |
| `HealthResource`, `HealthResource.Ready` | `/health`, `/health/ready` |

The full breakdown with status codes lives in the api layer:
[endpoint-transactions](../api/endpoint-transactions.md),
[endpoint-categories](../api/endpoint-categories.md), [endpoint-auth](../api/endpoint-auth.md),
[endpoint-demo](../api/endpoint-demo.md), [endpoint-health](../api/endpoint-health.md),
[endpoint-currencies](../api/endpoint-currencies.md).

## 2a. Code anchors

| File | What is there |
|---|---|
| `shared/src/commonMain/kotlin/io/github/youndie/mani/feature/` | one directory per subject area: resource + DTOs |
| `shared/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/Transaction.kt` | the rule model: amount, sign, period, date, category |
| `shared/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/WithId.kt` | "a record has a server-issued id" — implemented by `Transaction` and `Category` |
| `shared/src/commonMain/kotlin/io/github/youndie/mani/utilz/bigdecimal/` | `BigDecimalSerializable` and its serializer |
| `shared/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/TransactionsOperations.kt` | expanding rules into a calendar and simulating the balance |
| `shared/src/commonTest/kotlin/` | `TransactionOperationsTest`, `DemoSeedTest` |

## 3. How it is built

**One class, two uses.** On the server a `@Resource` class parses the path
(`post<TransactionResource.ById> { path -> ... }`); on the client the same class builds the URL
(`httpClient.post(AuthResource.Refresh())`). Hence the property the module exists for: you cannot
rename a path and forget to fix the other side, because there is only one side.

**The simulation lives here rather than on the server or the client.**
`TransactionsOperations.kt` expands rules into a calendar and computes the day-by-day balance. Both
the client needs it (the hero of the main screen, the chart, the "how far this moves the day you run
out" preview in the form) and the welcome screen does, drawing a chart from the demo seed **before
anyone has signed in**. A shared module is the only place where both sides get the same answer.

## 4. Dependencies

| Kind | Name | What for |
|---|---|---|
| Library | `io.ktor:ktor-resources` | typed paths |
| Library | `kotlinx.serialization` | the JSON on the wire |
| Library | `kotlinx.datetime` | `LocalDate` in the model |
| Library | `com.ionspin.kotlin:bignum` | amounts, without the losses of `double` |

## 5. Infrastructure and deploy

Neither published nor deployed on its own: it is compiled into its consumers — both server builds
and every client target.

## 6. Local setup

```bash
./gradlew :shared:jvmTest
```

## 7. Configuration

None. The module reads nothing from the environment.

## 8. Quirks

* **`Category.default` is part of the contract, not a client-side placeholder.**
  `Category("0", "Default")` is declared in `Transaction.kt` and substituted by the server whenever
  a record's `categoryId` is not among the owner's categories. A deleted category therefore looks
  like "Default" rather than like an error; see open question 1 in
  [research-architecture](../research/research-architecture.md).
* **The sign comes from `income`, not from the amount.** `amountSigned` multiplies by −1, so a
  negative amount with `income = false` would add a **plus** to the forecast. The server refuses
  that (`Rules.kt`), but the model on its own permits it.
