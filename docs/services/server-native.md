---
id: server-native
title: ":server-native — нативный бинарь, образ стенда"
type: service
repo_url: https://github.com/youndie/mani-kotlin-fullstack
module: ":server-native"
tech_stack: [Kotlin/Native linuxX64, Ktor CIO, mongkn, libmongoc, Koin]
owner: unassigned
depends_on:
  - server-common
  - shared
  - MongoDB
  - mongkn
publishes:
  - "ghcr.io/youndie/mani-kotlin-fullstack:<mani.version>.<номер прогона CI>"
---

# :server-native — нативный бинарь, образ стенда

## 1. Ответственность

Сборка сервера под `linuxX64` — **та, что работает на стенде**. Своего в ней: хранилище на
[mongkn](https://github.com/youndie/mongkn), отдача статики вручную и точка входа `main()`. Всё
остальное — из [server-common](server-common.md).

Таргет ровно один, и не по выбору: столько публикует mongkn. На macOS модуль компилируется, но не
линкуется, поэтому разработка на маке идёт через [server](server.md).

## 2. Контракты

Те же, что у [server-common](server-common.md), плюс маршрут статики `GET /{path...}`,
регистрируемый **последним** — он ловит всё оставшееся и потому API не перехватывает.

## 2a. Код

| Файл | Что там |
|---|---|
| `server-native/src/linuxX64Main/kotlin/io/github/youndie/mani/Main.kt` | `main()` и `Application.maniModule(config)` — сборка приложения, поднимаемая и в тестах |
| `server-native/src/linuxX64Main/kotlin/io/github/youndie/mani/MongknStorageModule.kt` | DI хранилища на mongkn |
| `server-native/src/linuxX64Main/kotlin/io/github/youndie/mani/db/DbModel.kt` | форма документа: `StringAsBsonObjectId`, `BigDecimalAsBsonDecimal128` |
| `server-native/src/linuxX64Main/kotlin/io/github/youndie/mani/feature/*/data/` | реализации портов |
| `server-native/src/linuxX64Main/kotlin/io/github/youndie/mani/web/WebAssets.kt` | сканирование каталога статики на старте |
| `server-native/src/linuxX64Main/kotlin/io/github/youndie/mani/web/WebRoutes.kt` | отдача файлов, ETag, `Cache-Control`, готовые `.gz` |
| `server-native/Dockerfile` | образ: сборка статики + рантайм |
| `server-native/src/linuxX64Test/kotlin/io/github/youndie/mani/TestMongo.kt` | обвязка тестов, работающих в своих базах |

## 3. Как это устроено

**Бинарь линкуется снаружи и в образ только копируется.** Линковка Kotlin/Native внутри docker шла
бы без кеша Gradle и занимала минуты на каждую сборку образа. Отсюда двухшаговый выкат в
`deploy.yml`: сначала `linkReleaseExecutableLinuxX64` и `wasmJsBrowserDistribution`, потом
`docker build`.

**Версия базового образа привязана к сборочной машине.** Бинарь слинкован с той `libmongoc`, что
стояла при линковке; у Ubuntu 24.04 это 1.26. Soname (`libmongoc-1.0.so.0`) у веток общий, поэтому
подмена **не ловится ни сборкой, ни стартом** — она проявится отсутствующим символом на первом
обращении к Mongo. Раннер CI, раннер выката и `FROM` в Dockerfile обязаны быть одной версии
дистрибутива; меняете одно — меняйте оба.

**Статика читается один раз на старте**, а не на каждый запрос: файлы вшиты в образ и за время
жизни процесса не меняются. Пустой `MANI_WEB_ROOT` — сервер без фронтенда, так удобно поднимать его
в тестах.

**Сжатия на лету нет и быть не может:** `ktor-server-compression` публикуется только под JVM.
Вместо него в образе рядом с каждым файлом лежит готовый `.gz`, сжатый один раз на сборке
(`Dockerfile`, стадия `web`), и отдаётся он, если клиент принимает gzip.

**`Cache-Control: immutable` ставится по имени файла, а не по расширению.** Правило «`.wasm` —
значит immutable» неверно: рядом с `6e23e5428398b92da386.wasm` в бандле лежит `skiko.wasm` с
постоянным именем. Проверяется, что имя — не меньше 16 шестнадцатеричных цифр: столько webpack даёт
именно тем файлам, которые пересобираются под новым именем при любой правке
(`WebRoutes.kt:34`, тест `WebCachingTest`).

**Логгера нет.** `koin-logger-slf4j` — JVM-only, `CallLogging` — тоже. Диагностика идёт через
`println` в stdout, который в контейнере и есть лог.

## 4. Зависимости

| Вид | Что | Зачем |
|---|---|---|
| Модуль | [server-common](server-common.md) | весь сервер, кроме хранилища |
| База | MongoDB | `MONGO_HOST`, `MONGO_DATABASE` |
| Библиотека | [mongkn](https://github.com/youndie/mongkn) | драйвер MongoDB для Kotlin/Native (свой: официального нет) |
| Системная | `libmongoc-dev`, `libbson-dev` — на сборке; `libmongoc-1.0-0t64` — в образе | C-драйвер, поверх которого работает mongkn |
| Библиотека | Ktor CIO | HTTP |

## 5. Инфраструктура и выкат

* **Образ:** `ghcr.io/youndie/mani-kotlin-fullstack:<mani.version>.<номер прогона>`
* **Манифест:** `.k8s-templates/deployment.yaml` (шаблон, `envsubst` подставляет версию и номер
  прогона; результат кладётся в `.k8s/` и применяется `kubectl apply`)
* **Живость:** `GET /health` — базу **не трогает** намеренно: проба живости, зависящая от базы,
  превращает её падение в перезапуск всех подов
* **Готовность:** `GET /health/ready` — базу спрашивает: под, который её не видит, не должен
  получать трафик. `initialDelaySeconds` не нужен, бинарь отвечает через 87 мс после старта
* **Ресурсы:** requests `50m`/`64Mi`, limits `1`/`128Mi`
* **Выкат:** `.github/workflows/deploy.yml`, по завершении зелёного прогона `Test` на `main`, плюс
  ручной `workflow_dispatch`

Замер на собранном образе: старт до первого ответа 87 мс, 42 МиБ в покое, пик 45 МиБ, образ 213 МБ,
бинарь 13 МБ. **Без нагрузки и на одной реплике.**

## 6. Локальная сборка

Только на Linux. Нужен C-драйвер:

```bash
sudo apt-get install -y libmongoc-dev libbson-dev
```

```bash
./gradlew :server-native:linkReleaseExecutableLinuxX64 :composeApp:wasmJsBrowserDistribution
```

```bash
docker build -f server-native/Dockerfile -t mani-native .
```

Тестам нужен настоящий `mongod` — то, что они ищут, не поднимает ошибок:

```bash
docker run -d --name mani-mongo -p 27017:27017 mongo:8
```

```bash
./gradlew :server-native:linuxX64Test :server-native:linuxX64ReleaseTest
```

**Релизный прогон не опционален.** Kotlin/Native в релизе не вставляет проверок приведения типов, и
код, падающий в отладке ловимым исключением, в релизе уходит в неопределённое поведение. В образ
едет релизный бинарь.

## 7. Конфигурация

Те же переменные, что у [server-common](server-common.md). В образе заданы `MANI_WEB_ROOT` и `PORT`
(`Dockerfile`); в манифесте — `MONGO_HOST` и `JWT_SECRET` из секрета `mani-backend`.

## 8. Особенности

* **`ca-certificates` в образе нет причины по `ldd`.** Пакет ставится отдельной строкой: это не
  библиотека, а набор корневых сертификатов, которых в `ubuntu:24.04` нет вовсе. Наружу по https
  mani сегодня не ходит, но первый такой вызов иначе выглядел бы как тихий отказ.
* **Выход за корень каталога статики отсекается проверкой на `..`** (`WebRoutes.kt:57`), а
  неизвестный путь отдаёт `index.html` — это SPA, дальше маршрутизирует само приложение.
