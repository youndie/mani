---
id: feature-categories
title: Категории правил
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

# Категории правил

## 1. Обзор

Ярлык на правиле: «Food», «Rent», «Bills». Нужен ровно для двух вещей — подписать правило в ленте и
**отфильтровать ленту** по одной категории на главном экране. Своего экрана у категорий нет: их
заводят и удаляют прямо в форме правила, чипами.

Категория принадлежит человеку, а не системе: список у каждого свой, общесистемного справочника
нет. Демо-песочница получает пять готовых категорий из сида
([feature-demo-sandbox](feature-demo-sandbox.md)).

## 2. Правила

* Имя не пустое и не длиннее 64 символов.
* Категории видит и правит **только владелец**. Чужая даёт `403`.
* При переименовании идентификатор берётся **из пути**, а не из тела — как и у правил бюджета.
* Категория хранится **внутри документа пользователя**, а не отдельной коллекцией. Отсюда всё
  остальное: список приходит целиком, идентификатор выдаётся в момент создания, а удаление
  пользователя уносит его категории без отдельного шага.
* У правила хранится `categoryId`, а не сама категория. Собрать `Transaction` можно только зная
  список категорий владельца — подстановкой занимается маршрут правил, не репозиторий.
* Не нашлось — подставляется `Category.default` (`Category("0", "Default")`).

## 3. Ход

```
форма правила ──GET /categories───────▶ чипы
              ──POST /categories──────▶ новая категория с id ──▶ выбрана в форме
              ──DELETE /categories/{id}▶ ушла из документа пользователя

главный экран ──GET /categories───────▶ выпадающий фильтр ленты
правила       ──GET /transactions─────▶ маршрут подставляет категорию по categoryId
```

## 4. Код

| Сервис | Код |
|---|---|
| shared | `shared/src/commonMain/kotlin/io/github/youndie/mani/feature/category/CategoryResource.kt` — путь |
| shared | `shared/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/Transaction.kt` — сам тип `Category` и `Category.default` |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/category/CategoryRouting.kt` — пять маршрутов |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/category/CategoryRepository.kt` — порт |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/Rules.kt` — `categoryProblem` |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/data/TransactionRepository.kt` — подстановка в `toTransaction` |
| server | `server/src/main/kotlin/io/github/youndie/mani/feature/category/data/MongoCategoryRepository.kt` |
| server-native | `server-native/src/linuxX64Main/kotlin/io/github/youndie/mani/feature/category/data/MongknCategoryRepository.kt` |
| composeApp | `composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/categories/` — репозиторий и четыре use case'а |

## 5. Сценарии

### Scenario: категория заведена, прочитана, переименована и удалена

* **Given:** вошедший пользователь.
* **When:** `POST /categories`, затем `GET`, `PATCH` и `DELETE` по выданному идентификатору.
* **Then:** каждый шаг отвечает `200`; после удаления категории в списке нет.
* **Automated:** `MongoCategoryRepositoryTest`

### Scenario: категории лежат в документе пользователя

* **Given:** пользователь с заведёнными категориями.
* **When:** читается сырой документ пользователя.
* **Then:** категории лежат внутри него массивом, а не отдельной коллекцией.
* **Automated:** `MongknStorageTest`

### Scenario: чужую категорию не переименовать через `id` в теле

* **Given:** два пользователя, у каждого своя категория.
* **When:** первый шлёт `PATCH /categories/<своя>`, положив в тело `id` чужой.
* **Then:** `id` из тела игнорируется, правится своя; чужая не тронута.
* **Automated:** `OwnershipTest`

### Scenario: обращение к чужой категории

* **Given:** идентификатор категории, которая пользователю не принадлежит.
* **When:** `GET`, `PATCH` или `DELETE` по нему.
* **Then:** `403` — принадлежность проверяется до всего остального.
* **Automated:** `OwnershipTest`

### Scenario: продукт отказывается от имени, которое не может показать

* **Given:** имя пустое, из одних пробелов, либо длиннее 64 символов.
* **When:** проверяется `categoryProblem`.
* **Then:** возвращается текст: `Category name cannot be empty` либо
  `Category name must be at most 64 characters long`; маршрут отдаёт его с `400`.
* **And:** проверка автоматизирована **на уровне правила, а не маршрута**: сквозного теста, который
  послал бы `POST /categories` с плохим именем, нет.
* **Automated:** `RulesTest`

### Scenario: правило сохраняет свою категорию

* **Given:** у пользователя заведена категория.
* **When:** правило создано с ней и прочитано обратно.
* **Then:** в ответе категория та же, а не `Default`.
* **Automated:** `ManiApiTest`

### Scenario: у песочницы категории настоящие

* **Given:** только что заведённая песочница.
* **When:** читается её список правил.
* **Then:** у каждого правила категория с выданным сервером идентификатором, а не синтетическая из
  сида.
* **Automated:** `DemoRoutingTest`

## 6. Вне охвата

* Общесистемного справочника категорий нет и не планируется: список у каждого свой.
* Порядка и цвета у категории нет — только имя.
* Слияния и переноса правил между категориями нет.

## 7. Особенности

* **Удаление категории оставляет правила с висящим `categoryId`.** Реализация делает `$pull` из
  документа пользователя и правил не трогает
  (`MongknCategoryRepository.kt:83`, `MongoCategoryRepository.kt`). При следующем чтении
  `toTransaction()` не находит категорию и подставляет `Category.default` — правило показывается
  как «Default». Это тихая деградация: ни ошибки, ни следа. Открытый вопрос 1 в
  [research-architecture](../research/research-architecture.md); тестом путь не покрыт.
* **`POST /categories` отвечает `200`, а не `201`** — в отличие от `POST /transactions`. Явного
  кода в обработчике нет, отдаётся умолчание `respond()`.
* **`GET /categories/{id}` — единственный маршрут категорий, умеющий ответить `404`.** Он
  достижим только если категория есть в списке владельца, но `getById` её не нашёл, то есть при
  расхождении внутри одного документа. Утечки идентификаторов это не даёт: принадлежность
  проверяется раньше и отвечает `403`.
* **Проверка принадлежности стоит четыре раза подряд и каждый раз читает весь список.**
  `getByUser(...)` вызывается в начале `GET /{id}`, `PATCH`, `DELETE` — для десятка категорий это
  не имеет цены, но это именно перечитывание документа, а не индекс.
* **`Category.default` не лежит в базе.** Это константа контракта (`id = "0"`), и ни один
  `categoryId` в базе с ней не совпадает — она появляется только на сборке ответа.
