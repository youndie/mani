---
id: screen-history
title: История — лента за месяц
type: client_screen
platform: [android, ios, desktop, web]
status: active
entry:
  all: "маршрут ManiScreen.History"
parent_feature: feature-transactions
calls_api:
  - endpoint-transactions
source: composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/ui/component/
---

# Экран: история

Та же лента, что на главном, но с итогом за текущий месяц вместо прогноза наперёд.

## 0a. Код

| Что | Файл |
|---|---|
| ViewModel | `composeApp/.../feature/transaction/ui/TransactionsViewModel.kt` |
| Состояние | `composeApp/.../feature/transaction/ui/model/TransactionListUiState.kt` |
| Композиция | `composeApp/.../feature/transaction/ui/component/TransactionsListComponent.kt` |
| Строка правила | `composeApp/.../feature/transaction/ui/component/TransactionItem.kt` |
| Пустое состояние | `composeApp/.../feature/transaction/ui/component/TransactionsEmpty.kt` |
| Группировка по дням | `composeApp/.../feature/transaction/ui/model/TransactionsByDays.kt` |

## 0. Точка входа и видимость

* **Точка входа:** «History» с главного экрана.
* **Показывается:** только авторизованному. Заголовок панели — `History`, стрелка «назад» есть
  (`History` не корневой экран).

## 1. Состояния

`TransactionListUiState` реализует общий `CommonUiState<TransactionsByDays>` — `load()`,
`showError()`, `showData()` вместо трёх независимых флагов:

* `loading` — загрузка;
* `data` пуст — пустое состояние;
* `data` заполнен — лента по дням, `dayBalances` — тот же баланс на конец дня, что на главном;
* `monthTitle` / `monthChange` / `balanceToday` — «August so far»: сколько накопилось за текущий
  месяц и каков баланс сегодня;
* `selectedTransactions` + `showDeleteDialog` — выбор и удаление;
* `showingCacheFrom != null` — показан кэш, с отметкой времени;
* `unreachable != null` — показать нечего;
* `errorMessage` — прочие отказы.

## 2. Обращения к API

| Вызов | Контракт | Документ |
|---|---|---|
| `GET /transactions` | `TransactionResource` | [endpoint-transactions](../api/endpoint-transactions.md) |
| `DELETE /transactions/{id}` | `TransactionResource.ById` | [endpoint-transactions](../api/endpoint-transactions.md) |

## 3. Инициализация

Входных параметров нет; список запрашивается при открытии. Разбор ответов — тот же, что у
[screen-main](screen-main.md) §3, включая обе ветки «нет сети».

## 4. Элементы

### 4.1. Итог месяца

`monthTitle` + `monthChange` + `balanceToday`. Это единственное, чем экран отличается от ленты
главного: там смотрят вперёд, здесь — на уже прошедшее.

### 4.2. Лента по дням

Та же группировка и тот же `dayBalances`, что на главном, — считаются по всей симуляции.

### 4.3. Выбор и удаление

Как на главном: долгое нажатие включает выбор, удаление идёт через подтверждение.

## 5. Навигация

* правило ──▶ `screen-transaction-form` (правка)
* «назад» ──▶ `screen-main`
