---
id: endpoint-transactions
title: Budget rules (transactions)
type: api_endpoints
status: active
services:
  - server-common
  - server
  - server-native
contract_source:
  - "mani-kotlin-fullstack::shared TransactionResource, Transaction"
parent_feature: feature-transactions
---

# API: budget rules

> **The complete route reference** for the `/transactions` resource. The shapes of the paths and
> bodies are in `shared/.../feature/transaction/`; what is here is the status codes, the auth tiers
> and the refusal texts.
>
> The product has no generated schema (see [endpoint-auth](endpoint-auth.md)); this document *is*
> the reference.

In the interface the entity is called a **rule** ("New rule", "Edit rule"); in the contract and in
the database it is a `Transaction`. They are the same thing: the record is expanded into a calendar
according to its period, and a one-off expense is the special case with period `OneTime`.

## Routes — all of them

| Method and path | Tier | Purpose |
|---|---|---|
| `GET /transactions` | Bearer access | every rule of the current user |
| `POST /transactions` | Bearer access | create a rule |
| `PATCH /transactions/{id}` | Bearer access | change a rule |
| `DELETE /transactions/{id}` | Bearer access | delete a rule |

The whole block is wrapped in a single `authenticate(jwtConfig.name)`
(`server-common/.../feature/transaction/TransactionRouter.kt:24`). There is no "one rule by id"
route: the client takes it out of the list it has already loaded.

## Handlers

| Route | Handler |
|---|---|
| all four | `server-common/.../feature/transaction/TransactionRouter.kt` |
| the validation rules | `server-common/.../feature/transaction/Rules.kt:16` |
| the storage port and `TransactionRecord` | `server-common/.../feature/transaction/data/TransactionRepository.kt` |
| implementation, JVM | `server/.../feature/transaction/data/MongoTransactionRepository.kt` |
| implementation, native | `server-native/.../feature/transaction/data/MongknTransactionRepository.kt` |

## Request and response bodies

| What | Class |
|---|---|
| body of `POST`/`PATCH`, element of the `GET` response | `shared/.../feature/transaction/Transaction.kt` |
| the nested category | `Category`, in the same file |
| the period | `Transaction.Period` — `OneTime`, `Day`, `Week`, `TwoWeek`, `Month`, `ThreeMonth`, `HalfYear`, `Year` |

`userId` is **not** part of the contract: the owner is a server-side notion. The server has a
separate `TransactionRecord` type for it.

## Responses

### `GET /transactions`

| Condition | Status | Body |
|---|---|---|
| always | `200` | an array of `Transaction` (possibly empty) |

Categories are substituted in the route rather than in the repository: they live in the user
document, and the transaction repository knows nothing about them. Not found — `Category.default` is
substituted.

### `POST /transactions`

| Condition | Status | Body |
|---|---|---|
| created | `201` | the created `Transaction`, with the category already substituted |
| amount ≤ 0 | `400` | `Amount must be greater than zero` |
| `until` earlier than `date` | `400` | `The end date cannot be earlier than the start date` |
| comment longer than 200 characters | `400` | `Comment must be at most 200 characters long` |
| the record was created but could not be read back | `404` | empty |

### `PATCH /transactions/{id}`

| Condition | Status | Body |
|---|---|---|
| changed | `200` | the new `Transaction` |
| a validation rule is broken | `400` | the same text as for `POST` |
| the record belongs to someone else **or** does not exist | `403` | empty |

**The id is taken from the path only.** The body arrives with an `id` of its own and it is
overwritten (`TransactionRouter.kt:66`).

The `403` on "does not exist" is deliberately the same answer as on "not yours": different codes
would say which ids are taken.

### `DELETE /transactions/{id}`

| Condition | Status | Body |
|---|---|---|
| deleted | `200` | empty |
| the record belongs to someone else **or** does not exist | `403` | empty |

### Common

| Condition | Status | Body |
|---|---|---|
| `{id}` does not parse as an `ObjectId` | `400` | `Malformed request` |
| the body did not parse | `400` | `Malformed request` |
| no token, not an access token, expired or corrupt | `401` | `Token is not valid or has expired` |

## Quirks

* **`POST` can answer `404`.** The "created it and could not find it by its own id" branch exists
  (`TransactionRouter.kt:44`) and looks from outside like "nothing happened", even though the record
  was created. It cannot be reproduced by any ordinary path, and no test covers it.
* **The sign comes from `income`, not from the amount.** That is why a negative amount is refused:
  with `income = false` it would add a **plus** to the forecast (`amountSigned` multiplies by −1).
  Zero is refused for a different reason — a rule for zero does not move the forecast, that is, it
  does not do the one thing it is created for.
* **A rule that ends before it begins expands into no day at all** — the simulation would simply
  find no place for it and stay silent. From outside that would look like a record that vanished,
  hence the explicit `until < date` check.
* **The checks deliberately duplicate the client's form.** A form is a convenience, not a boundary:
  behind it is open HTTP, and "the client will not send that" is a statement about the client, not
  about the server.
* **The comment is capped at 200 characters and a category name at 64.** The constants are private,
  are not part of the contract, and the client does not know them: it learns about a violation from
  the refusal text.
