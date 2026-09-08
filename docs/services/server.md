---
id: server
title: ":server — JVM-сборка"
type: service
repo_url: https://github.com/youndie/mani-kotlin-fullstack
module: ":server"
tech_stack: [Kotlin/JVM, Ktor CIO, MongoDB Kotlin Driver, Koin]
owner: unassigned
depends_on:
  - server-common
  - shared
  - MongoDB
publishes:
  - локальный образ через `publishImageToLocalRegistry`
---

# :server — JVM-сборка

## 1. Ответственность

Сборка сервера под JVM. Своего в ней **только хранилище и отдача статики**: реализации портов на
официальном драйвере MongoDB плюс `staticResources` из ресурсов jar'а. Всё остальное берётся из
[server-common](server-common.md).

Это сборка для разработки: единственная, что собирается **на macOS**, где нативный таргет не
линкуется вовсе. На стенде работает не она, а [server-native](server-native.md).

## 2. Контракты

Те же, что у [server-common](server-common.md) — маршруты общие. Собственных маршрутов нет, кроме
отдачи статики.

## 2a. Код

| Файл | Что там |
|---|---|
| `server/src/main/kotlin/io/github/youndie/mani/Application.kt` | точка входа: `EngineMain`, порядок сборки приложения |
| `server/src/main/kotlin/io/github/youndie/mani/Routing.kt` | `maniApiRouting()` + отдача wasm-приложения |
| `server/src/main/kotlin/io/github/youndie/mani/MongoStorageModule.kt` | DI хранилища: клиент, база, репозитории, `StorageHealth` |
| `server/src/main/kotlin/io/github/youndie/mani/feature/*/data/` | реализации портов на официальном драйвере |
| `server/src/main/kotlin/io/github/youndie/mani/feature/*/data/*Db.kt` | форма документа: `@BsonId val id: ObjectId`, `amount: java.math.BigDecimal` |
| `server/src/main/kotlin/io/github/youndie/mani/utilz/wasmJsApp.kt` | статика из ресурсов jar'а |
| `server/src/main/resources/application.conf` | читает только `EngineMain`, и только порт |
| `server/src/test/kotlin/ManiTestServer.kt` | общая обвязка тестов сборки |

## 3. Как это устроено

**`application.conf` остался ради одной вещи — порта.** `EngineMain` читает его сам, и это
JVM-only механизм. Всё остальное берётся из ENV теми же именами, что у нативной сборки
(`ManiConfig.fromEnv()`), поэтому две сборки конфигурируются **одинаково**, а не по-разному.

**Форма документа в базе — та же, что у нативной сборки, но достигается иначе.** Здесь её задают
кодеки официального драйвера: `@BsonId val id: ObjectId` даёт `_id` как `ObjectId`, а
`java.math.BigDecimal` драйвер пишет как `decimal128`. У нативной сборки то же самое делают свои
сериализаторы (`StringAsBsonObjectId`, `BigDecimalAsBsonDecimal128`). Расхождение здесь ничего не
роняет — запрос просто не находит существующие документы, — поэтому сторожат его тесты, смотрящие
на **сырой документ**, а не на результат `find`.

## 4. Зависимости

| Вид | Что | Зачем |
|---|---|---|
| Модуль | [server-common](server-common.md) | весь сервер, кроме хранилища |
| База | MongoDB | `MONGO_HOST`, `MONGO_DATABASE` |
| Библиотека | MongoDB Kotlin Driver (официальный) | доступ к базе |
| Библиотека | Ktor CIO + `ktor-server-*` (JVM) | HTTP, статика, логирование |
| Библиотека | `koin-logger-slf4j`, logback | логи (JVM-only, у нативной сборки их нет) |

## 5. Инфраструктура и выкат

**На стенд не едет.** Стенд разворачивает образ из [server-native](server-native.md).

Локальный образ:

```bash
./gradlew publishImageToLocalRegistry
docker compose up -d
```

`GET /health` этой сборки отвечает `{"build": "jvm", ...}` — по этому полю видно, какая сборка
ответила.

## 6. Локальный запуск

Порядок из `README.md`: собрать образ, поднять рядом с MongoDB, открыть
[http://localhost:8080/](http://localhost:8080/) и нажать **Try the demo**.

Тесты:

```bash
./gradlew :server:test
```

## 7. Конфигурация

Те же переменные, что у [server-common](server-common.md) (`ManiConfig.fromEnv()`), плюс порт из
`server/src/main/resources/application.conf`, который читает `EngineMain`.

## 8. Особенности

* **`docker-compose.yaml` не задаёт `JWT_SECRET`.** Это не упущение: без переменной сервер
  подписывает случайным секретом на процесс и пишет об этом в stdout. Перезапуск контейнера
  разлогинивает всех — ожидаемое поведение локального стенда, а не поломка.
* **Режим разработки в этой сборке выключен по умолчанию.** CORS ставится только при
  `MANI_DEVELOPMENT=true`.
