---
id: endpoint-transactions
title: Правила бюджета (transactions)
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

# API: правила бюджета

> **Полный перечень маршрутов** ресурса `/transactions`. Формы путей и тел — в
> `shared/.../feature/transaction/`; здесь коды ответов, ярусы доступа и тексты отказов.
>
> Сгенерированной схемы у продукта нет (см. [endpoint-auth](endpoint-auth.md)), этот документ и
> есть справочник.

В интерфейсе сущность называется **правилом** («New rule», «Edit rule»), в контракте и в базе —
`Transaction`. Это одно и то же: запись разворачивается в календарь по своему периоду, и разовая
трата — частный случай с периодом `OneTime`.

## Маршруты — все

| Метод и путь | Ярус | Назначение |
|---|---|---|
| `GET /transactions` | Bearer access | все правила текущего пользователя |
| `POST /transactions` | Bearer access | завести правило |
| `PATCH /transactions/{id}` | Bearer access | изменить правило |
| `DELETE /transactions/{id}` | Bearer access | удалить правило |

Весь блок обёрнут одним `authenticate(jwtConfig.name)`
(`server-common/.../feature/transaction/TransactionRouter.kt:24`). Отдельного маршрута «одно
правило по id» нет: клиент берёт его из уже загруженного списка.

## Обработчики

| Маршрут | Обработчик |
|---|---|
| все четыре | `server-common/.../feature/transaction/TransactionRouter.kt` |
| правила приёмки | `server-common/.../feature/transaction/Rules.kt:16` |
| порт хранилища и `TransactionRecord` | `server-common/.../feature/transaction/data/TransactionRepository.kt` |
| реализация, JVM | `server/.../feature/transaction/data/MongoTransactionRepository.kt` |
| реализация, native | `server-native/.../feature/transaction/data/MongknTransactionRepository.kt` |

## Тела запросов и ответов

| Что | Класс |
|---|---|
| тело `POST`/`PATCH`, элемент ответа `GET` | `shared/.../feature/transaction/Transaction.kt` |
| вложенная категория | `Category` там же |
| период | `Transaction.Period` — `OneTime`, `Day`, `Week`, `TwoWeek`, `Month`, `ThreeMonth`, `HalfYear`, `Year` |

`userId` в контракт **не входит**: владелец — понятие сервера. На сервере для этого есть отдельный
тип `TransactionRecord`.

## Ответы

### `GET /transactions`

| Условие | Код | Тело |
|---|---|---|
| всегда | `200` | массив `Transaction` (возможно пустой) |

Категории подставляются в маршруте, а не в репозитории: они лежат в документе пользователя, и
репозиторий транзакций о них не знает. Не нашлась — подставляется `Category.default`.

### `POST /transactions`

| Условие | Код | Тело |
|---|---|---|
| создано | `201` | созданный `Transaction` — уже с подставленной категорией |
| сумма ≤ 0 | `400` | `Amount must be greater than zero` |
| `until` раньше `date` | `400` | `The end date cannot be earlier than the start date` |
| комментарий длиннее 200 символов | `400` | `Comment must be at most 200 characters long` |
| запись создана, но не прочиталась обратно | `404` | пусто |

### `PATCH /transactions/{id}`

| Условие | Код | Тело |
|---|---|---|
| изменено | `200` | новый `Transaction` |
| нарушено правило приёмки | `400` | тот же текст, что у `POST` |
| запись чужая **или** не существует | `403` | пусто |

**Идентификатор берётся только из пути.** Тело приходит со своим `id`, и он затирается
(`TransactionRouter.kt:66`).

`403` на «не существует» — намеренно тот же ответ, что на «не твоё»: разные коды рассказали бы,
какие идентификаторы заняты.

### `DELETE /transactions/{id}`

| Условие | Код | Тело |
|---|---|---|
| удалено | `200` | пусто |
| запись чужая **или** не существует | `403` | пусто |

### Общее

| Условие | Код | Тело |
|---|---|---|
| `{id}` не разбирается как `ObjectId` | `400` | `Malformed request` |
| тело не разобралось | `400` | `Malformed request` |
| токена нет, он не access, просрочен или испорчен | `401` | `Token is not valid or has expired` |

## Особенности

* **`POST` может ответить `404`.** Ветка «создали и не нашли по своему же id» существует
  (`TransactionRouter.kt:44`) и снаружи выглядит как «ничего не произошло», хотя запись создана.
  Воспроизвести её обычным путём нельзя; тестом она не покрыта.
* **Знак задаёт `income`, а не сумма.** Поэтому отрицательная сумма отвергается: с
  `income = false` она дала бы **плюс** в прогнозе (`amountSigned` умножает на −1). Ноль отвергается
  по другой причине — правило на ноль не двигает прогноз, то есть не делает того единственного,
  ради чего заводится.
* **Правило, кончающееся раньше, чем начинается, не разворачивается ни в один день** — симуляция
  просто не нашла бы ему места и промолчала. Снаружи это выглядело бы как исчезнувшая запись,
  отсюда явная проверка `until < date`.
* **Проверки дублируют форму клиента намеренно.** Форма — удобство, а не граница: за ней открытый
  HTTP, и «клиент такого не пришлёт» — утверждение о клиенте, а не о сервере.
* **Длина комментария ограничена 200 символами, имя категории — 64.** Константы приватные, в
  контракт не входят, и клиент о них не знает: он узнаёт про нарушение из текста отказа.
