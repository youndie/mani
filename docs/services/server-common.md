---
id: server-common
title: ":server-common — сервер, кроме хранилища"
type: service
repo_url: https://github.com/youndie/mani-kotlin-fullstack
module: ":server-common"
tech_stack: [Kotlin Multiplatform, Ktor server, Koin, cryptography-kotlin]
owner: unassigned
depends_on:
  - shared
publishes:
  - klib/jar внутри сборки (наружу не публикуется)
---

# :server-common — сервер, кроме хранилища

## 1. Ответственность

**Весь сервер, кроме обращений к базе.** Маршруты, проверка ввода, выдача и проверка токенов,
хеширование паролей, конфигурация, плагины Ktor, состав DI-графа. Компилируется в `jvm` и
`linuxX64` — и то, что здесь лежит, обе сборки исполняют **одним кодом**.

Чего здесь нет: реализаций хранилища. Официальный драйвер MongoDB существует только на JVM, mongkn
— только под linuxX64, и общего типа документа у них нет. Поэтому в общей части объявлены только
порты — интерфейсы `UserRepository`, `TokenRepository`, `TransactionRepository`,
`CategoryRepository`, `StorageHealth`, — а реализации приносят [server](server.md) и
[server-native](server-native.md).

Правило, из которого это следует: **всё, что не является обращением к базе, обязано быть общим.**
Каждая лишняя пара `expect/actual` — две реализации, которые разъедутся молча.

## 2. Контракты

Пути и DTO — в [shared](shared.md). Слой api разбирает их по кодам ответов:
[endpoint-auth](../api/endpoint-auth.md), [endpoint-transactions](../api/endpoint-transactions.md).

Ярусы доступа:

| Ярус | Как ставится | Какие маршруты |
|---|---|---|
| открытый | вне `authenticate` | `POST /auth`, `POST /auth/refresh`, `POST /users`, `POST /demo`, `GET /health`, `GET /health/ready`, `GET /currency` |
| Bearer access-токен | `authenticate(jwtConfig.name)` | `/transactions`, `/transactions/{id}`, `/categories`, `/categories/{id}` |

Проверяется тестами `protected routes require a valid token` и
`a refresh token opens no door and an access token refreshes nothing`.

## 2a. Код

| Файл | Что там |
|---|---|
| `server-common/src/commonMain/kotlin/io/github/youndie/mani/ManiApp.kt` | состав DI (`coreModule`), плагины (`configureManiPlugins`), проверка токенов (`configureManiAuth`), сборка маршрутов (`maniApiRouting`) |
| `server-common/src/commonMain/kotlin/io/github/youndie/mani/config/ManiConfig.kt` | вся конфигурация из ENV + `expect fun readEnv` |
| `server-common/src/commonMain/kotlin/io/github/youndie/mani/security/TokenService.kt` | выпуск и проверка JWT |
| `server-common/src/commonMain/kotlin/io/github/youndie/mani/security/ManiAuth.kt` | свой Bearer-провайдер вместо `ktor-server-auth-jwt` |
| `server-common/src/commonMain/kotlin/io/github/youndie/mani/security/Base64Url.kt` | base64url, hex, сравнение за постоянное время |
| `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/` | по каталогу на предметную область: `<X>Routing.kt` + `data/` с портами |
| `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/Rules.kt` | правила приёмки правила и категории |
| `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/user/Credentials.kt` | правила регистрации |
| `server-common/src/jvmMain/`, `server-common/src/linuxX64Main/` | ровно два `actual`: `readEnv` и `serverBuildKind` |

## 3. Как это устроено

**Порядок сборки приложения фиксирован, и это не стиль.** `configureManiPlugins` → `Koin` →
`configureManiAuth(config, get<TokenService>())` → маршруты. Проверка токенов ставится **после**
Koin намеренно: `TokenService` берётся из графа, а не собирается вторым экземпляром на том же
секрете — иначе секрет пришлось бы протаскивать во второе место. Обе сборки повторяют этот порядок
(`server/.../Application.kt`, `server-native/.../Main.kt`).

**`StatusPages` вместо логгера.** Логгера в общей части нет ни у одной сборки (`CallLogging` —
JVM-only), поэтому отказ печатается через `println` в stdout, который в контейнере и есть лог. Без
этого нативная сборка отвечала 500, не оставляя следа. Разбор причин:

* `CancellationException` пробрасывается дальше — отменённый клиентом запрос не отказ и не 500;
* `BadRequestException`, `IllegalArgumentException`, `SerializationException` → `400 Malformed
  request` общим текстом: подробности рассказали бы об устройстве сервера больше, чем нужно;
* остальное → 500 плюс строка в stdout.

**`Json` без `isLenient`.** Послабление разрешало телу приходить без кавычек, то есть сервер брался
угадывать намерение отправителя. Наши клиенты пишут JSON сериализатором — послабление обслуживало
только того, кто ходит мимо них.

**CORS только в режиме разработки.** В стенде фронтенд отдаёт тот же сервер, разрешать чужие
источники незачем (`MANI_DEVELOPMENT`).

**Проверки ввода стоят на сервере, а не только в форме.** Форма клиента до половины этого не
допускает, и всё же правило живёт здесь: форма — удобство, а не граница, за ней открытый HTTP.
Тексты отказов возвращаются наружу и показываются человеку, поэтому они на английском и говорят,
что исправить.

## 4. Зависимости

| Вид | Что | Зачем |
|---|---|---|
| Модуль | [shared](shared.md) | ресурсы и DTO |
| Библиотека | Ktor server (`resources`, `content-negotiation`, `status-pages`, `cors`, `auth`) | HTTP |
| Библиотека | Koin | DI, общий состав графа |
| Библиотека | `cryptography-kotlin` (`-prebuilt`) | HMAC-SHA256 и SHA-256 на обеих сборках; на native — OpenSSL, поэтому `libssl-dev` на сборочной машине не нужен |
| Библиотека | `com.auth0:java-jwt` — **только `jvmTest`** | эталон совместимости формата токена |

## 5. Инфраструктура и выкат

Своих нет: разворачивается внутри [server-native](server-native.md) (стенд) и
[server](server.md) (локально).

## 6. Локальная сборка

```bash
./gradlew :server-common:jvmTest
```

Нативный набор — на Linux, `mongod` для него не нужен (эти тесты в базу не ходят):

```bash
./gradlew :server-common:linuxX64Test
```

## 7. Конфигурация

Все ключи объявлены в одном месте — `ManiConfig.fromEnv()`. Список с умолчаниями не дублируется
здесь намеренно: он в `config/ManiConfig.kt` и в `README.md`, раздел «Configuration».

Стоит знать про два:

| Ключ | Что будет, если не задать |
|---|---|
| `JWT_SECRET` | случайный секрет на процесс + предупреждение в stdout; перезапуск разлогинивает всех |
| `MANI_WEB_ROOT` | фронтенд не отдаётся, работает только API |

## 8. Особенности

* **Токен без claim `kind` принимается как refresh.** Временное окно совместимости с датой снятия
  — см. §1.3 в [research-architecture](../research/research-architecture.md). Единственная вещь в
  коде, которая должна исчезнуть сама.
* **`currentUserId()` падает `error()`, а не отвечает 401.** Внутри `authenticate` ветка мертва:
  запрос без принципала туда не доходит. Отсутствие принципала здесь означает **незащищённый
  маршрут**, то есть ошибку проводки, и она обязана быть громкой. Прежняя версия возвращала пустую
  строку, и та ушла бы в хранилище как владелец. Ловушку сторожит тест
  `a route outside authenticate cannot ask who is calling`.
* **`credentialsProblem` проверяет только регистрацию.** Вход обязан принимать имя, каким бы оно ни
  было: у заведённых до этих правил имена им не подчиняются, и запрет на входе выселил бы
  существующих пользователей.
* **Имена, начинающиеся с префикса демо-песочницы, зарезервированы.** Иначе можно было завести
  аккаунт, который через сутки унесёт уборщик песочниц.
* **`UserResource.CurrentUserResource` (`/users/current`) не реализован ничем.** Класс объявлен в
  контракте (`shared/.../feature/user/UserResource.kt:9`), но обработчика в `UserRouting.kt` нет,
  и ни один клиент его не зовёт — поиск по `CurrentUserResource` во всём дереве даёт одно
  вхождение, само объявление. То есть путь `/users/current` отдаёт то же, что любой неизвестный
  путь. Контракт обещает больше, чем сервер умеет; проверено 08.09.2026.
