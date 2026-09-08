---
id: shared
title: ":shared — контракт обмена"
type: service
repo_url: https://github.com/youndie/mani-kotlin-fullstack
module: ":shared"
tech_stack: [Kotlin Multiplatform, ktor-resources, kotlinx.serialization, kotlinx.datetime]
owner: unassigned
depends_on: []
publishes:
  - klib/jar внутри сборки (наружу не публикуется)
---

# :shared — контракт обмена

## 1. Ответственность

Единственное, что здесь лежит: **описание того, чем клиент и сервер обмениваются**. Классы
`@Resource` (они же маршрутизируют запрос на сервере и собирают URL на клиенте), модель домена,
сериализаторы и пара чистых функций над моделью.

Таргеты: `android`, `ios`, `jvm`, `wasmJs`, `linuxX64` — то есть все, какие есть у продукта.

Чего здесь **нет намеренно**:

* **адреса сервера.** Он уехал в `composeApp/.../Constants.kt`: серверу адрес самого себя не нужен,
  а модуль, который называется контрактом, должен им быть;
* **`userId`.** Владелец записи — понятие сервера. Клиенту он не нужен и в контракт не входит; на
  сервере для этого есть `TransactionRecord` (см. [server-common](server-common.md));
* **бизнес-правил.** Проверки лежат на сервере (`Rules.kt`, `Credentials.kt`) — за формой клиента
  стоит открытый HTTP.

## 2. Контракты

Ресурсы, по одному на предметную область:

| Ресурс | Путь |
|---|---|
| `AuthResource`, `AuthResource.Refresh` | `/auth`, `/auth/refresh` |
| `UserResource` | `/users` |
| `TransactionResource`, `TransactionResource.ById` | `/transactions`, `/transactions/{id}` |
| `CategoryResource` | `/categories` |
| `CurrencyResource` | `/currency` |
| `DemoResource` | `/demo` |
| `HealthResource`, `HealthResource.Ready` | `/health`, `/health/ready` |

Полный разбор с кодами ответов — в слое api: [endpoint-auth](../api/endpoint-auth.md),
[endpoint-transactions](../api/endpoint-transactions.md).

## 2a. Код

| Файл | Что там |
|---|---|
| `shared/src/commonMain/kotlin/io/github/youndie/mani/feature/` | по каталогу на предметную область: ресурс + DTO |
| `shared/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/Transaction.kt` | модель правила: сумма, знак, период, дата, категория |
| `shared/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/WithId.kt` | «у записи есть выданный сервером id» — реализуют `Transaction` и `Category` |
| `shared/src/commonMain/kotlin/io/github/youndie/mani/utilz/bigdecimal/` | `BigDecimalSerializable` и его сериализатор |
| `shared/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/TransactionsOperations.kt` | развёртка правил в календарь и симуляция баланса |
| `shared/src/commonTest/kotlin/` | `TransactionOperationsTest`, `DemoSeedTest` |

## 3. Как это устроено

**Один класс — два употребления.** `@Resource`-класс на сервере разбирает путь
(`post<TransactionResource.ById> { path -> ... }`), а на клиенте тот же класс строит URL
(`httpClient.post(AuthResource.Refresh())`). Отсюда свойство, ради которого модуль существует:
переименовать путь и забыть поправить вторую сторону нельзя — сторона одна.

**Симуляция живёт здесь, а не на сервере или клиенте.** `TransactionsOperations.kt` разворачивает
правила в календарь и считает баланс по дням. Это нужно и клиенту (герой главного экрана, график,
предпросмотр «на сколько сдвинется день обнуления» в форме), и витрине, которая рисует график по
демонстрационному набору **до всякого входа**. Общий модуль — единственное место, где обе стороны
получают один и тот же ответ.

## 4. Зависимости

| Вид | Что | Зачем |
|---|---|---|
| Библиотека | `io.ktor:ktor-resources` | типизированные пути |
| Библиотека | `kotlinx.serialization` | JSON обмена |
| Библиотека | `kotlinx.datetime` | `LocalDate` в модели |
| Библиотека | `com.ionspin.kotlin:bignum` | суммы, без потерь на double |

## 5. Инфраструктура и выкат

Отдельно не публикуется и не разворачивается: собирается внутрь потребителей — сервера обеих
сборок и всех клиентских таргетов.

## 6. Локальная сборка

```bash
./gradlew :shared:jvmTest
```

## 7. Конфигурация

Нет. Модуль ничего не читает из окружения.

## 8. Особенности

* **`Category.default` — часть контракта, а не заглушка клиента.** `Category("0", "Default")`
  объявлена в `Transaction.kt` и подставляется сервером, когда `categoryId` записи не нашёлся среди
  категорий владельца. Удалённая категория выглядит как «Default», а не как ошибка; см. открытый
  вопрос 1 в [research-architecture](../research/research-architecture.md).
* **Знак задаёт `income`, а не сумма.** `amountSigned` умножает на −1, поэтому отрицательная сумма
  с `income = false` дала бы **плюс** в прогнозе. Сервер такое отвергает (`Rules.kt`), но модель
  сама по себе это позволяет.
