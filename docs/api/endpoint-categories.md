---
id: endpoint-categories
title: Категории
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

# API: категории

> **Полный перечень маршрутов** ресурса `/categories`. Сгенерированной схемы у продукта нет
> (см. [endpoint-auth](endpoint-auth.md)), этот документ и есть справочник.

## Маршруты — все

| Метод и путь | Ярус | Назначение |
|---|---|---|
| `GET /categories` | Bearer access | все категории текущего пользователя |
| `POST /categories` | Bearer access | завести категорию |
| `GET /categories/{id}` | Bearer access | одна категория |
| `PATCH /categories/{id}` | Bearer access | переименовать |
| `DELETE /categories/{id}` | Bearer access | удалить |

Весь блок обёрнут одним `authenticate(jwtConfig.name)`
(`server-common/.../feature/category/CategoryRouting.kt:22`).

## Обработчики

| Маршрут | Обработчик |
|---|---|
| все пять | `server-common/.../feature/category/CategoryRouting.kt` |
| правило имени | `server-common/.../feature/transaction/Rules.kt:42` (`categoryProblem`) |
| порт хранилища | `server-common/.../feature/category/CategoryRepository.kt` |
| реализация, JVM | `server/.../feature/category/data/MongoCategoryRepository.kt` |
| реализация, native | `server-native/.../feature/category/data/MongknCategoryRepository.kt` |

## Тела запросов и ответов

| Что | Класс |
|---|---|
| тело `POST`/`PATCH`, элемент ответа | `shared/.../feature/transaction/Transaction.kt` (`Category`) |

`Category` объявлена рядом с `Transaction`, а не в пакете `feature/category`: тип принадлежит
модели правила, а `feature/category` в `:shared` держит только путь.

## Ответы

### `GET /categories`

| Условие | Код | Тело |
|---|---|---|
| всегда | `200` | массив `Category` (возможно пустой) |

### `POST /categories`

| Условие | Код | Тело |
|---|---|---|
| создано | `200` | созданная `Category` с выданным `id` |
| имя пустое или из пробелов | `400` | `Category name cannot be empty` |
| имя длиннее 64 символов | `400` | `Category name must be at most 64 characters long` |

**`200`, а не `201`** — в отличие от `POST /transactions`. Явного кода в обработчике нет.

### `GET /categories/{id}`

| Условие | Код | Тело |
|---|---|---|
| своя категория найдена | `200` | `Category` |
| категория не принадлежит вызывающему **или** не существует | `403` | пусто |
| есть в списке владельца, но не прочиталась | `404` | пусто |

Порядок проверок важен: принадлежность сверяется по списку владельца **до** чтения, поэтому `404`
достижим только при расхождении внутри одного документа и идентификаторов не выдаёт.

### `PATCH /categories/{id}`

| Условие | Код | Тело |
|---|---|---|
| переименовано | `200` | обновлённая `Category` |
| не своя или нет такой | `403` | пусто |
| имя не проходит правило | `400` | текст правила |

**Идентификатор берётся только из пути.** Тело приходит со своим `id`, и он затирается
(`CategoryRouting.kt:64`): раньше принадлежность проверялась по пути, а переименовывалось то, что
назвало тело.

### `DELETE /categories/{id}`

| Условие | Код | Тело |
|---|---|---|
| удалено | `200` | пусто |
| не своя или нет такой | `403` | пусто |

### Общее

| Условие | Код | Тело |
|---|---|---|
| `{id}` не разбирается как `ObjectId` | `400` | `Malformed request` |
| тело не разобралось | `400` | `Malformed request` |
| токена нет, он не access, просрочен или испорчен | `401` | `Token is not valid or has expired` |

## Особенности

* **Удаление не трогает правила.** Реализация делает `$pull` категории из документа пользователя;
  у правил остаётся `categoryId`, которому больше ничего не соответствует, и при чтении
  подставляется `Category.default`. См. [feature-categories](../features/feature-categories.md) §7.
* **Список приходит целиком, без страниц** — категории лежат массивом в документе пользователя.
* **`403` покрывает и «не твоё», и «не существует»**, как у правил бюджета: разные ответы
  говорили бы, какие идентификаторы заняты.
