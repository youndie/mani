---
id: feature-categories
title: Rule categories
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
  - screen-transaction-form
  - screen-main
api:
  - endpoint-categories
tags: [core]
---

# Rule categories

## 1. Overview

A label on a rule: "Food", "Rent", "Bills". It exists for exactly two things — to caption a rule in
the ledger, and to **filter the ledger** by one category on the main screen. Categories have no
screen of their own: they are created and deleted right in the rule form, as chips.

A category belongs to a person, not to the system: everyone has their own list and there is no
system-wide directory. The demo sandbox gets five ready categories from the seed
([feature-demo-sandbox](feature-demo-sandbox.md)).

## 2. Business rules

* The name is not empty and not longer than 64 characters.
* Categories are seen and edited **only by their owner**. Somebody else's answers `403`.
* On a rename the id is taken **from the path**, not from the body — as with budget rules.
* A category is stored **inside the user document**, not in a collection of its own. Everything else
  follows from that: the list arrives whole, the id is issued at creation time, and deleting a user
  carries their categories off without a separate step.
* A rule stores a `categoryId`, not the category itself. A `Transaction` can only be assembled if
  the owner's category list is known — the substitution is done by the rules route, not by the
  repository.
* Not found — `Category.default` is substituted (`Category("0", "Default")`).

## 3. Flow

```
rule form   ──GET /categories────────▶ chips
            ──POST /categories───────▶ a new category with an id ──▶ selected in the form
            ──DELETE /categories/{id}─▶ gone from the user document

main screen ──GET /categories────────▶ the ledger's dropdown filter
rules       ──GET /transactions──────▶ the route substitutes the category by categoryId
```

## 4. Code anchors

| Service | Code |
|---|---|
| shared | `shared/src/commonMain/kotlin/io/github/youndie/mani/feature/category/CategoryResource.kt` — the path |
| shared | `shared/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/Transaction.kt` — the `Category` type itself and `Category.default` |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/category/CategoryRouting.kt` — the five routes |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/category/CategoryRepository.kt` — the port |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/Rules.kt` — `categoryProblem` |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/data/TransactionRepository.kt` — the substitution in `toTransaction` |
| server | `server/src/main/kotlin/io/github/youndie/mani/feature/category/data/MongoCategoryRepository.kt` |
| server-native | `server-native/src/linuxX64Main/kotlin/io/github/youndie/mani/feature/category/data/MongknCategoryRepository.kt` |
| composeApp | `composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/categories/` — the repository and four use cases |

## 5. Scenarios (BDD)

### Scenario: a category is created, read, renamed and deleted

* **Given:** a signed-in user.
* **When:** `POST /categories`, then `GET`, `PATCH` and `DELETE` on the id it returned.
* **Then:** every step answers `200`; after the delete the category is not in the list.
* **Automated:** `MongoCategoryRepositoryTest`

### Scenario: categories live in the user document

* **Given:** a user with categories.
* **When:** the raw user document is read.
* **Then:** the categories are inside it as an array, not in a collection of their own.
* **Automated:** `MongknStorageTest`

### Scenario: a stranger's category cannot be renamed through the `id` in the body

* **Given:** two users, each with a category of their own.
* **When:** the first sends `PATCH /categories/<their own>` with the other's `id` in the body.
* **Then:** the `id` from the body is ignored and their own is edited; the other's is untouched.
* **Automated:** `OwnershipTest`

### Scenario: reaching for a foreign category

* **Given:** the id of a category that does not belong to the user.
* **When:** `GET`, `PATCH` or `DELETE` on it.
* **Then:** `403` — ownership is checked before anything else.
* **Automated:** `OwnershipTest`

### Scenario: the product refuses a name it cannot display

* **Given:** a name that is empty, only whitespace, or longer than 64 characters.
* **When:** `categoryProblem` is evaluated.
* **Then:** it returns a text: `Category name cannot be empty` or
  `Category name must be at most 64 characters long`; the route returns it with a `400`.
* **And:** the check is automated **at the rule level, not the route level**: there is no end-to-end
  test that posts a bad category name to `POST /categories`.
* **Automated:** `RulesTest`

### Scenario: a rule keeps its category

* **Given:** the user has a category.
* **When:** a rule is created with it and read back.
* **Then:** the response carries the same category, not `Default`.
* **Automated:** `ManiApiTest`

### Scenario: a sandbox's categories are real

* **Given:** a freshly created sandbox.
* **When:** its rule list is read.
* **Then:** every rule carries a category with a server-issued id, not the synthetic one from the
  seed.
* **Automated:** `DemoRoutingTest`

## 6. Out of scope

* There is no system-wide category directory and none is planned: everyone has their own list.
* A category has no ordering and no colour — only a name.
* There is no merging and no moving of rules between categories.

## 7. Quirks

* **Deleting a category leaves its rules with a dangling `categoryId`.** The implementation `$pull`s
  it out of the user document and does not touch the rules
  (`MongknCategoryRepository.kt:83`, `MongoCategoryRepository.kt`). On the next read
  `toTransaction()` fails to find the category and substitutes `Category.default` — the rule is
  shown as "Default". A silent degradation: no error, no trace. Open question 1 in
  [research-architecture](../research/research-architecture.md); the path is not covered by a test.
* **`POST /categories` answers `200`, not `201`** — unlike `POST /transactions`. There is no
  explicit status in the handler; `respond()`'s default is what goes out.
* **`GET /categories/{id}` is the only category route that can answer `404`.** It is reachable only
  when the category is in the owner's list but `getById` did not find it, that is, on an
  inconsistency inside a single document. It leaks no ids: ownership is checked earlier and answers
  `403`.
* **The ownership check runs in three routes and each time reads the whole list.** `getByUser(...)`
  is called at the top of `GET /{id}`, `PATCH` and `DELETE` — for a dozen categories that costs
  nothing, but it is a re-read of the document rather than an index lookup.
* **`Category.default` is not in the database.** It is a contract constant (`id = "0"`), and no
  `categoryId` in the database matches it — it appears only while the response is being assembled.
