---
id: endpoint-currencies
title: Currencies
type: api_endpoints
status: active
services:
  - server-common
  - server
  - server-native
contract_source:
  - "mani-kotlin-fullstack::shared CurrencyResource, Currency"
parent_feature: feature-transactions
---

# API: currencies

> One route, and **nobody calls it**. This document exists so that this is written down rather than
> rediscovered by everyone who trips over `CurrencyResource`.

## Routes — all of them

| Method and path | Tier | Purpose |
|---|---|---|
| `GET /currencies` | open | the list of currencies the server knows about |

The path is `/currencies`, plural, although the class is called `CurrencyResource`, the package is
`feature/currency` and the type is `Currency`. The only place the plural appears is the string in
the annotation.

## Handler

| Route | Handler |
|---|---|
| `GET /currencies` | `server-common/.../feature/currency/CurrencyRouting.kt:9` |

The handler in its entirety is one `respond` with a list of two constants; there is no storage and
no configuration behind it.

## Response body

| What | Class |
|---|---|
| element of the response | `shared/.../feature/currency/Currency.kt` |

## Responses

| Condition | Status | Body |
|---|---|---|
| always | `200` | `[Currency.Rub, Currency.Usd]` — a hard-coded list of two |

## Quirks

There are more of them here than there is route.

* **The client never calls this route.** Searching the whole tree for `CurrencyResource` returns two
  occurrences: the declaration in `:shared` and `get<CurrencyResource>` in the routing. There is not
  one call from the client. Verified 2026-09-08.
* **On the client the currency comes from local settings.** `CurrentCurrencyRepositoryImpl` reads it
  out of `Settings` with `Currency.Usd` as the default
  (`composeApp/.../feature/currency/data/CurrentCurrencyRepository.kt`). Every screen gets it
  through `GetCurrentCurrencyUseCase`.
* **And nobody writes it.** The `CurrentCurrencyRepository.currency` setter is called nowhere: the
  app has no currency picker. So `Settings` is always empty, the default is always what comes back,
  and **the app effectively works in dollars only** — including the `$` symbol on every amount.
* **`Currency.Rub` is therefore declared and unreachable**, and its `name` is the only Cyrillic in
  the contract (`"Рубль"`).

What follows is that there are **two separate loose ends here, not one**: a route with no consumer,
and a setting with no way to set it. Fixing either does not fix the other — a currency picker, if
one appears, will be able to take the list from the server, but today its absence means nobody needs
that list either.
