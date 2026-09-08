---
id: endpoint-categories
title: Categories
type: api_endpoints
status: active
services:
  - server-common
  - server
  - server-native
contract_source:
  - "mani-kotlin-fullstack::shared CategoryResource, Category"
parent_feature: feature-categories
---

# API: categories

> **The complete route reference** for the `/categories` resource. The product has no generated
> schema (see [endpoint-auth](endpoint-auth.md)); this document *is* the reference.

## Routes — all of them

| Method and path | Tier | Purpose |
|---|---|---|
| `GET /categories` | Bearer access | every category of the current user |
| `POST /categories` | Bearer access | create a category |
| `GET /categories/{id}` | Bearer access | one category |
| `PATCH /categories/{id}` | Bearer access | rename |
| `DELETE /categories/{id}` | Bearer access | delete |

The whole block is wrapped in a single `authenticate(jwtConfig.name)`
(`server-common/.../feature/category/CategoryRouting.kt:22`).

## Handlers

| Route | Handler |
|---|---|
| all five | `server-common/.../feature/category/CategoryRouting.kt` |
| the name rule | `server-common/.../feature/transaction/Rules.kt:42` (`categoryProblem`) |
| the storage port | `server-common/.../feature/category/CategoryRepository.kt` |
| implementation, JVM | `server/.../feature/category/data/MongoCategoryRepository.kt` |
| implementation, native | `server-native/.../feature/category/data/MongknCategoryRepository.kt` |

## Request and response bodies

| What | Class |
|---|---|
| body of `POST`/`PATCH`, element of the response | `shared/.../feature/transaction/Transaction.kt` (`Category`) |

`Category` is declared next to `Transaction` rather than in the `feature/category` package: the type
belongs to the rule model, while `feature/category` in `:shared` holds only the path.

## Responses

### `GET /categories`

| Condition | Status | Body |
|---|---|---|
| always | `200` | an array of `Category` (possibly empty) |

### `POST /categories`

| Condition | Status | Body |
|---|---|---|
| created | `200` | the created `Category` with its issued `id` |
| the name is empty or only whitespace | `400` | `Category name cannot be empty` |
| the name is longer than 64 characters | `400` | `Category name must be at most 64 characters long` |

**`200`, not `201`** — unlike `POST /transactions`. There is no explicit status in the handler.

### `GET /categories/{id}`

| Condition | Status | Body |
|---|---|---|
| the caller's own category was found | `200` | `Category` |
| the category does not belong to the caller **or** does not exist | `403` | empty |
| present in the owner's list but could not be read | `404` | empty |

The order of the checks matters: ownership is verified against the owner's list **before** the read,
so the `404` is reachable only on an inconsistency inside a single document and gives no ids away.

### `PATCH /categories/{id}`

| Condition | Status | Body |
|---|---|---|
| renamed | `200` | the updated `Category` |
| not the caller's, or no such category | `403` | empty |
| the name fails the rule | `400` | the rule's text |

**The id is taken from the path only.** The body arrives with an `id` of its own and it is
overwritten (`CategoryRouting.kt:64`): ownership used to be checked by the path while what got
renamed was whatever the body named.

### `DELETE /categories/{id}`

| Condition | Status | Body |
|---|---|---|
| deleted | `200` | empty |
| not the caller's, or no such category | `403` | empty |

### Common

| Condition | Status | Body |
|---|---|---|
| `{id}` does not parse as an `ObjectId` | `400` | `Malformed request` |
| the body did not parse | `400` | `Malformed request` |
| no token, not an access token, expired or corrupt | `401` | `Token is not valid or has expired` |

## Quirks

* **Deleting does not touch the rules.** The implementation `$pull`s the category out of the user
  document; the rules keep a `categoryId` that no longer corresponds to anything, and on read
  `Category.default` is substituted. See
  [feature-categories](../features/feature-categories.md) §7.
* **The list arrives whole, without paging** — categories sit in an array inside the user document.
* **`403` covers both "not yours" and "does not exist"**, as with budget rules: different answers
  would say which ids are taken.
