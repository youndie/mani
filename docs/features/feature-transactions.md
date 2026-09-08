---
id: feature-transactions
title: Правила бюджета и прогноз
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
  - screen-main
  - screen-history
  - screen-transaction-form
api:
  - endpoint-transactions
tags: [core, forecast]
---

# Правила бюджета и прогноз

## 1. Обзор

Это ядро продукта. Человек заводит **правила** — «зарплата, 1-го числа, каждый месяц»,
«подписка, 15-го, каждый месяц», «билеты, 3 марта, разово», — а приложение разворачивает их в
календарь и отвечает на один вопрос: **когда деньги кончатся**.

Отсюда всё остальное. Лента — не история трат, а будущее вперемешку с прошлым, с балансом на конец
каждого дня. График — та же симуляция, нарисованная линией, с отметкой дня перехода через ноль.
Форма правила показывает не только поля, но и **насколько это правило сдвинет день обнуления** — до
того, как правило сохранено.

Продукт говорит о «правилах», код — о `Transaction`. Разовая трата тут частный случай: период
`OneTime`.

## 2. Правила

* Сумма строго больше нуля. Знак задаёт флаг `income`, а не сумма: отрицательная сумма с
  `income = false` дала бы **плюс** в прогнозе.
* Ноль не принимается: правило на ноль не двигает прогноз, то есть не делает того единственного,
  ради чего заводится.
* Дата окончания не может быть раньше даты начала — такое правило не развернулось бы ни в один
  день, и снаружи выглядело бы как исчезнувшая запись.
* Комментарий — не длиннее 200 символов, имя категории — не длиннее 64 и не пустое.
* Правило видит и правит **только его владелец**. Чужое и несуществующее дают один ответ `403`.
* При изменении идентификатор берётся **из пути**, а не из тела.
* Категория хранится в документе пользователя; в ответе она подставляется целиком. Не нашлась —
  подставляется `Category.default`.
* Проверки стоят на сервере, а не только в форме: за формой открытый HTTP.

## 3. Ход

```
экран ──GET /transactions──▶ список ──▶ кэш (Settings) ──▶ симуляция (:shared) ──▶ лента, график, герой
форма ──POST /transactions──▶ 201 ──▶ список перечитан
форма ──PATCH /transactions/{id}──▶ 200
лента ──DELETE /transactions/{id}──▶ 200

нет сети ──▶ кэш есть?  ──да──▶ лента с отметкой «снято тогда-то»
                        ──нет─▶ экран «сервер недоступен» с причиной и автоповтором
```

Симуляция считается **на клиенте**, кодом из [shared](../services/shared.md): один и тот же
`simulate()`/`toChartInternal()` обслуживает главный экран, историю, форму правила и график на
витрине — до всякого входа.

## 4. Код

| Сервис | Код |
|---|---|
| shared | `shared/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/Transaction.kt` — модель |
| shared | `shared/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/TransactionsOperations.kt` — развёртка в календарь и симуляция баланса |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/TransactionRouter.kt` — четыре маршрута и проверка владельца |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/Rules.kt` — правила приёмки |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/data/TransactionRepository.kt` — порт и `TransactionRecord` |
| server | `server/src/main/kotlin/io/github/youndie/mani/feature/transaction/data/` |
| server-native | `server-native/src/linuxX64Main/kotlin/io/github/youndie/mani/feature/transaction/data/` |
| composeApp | `composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/` — репозиторий, кэш, use case'ы, экраны |
| composeApp | `composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/main/` — главный экран и герой-прогноз |

## 5. Сценарии

### Scenario: правило заведено и сразу видно

* **Given:** вошедший пользователь.
* **When:** `POST /transactions` с годным правилом.
* **Then:** `201`, в теле — правило с выданным сервером `id` и подставленной категорией.
* **And:** `GET /transactions` возвращает его в списке.
* **Automated:** `ManiApiTest`

### Scenario: правило изменено и удалено

* **Given:** заведённое правило.
* **When:** `PATCH /transactions/{id}`, затем `DELETE /transactions/{id}`.
* **Then:** `200` на оба; после удаления записи в списке нет.
* **Automated:** `MongknStorageTest`

### Scenario: правило сохраняет свою категорию

* **Given:** у пользователя заведена категория.
* **When:** правило создано с ней и прочитано обратно.
* **Then:** в ответе категория та же, а не `Default`.
* **Automated:** `ManiApiTest`

### Scenario: продукт отказывается от правила, которого не может исполнить

* **Given:** вошедший пользователь.
* **When:** правило с суммой `0` либо с отрицательной суммой, либо с `until` раньше `date`, либо с
  комментарием длиннее 200 символов.
* **Then:** `400` с текстом, называющим, что исправить: `Amount must be greater than zero`,
  `The end date cannot be earlier than the start date`,
  `Comment must be at most 200 characters long`.
* **Automated:** `ManiApiTest`

### Scenario: чужое правило не переписать через `id` в теле

* **Given:** два пользователя, у каждого своё правило.
* **When:** первый шлёт `PATCH /transactions/<своё>` и кладёт в тело `id` чужой записи.
* **Then:** `id` из тела игнорируется, правится своя запись; чужая не тронута и владельца не
  меняет.
* **Automated:** `OwnershipTest`

### Scenario: обращение к чужому или несуществующему правилу

* **Given:** идентификатор, который пользователю не принадлежит либо не существует вовсе.
* **When:** `PATCH` или `DELETE` по нему.
* **Then:** `403` — **один и тот же** ответ в обоих случаях, чтобы по коду нельзя было узнать,
  какие идентификаторы заняты.
* **Automated:** `OwnershipTest`

### Scenario: испорченный идентификатор в пути

* **Given:** `{id}`, который не разбирается как `ObjectId`.
* **When:** любой маршрут с этим путём.
* **Then:** `400` с телом `Malformed request` — не `500`.
* **Automated:** `MalformedRequestTest`

### Scenario: неразбираемое тело

* **Given:** тело, которое не разбирается в `Transaction`.
* **When:** `POST /transactions`.
* **Then:** `400` с телом `Malformed request`.
* **Automated:** `MalformedRequestTest`

### Scenario: сети нет, но список известен

* **Given:** список правил уже загружался, кэш цел.
* **When:** запрос за списком не проходит.
* **Then:** показывается последний известный список **с отметкой, когда он снят**
  (`showingCacheFrom`), а не пустой экран.
* **Automated:** `TransactionsCacheTest`

### Scenario: сети нет и кэша нет

* **Given:** кэш пуст либо испорчен.
* **When:** запрос за списком не проходит.
* **Then:** показывается экран «Can't reach the server» с машинной причиной (код, адрес, время) и
  обратным отсчётом до автоповтора.
* **Automated:** `TransactionsCacheTest`

### Scenario: кэш говорит, когда он снят

* **Given:** данные пришли из кэша.
* **When:** экран собран.
* **Then:** в состоянии проставлен `showingCacheFrom` со временем снятия.
* **Automated:** `TransactionsViewModelCacheTest`

### Scenario: форма показывает, на сколько правило сдвинет день обнуления

* **Given:** открыта форма нового правила, в приложении уже есть правила.
* **When:** введены сумма и дата расхода.
* **Then:** под формой сказано, насколько раньше кончатся деньги, и это помечено как ухудшение
  (`RunsOutShift.worse`).
* **And:** для дохода отметки об ухудшении нет.
* **Automated:** `TransactionViewModelTest`

## 6. Вне охвата

* Категории как самостоятельная область (`/categories`, их создание и удаление) — документом не
  покрыты; код: `server-common/.../feature/category/`, `composeApp/.../feature/categories/`.
* Валюта (`/currency`) — сервер отдаёт зашитый список из двух значений.
* График как отдельный экран — рисуется внутри главного и витрины; вендоренный
  `compose-charts` описан в [composeApp](../services/composeApp.md).
* Фильтры ленты (`FiltersState`) — часть главного экрана, разобраны в
  [screen-main](../screens/screen-main.md).

## 7. Особенности

* **`POST /transactions` умеет ответить `404`.** Ветка «создали и не нашли по своему же id»
  существует (`TransactionRouter.kt:44`), обычным путём не воспроизводится и тестом не покрыта.
  Снаружи выглядит как «ничего не произошло», хотя запись создана.
* **Удалённая категория выглядит как «Default», а не как ошибка.** `toTransaction()` подставляет
  `Category.default`, когда `categoryId` не нашёлся среди категорий владельца. Тихая деградация;
  решения по ней в тексте нет — открытый вопрос 1 в
  [research-architecture](../research/research-architecture.md).
* **Отдельного маршрута «одно правило по id» нет.** Экран правки берёт запись из уже загруженного
  списка; при холодном открытии по ссылке (в браузере это настоящий случай) сначала грузится весь
  список.
* **Пределы длины серверу известны, клиенту — нет.** `MAX_COMMENT_LENGTH = 200` и
  `MAX_CATEGORY_NAME_LENGTH = 64` — приватные константы `Rules.kt`, в контракт они не входят. Форма
  не мешает набрать больше; человек узнаёт о пределе из текста отказа.
* **Список приходит целиком, без страниц.** Для десятка правил это верно, и на этом построен кэш.
