---
id: endpoint-demo
title: Demo sandbox
type: api_endpoints
status: active
services:
  - server-common
  - server
  - server-native
contract_source:
  - "mani-kotlin-fullstack::shared DemoResource, Tokens"
parent_feature: feature-demo-sandbox
---

# API: demo sandbox

> **The complete route reference** for the `/demo` resource. The product has no generated schema
> (see [endpoint-auth](endpoint-auth.md)); this document *is* the reference.
>
> `POST /demo` returns tokens and is therefore **a way into the app** on a par with `POST /auth` —
> the tokens themselves and the session rules are covered in [endpoint-auth](endpoint-auth.md).

## Routes — all of them

| Method and path | Tier | Purpose |
|---|---|---|
| `POST /demo` | open | create a sandbox and get tokens; **the request has no body** |
| `POST /demo/seed` | Bearer access | seed **your own** account with the same data set |

The differing tiers are not an oversight: creating a sandbox is precisely what issues the first
token, while seeding your own account is an owner's operation.

## Handlers

| Route | Handler |
|---|---|
| both | `server-common/.../feature/demo/DemoRouting.kt` |
| unrolling the sandbox | `server-common/.../feature/demo/data/DemoService.kt:47` (`createSandbox`) |
| seeding | `server-common/.../feature/demo/data/DemoService.kt` (`seed`) |
| the sweep and the ceiling | `server-common/.../feature/demo/data/DemoSandboxCleaner.kt` |
| the data set itself | `shared/.../feature/demo/DemoSeed.kt` |

## Request and response bodies

| What | Class |
|---|---|
| response of `POST /demo` | `shared/.../feature/auth/Tokens.kt` |
| the set that gets unrolled | `shared/.../feature/demo/DemoSeed.kt` (`DemoRule`) |

`DemoSeed` lives in the contract rather than on the server because the welcome screen draws a sample
forecast from it **before anyone has signed in** — with the same code the server unrolls the sandbox
with.

## Responses

### `POST /demo`

| Condition | Status | Body |
|---|---|---|
| the sandbox was created | `201` | `Tokens` |
| there are at least 500 live sandboxes | `503` | `The demo is full right now, try again later` |
| storage refused at any step | `500` | empty |

`503` rather than `500`: the server is fine, there is no room — and in an hour there probably will
be. The text goes to the welcome screen as is and is shown to a person.

Three sources of the `500` (`DemoService.Outcome.Refused`): no free name could be found, the user
could not be written, or tokens could not be issued for them.

### `POST /demo/seed`

| Condition | Status | Body |
|---|---|---|
| seeded | `201` | empty |
| no token, or not an access token | `401` | `Token is not valid or has expired` |

There is no idempotency: two calls put the set in twice.

### Common

| Condition | Status | Body |
|---|---|---|
| an unhandled exception | `500` | empty (a line goes to the server's stdout) |

## Quirks

* **The sweep runs inside `POST /demo`, before the ceiling is checked.** Neither build has a
  scheduler. That means the first request after a long quiet spell pays for deleting all the
  accumulated rubbish — which costs no noticeable time only because there are a handful of
  sandboxes.
* **A failing sweep does not fail the visitor.** `sweep()` is wrapped so that its failure yields
  "the count is unknown", and an unknown count does not trip the ceiling.
* **`POST /demo` requires neither input nor a sign-in** — which is what makes the ceiling mandatory
  rather than desirable: the deployed instance's database has 256 MiB of disk.
