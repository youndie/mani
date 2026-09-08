---
id: screen-transaction-form
title: Форма правила (создание и правка)
type: client_screen
platform: [android, ios, desktop, web]
status: active
entry:
  all: "маршруты ManiScreen.Add (создание) и TransactionRoute(id) (правка)"
parent_feature: feature-transactions
calls_api:
  - endpoint-transactions
  - endpoint-categories
source: composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/ui/
---

# Экран: форма правила

Один документ на два маршрута: создание и правка — одна композиция и один базовый ViewModel,
различаются загрузкой исходной записи и вызовом на сохранении.

| Маршрут | ViewModel | Заголовок | Запрос на сохранении |
|---|---|---|---|
| `Add` | `AddTransactionViewModel` | `New rule` | `POST /transactions` |
| `TransactionRoute(id)` | `EditTransactionViewModel` | `Edit rule` | `PATCH /transactions/{id}` |

## 0a. Код

| Что | Файл |
|---|---|
| Общий ViewModel | `composeApp/.../feature/transaction/ui/BaseTransactionViewModel.kt` |
| Создание / правка | `.../ui/AddTransactionViewModel.kt`, `.../ui/EditTransactionViewModel.kt` |
| Состояние | `composeApp/.../feature/transaction/ui/model/TransactionUiState.kt` |
| Композиция | `composeApp/.../feature/transaction/ui/component/TransactionComponent.kt` |
| Выбор даты | `.../ui/component/TransactionDatePicker.kt` |
| Формат суммы при вводе | `.../ui/utils/CurrencyVisualTransformation.kt` |
| Подписи периодов | `.../ui/model/PeriodStringResource.kt` |
| Правила сервера | `server-common/.../feature/transaction/Rules.kt` |

## 0. Точка входа и видимость

* **Точка входа:** «+» на главном экране (создание); нажатие на правило в ленте главного или
  истории (правка).
* **Показывается:** только авторизованному.

**Входные параметры (правка):**

| Параметр | Тип | Откуда |
|---|---|---|
| `id` | `String` | `TransactionRoute` в аргументах навигации |

## 1. Состояния

Поля `TransactionUiState`:

* `amount`, `income`, `period`, `comment`, `date`, `until`, `category` — сам ввод;
* `periods` — четвёрка из макета (`OneTime`, `TwoWeek`, `Month`, `Year`), остальное под «More»;
  `periodsExpanded` = список отличается от умолчания;
* `valid` — сумма без ошибки, непустая, и дата выбрана;
* `amountError` — под самим полем: `this is not an amount` либо `an amount is required` (ноль);
* `futureInformation` — что это правило даст в будущем;
* `runsOutShift` — **на сколько сдвинется день обнуления**, с флагом `worse`;
* `loading` — сохранение идёт;
* `errorMessage` — отказ сервера;
* `success` — сохранено, экран закрывается;
* `edit` — режим правки.

## 2. Обращения к API

| Вызов | Контракт | Документ |
|---|---|---|
| `POST /transactions` | `TransactionResource` | [endpoint-transactions](../api/endpoint-transactions.md) |
| `PATCH /transactions/{id}` | `TransactionResource.ById` | [endpoint-transactions](../api/endpoint-transactions.md) |
| `GET /categories`, `POST /categories`, `DELETE /categories/{id}` | `CategoryResource` | [endpoint-categories](../api/endpoint-categories.md) |

## 3. Инициализация

| Вызов | Когда | Результат |
|---|---|---|
| чтение записи из уже загруженного списка | правка | заполняет форму через `TransactionUiState(transaction, currency)` |
| `GET /categories` | всегда | чипы категорий |
| `GetCurrentCurrencyUseCase` | всегда | формат сумм — **из локальных `Settings`, а не из сети** ([endpoint-currencies](../api/endpoint-currencies.md)) |

Отдельного маршрута «одно правило по id» нет — запись берётся из списка.

## 4. Элементы

### 4.1. Сумма

Ввод форматируется на лету (`CurrencyVisualTransformation`). Ошибка показывается **под полем**, а
не в общем сообщении внизу: неактивная кнопка без объяснения оставляла человека гадать, чего от
него ждут. Ноль отвергается здесь же — правило на ноль ничего не сдвигает в прогнозе.

### 4.2. Доход или расход

Расход по умолчанию: их вносят чаще. Знак задаёт **этот флаг**, а не введённая сумма.

### 4.3. Период

Четыре чипа из макета плюс «More» с остальными: `Day`, `Week`, `ThreeMonth`, `HalfYear`.

### 4.4. Даты `date` и `until`

`date` обязательна: правило без неё не разворачивается в календарь, и кнопка «Create» при одной
введённой сумме приглашала сохранить то, что сохранить нельзя. `until` необязательна; раньше даты
начала сервер такую пару не принимает.

### 4.5. Категория

Чипы; заводится и удаляется прямо здесь. Имя не пустое и не длиннее 64 символов — проверяет сервер.

### 4.6. Предпросмотр сдвига

`runsOutShift` — то, ради чего форма отличается от обычной формы записи: она говорит, **на сколько
раньше кончатся деньги**, до сохранения. `worse` отделяет «раньше» от «позже»: две разные новости,
и красным помечать надо только первую. У дохода отметки об ухудшении нет.

### 4.7. Кнопка сохранения

Активна при `valid`.

| Случай | Обработка | Состояние |
|---|---|---|
| `201` / `200` | список перечитывается | `success = true`, экран закрывается |
| `400` | текст сервера в `errorMessage` | `loading = false` |
| прочее | текст отказа | `loading = false` |

## 5. Навигация

* сохранено ──▶ назад (`popBackStack`), на экран, с которого пришли
* «назад» ──▶ туда же, без сохранения
