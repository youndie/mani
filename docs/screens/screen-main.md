---
id: screen-main
title: Главный экран — прогноз и лента
type: client_screen
platform: [android, ios, desktop, web]
status: active
entry:
  all: "маршрут ManiScreen.Main; точка входа графа, если refresh-токен есть"
parent_feature: feature-transactions
calls_api:
  - endpoint-transactions
  - endpoint-categories
  - endpoint-demo
source: composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/main/
---

# Экран: главный

Ради него всё и написано: сверху ответ на вопрос «когда деньги кончатся», под ним график, дальше
лента правил с балансом на конец каждого дня.

## 0a. Код

| Что | Файл |
|---|---|
| ViewModel | `composeApp/.../feature/main/MainViewModel.kt` |
| Состояние экрана | `composeApp/.../feature/main/ui/MainUiState.kt` |
| Состояние героя | `composeApp/.../feature/main/ui/ForecastUiState.kt` |
| Композиция и фильтры | `composeApp/.../feature/main/ui/MainComponent.kt` (`FiltersState` там же, строка 225) |
| Герой | `composeApp/.../feature/main/ui/ForecastHero.kt` |
| «Сервер недоступен» | `composeApp/.../feature/main/ui/ServerUnreachable.kt` |
| Расписание автоповтора | `composeApp/.../feature/main/ui/RetrySchedule.kt` |
| Симуляция | `shared/.../feature/transaction/TransactionsOperations.kt` |

## 0. Точка входа и видимость

* **Точка входа:** точка входа графа, когда refresh-токен есть; сюда же ведут успешный вход,
  регистрация→вход и демо.
* **Показывается:** только авторизованному.
* Стрелки «назад» нет: `Main` — корневой экран. Раньше стрелка там была и врала — за ней оставалась
  витрина, куда вернуться уже нельзя.

## 1. Состояния

Поля `MainUiState`; герой — отдельная иерархия `ForecastUiState`, а не набор необязательных полей:

**Герой (`forecast`):**

| Состояние | Что показывает |
|---|---|
| `Loading` | данные ещё не пришли |
| `Empty` | правил нет — прогнозировать нечего |
| `RunsOut` | «деньги кончатся такого-то», сколько дней осталось, баланс сегодня, низшая точка |
| `Steady` | внутри горизонта в минус не уходим — показывается сам баланс |

`Steady` — отдельное состояние, а не `runsOutOn = null`: показывать нужно другое.

**Экран целиком:**

* `loading` — идёт загрузка (лента под шиммером);
* `transactions` — лента, сгруппированная по дням; `dayBalances` — баланс на конец каждого дня,
  считается **по всей симуляции**, а не по отфильтрованной ленте;
* `selectedTransactions` + `showDeleteDialog` — режим выбора и подтверждение удаления;
* `showProfile` — всплывающее меню профиля (в нём выход);
* `filtersState` — фильтры (см. §4.3);
* `showingCacheFrom != null` — сети нет, показано последнее известное, снятое в это время;
* `unreachable != null` — сервер не ответил и показать нечего;
* `errorMessage` — прочие отказы, снекбаром.

Последние два — **разные** экраны, а не разные значения одного поля.

## 2. Обращения к API

| Вызов | Контракт | Документ |
|---|---|---|
| `GET /transactions` | `TransactionResource` | [endpoint-transactions](../api/endpoint-transactions.md) |
| `DELETE /transactions/{id}` | `TransactionResource.ById` | [endpoint-transactions](../api/endpoint-transactions.md) |
| `GET /categories` | `CategoryResource` | [endpoint-categories](../api/endpoint-categories.md) (фильтр по категориям) |
| `POST /demo/seed` | `DemoResource.Seed` | [endpoint-demo](../api/endpoint-demo.md) (кнопка на пустом экране) |

## 3. Инициализация

Входных параметров нет.

| Вызов | Когда | Результат |
|---|---|---|
| `GET /transactions` | при открытии и на возврате в `ON_START` | лента, график, герой |
| `GET /categories` | при открытии | наполняет фильтр |

Валюта **в сеть не ходит**: `GetCurrentCurrencyUseCase` читает её из локальных `Settings`, и там
всегда умолчание — см. [endpoint-currencies](../api/endpoint-currencies.md).

| Случай | Обработка | Состояние |
|---|---|---|
| `200`, есть записи | симуляция, группировка по дням | лента + `RunsOut`/`Steady` |
| `200`, пусто | — | пустая лента + `Empty` |
| отказ, кэш есть | показывается кэш | `showingCacheFrom` заполнен |
| отказ, кэша нет | — | `unreachable` заполнен, с причиной и автоповтором |
| `401`, продлить не удалось | событие `expired` | граф уводит на витрину |

## 4. Элементы

### 4.1. Герой-прогноз

Заголовок экрана — **дата, когда деньги кончатся**. До редизайна это были пять строк
моноширинного текста одним `AnnotatedString`, и главное стояло последним, выглядя как строка
отладочного вывода.

`runsOutOn` — день без года: год очевиден из «через столько-то дней», а место в заголовке дорого.

### 4.2. График

Тот же вендоренный `compose-charts`, что на витрине. Маркер дня перехода через ноль рисуется
**внутри канваса** библиотеки, где известна геометрия графика, — отсюда местные правки в
вендоренном коде.

### 4.3. Фильтры

`FiltersState`: `upcoming` (чипы `Upcoming` / `Past`), `category`, плюс `loading` для шиммера.

Важно: фильтр меняет **ленту**, но не `dayBalances` — баланс считается по всей симуляции. Иначе
скрытие части правил меняло бы баланс, которого оно не меняет.

### 4.4. Лента

Сгруппирована по дням, у каждого дня — баланс на его конец. Долгое нажатие включает выбор;
выбранные удаляются через подтверждение (`showDeleteDialog`).

### 4.5. Пустой экран

Правил ещё нет — герой в состоянии `Empty`, и предлагается заполнить аккаунт демонстрационным
набором (`MainViewModel.onFillWithDemoDataClicked` → `POST /demo/seed`). Заводить второй аккаунт
ради того, чтобы посмотреть на заполненное приложение, не нужно. Отказ показывается в
`errorMessage`.

### 4.6. Меню профиля

`showProfile`; в нём выход. Выход уводит на витрину **явным переходом** (`MainViewModel.loggedOut`),
а не сбросом графа при пропаже токена, как было раньше.

### 4.7. «Сервер недоступен»

Показывается вместо содержимого, когда нет ни свежего, ни сохранённого. Несёт машинную причину
(`HTTP 503 · api.mani.kotlin.website · 11:42:07`), кнопку «Try again» и обратный отсчёт до
автоповтора. Отдельной строкой сказано «Your rules are safe» — без этого «не удалось загрузить»
читается как «данные потеряны», хотя пропала только связь.

## 5. Навигация

* правило в ленте ──▶ `screen-transaction-form` (правка, `TransactionRoute(id)`)
* «+» ──▶ `screen-transaction-form` (создание)
* «History» ──▶ `screen-history`
* выход ──▶ `screen-welcome` (стек чистится)
* сессия истекла ──▶ `screen-welcome`
