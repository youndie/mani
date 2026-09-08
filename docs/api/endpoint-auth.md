---
id: endpoint-auth
title: Регистрация, вход, обновление сессии
type: api_endpoints
status: active
services:
  - server-common
  - server
  - server-native
contract_source:
  - "mani-kotlin-fullstack::shared AuthResource, UserResource, DemoResource"
parent_feature: feature-auth
---

# API: регистрация, вход, обновление сессии

> **Полный перечень маршрутов входа** — открытых и защищённых. Формы путей и тел живут в
> `@Resource`- и DTO-классах модуля [shared](../services/shared.md), названных в
> `contract_source`; здесь — коды ответов и ярусы доступа, потому что в коде они разбросаны по
> обработчикам.
>
> Сгенерированной схемы у продукта нет: аннотации smiley4 под Kotlin/Native не живут, а `swagger()`
> к маршрутизации не подключался ни разу. Поэтому колонка «в схеме?» отсутствует — этот документ и
> есть справочник.

## Маршруты — все

| Метод и путь | Ярус | Назначение |
|---|---|---|
| `POST /users` | открытый | регистрация |
| `POST /auth` | открытый | вход по имени и паролю, выдаёт пару токенов |
| `POST /auth/refresh` | открытый (сам предъявляет refresh-токен) | обмен refresh-токена на новую пару |
| `GET /users/current` | — | **не реализован**, см. «Особенности» |

Ещё один вход в приложение — `POST /demo`: он тоже выдаёт `Tokens`, но принадлежит песочнице и
разобран в [endpoint-demo](endpoint-demo.md).

Ярус «Bearer access» — это `authenticate(jwtConfig.name)` вокруг маршрута; провайдер требует токен
с claim `kind = "access"` (`server-common/.../security/ManiAuth.kt:32`).

## Обработчики

| Маршрут | Обработчик |
|---|---|
| `POST /users` | `server-common/.../feature/user/UserRouting.kt:15` |
| `POST /auth` | `server-common/.../feature/auth/AuthRouting.kt:14` |
| `POST /auth/refresh` | `server-common/.../feature/auth/AuthRouting.kt:24` |
| проверка Bearer-заголовка | `server-common/.../security/ManiAuth.kt` |
| выпуск и проверка токена | `server-common/.../security/TokenService.kt` |
| логин и обновление | `server-common/.../feature/auth/data/AuthService.kt` |
| правила регистрации | `server-common/.../feature/user/Credentials.kt` |

## Тела запросов и ответов

Не копируются — поля меняются, путь нет:

| Что | Класс |
|---|---|
| тело `POST /users` и `POST /auth` | `shared/.../feature/auth/LoginParams.kt` |
| тело `POST /auth/refresh` | `shared/.../feature/auth/RefreshParams.kt` |
| ответ `POST /auth` и `/auth/refresh` | `shared/.../feature/auth/Tokens.kt` |

## Ответы

### `POST /users`

| Условие | Код | Тело |
|---|---|---|
| создан | `201` | пусто |
| имя короче 3 или длиннее 32 символов | `400` | `Name must be 3 to 32 characters long` |
| в имени не только `a–z`, `0–9`, `-`, `_` | `400` | `Name may contain lowercase letters, digits, hyphen and underscore only` |
| имя начинается с `demo-` | `400` | `Names starting with "demo-" are reserved for the demo` |
| пароль короче 8 символов | `400` | `Password must be at least 8 characters long` |
| имя занято | `400` | `User already exist` |
| хранилище не записало | `500` | пусто |

Проверка формы стоит **до** обращения к базе: отказ по вводу незачем оплачивать запросом.

### `POST /auth`

| Условие | Код | Тело |
|---|---|---|
| пара подошла | `200` | `Tokens` |
| пользователь не найден **или** пароль не тот | `404` | пусто |

`404` на неверный пароль — не описка: разные ответы говорили бы, какие имена заняты.

Правила регистрации на входе **не применяются**: у заведённых до их появления имена им не
подчиняются, и запрет выселил бы существующих пользователей.

### `POST /auth/refresh`

| Условие | Код | Тело |
|---|---|---|
| токен принят | `200` | новая пара `Tokens` |
| подпись не сошлась, вид не тот, токен просрочен, токена нет в базе, имя в claim не совпало с владельцем | `401` | пусто |

Подписи мало: предъявленный refresh-токен обязан ещё и **лежать в базе**, иначе однажды отозванный
токен работал бы до самого истечения. Удачное обновление старый токен сжигает —
`tokenRepository.removeToken(...)` перед выдачей новой пары.

### Общее для всех маршрутов

| Условие | Код | Тело |
|---|---|---|
| тело не разобралось, параметр пути не разобрался | `400` | `Malformed request` |
| необработанное исключение | `500` | пусто (строка уходит в stdout сервера) |

Ставится `StatusPages` в `server-common/.../ManiApp.kt:86`. Отмена запроса клиентом отказом не
считается и в 500 не превращается.

## Особенности

* **`GET /users/current` объявлен, но не реализован.** `UserResource.CurrentUserResource` есть в
  контракте (`shared/.../feature/user/UserResource.kt:9`), обработчика в `UserRouting.kt` нет, и
  никто его не зовёт. На нативной сборке путь попадёт в маршрут статики и получит `index.html`; на
  JVM-сборке — что отдаст `staticResources`. Проверено 08.09.2026.
* **Токен без claim `kind` принимается как refresh.** Окно совместимости с датой снятия — §1.3
  [research-architecture](../research/research-architecture.md).
* **`401` от Bearer-провайдера несёт текст** `Token is not valid or has expired` — тот же, что
  давал прежний `ktor-server-auth-jwt`.
