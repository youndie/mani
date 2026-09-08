---
id: composeApp
title: ":composeApp — клиент под четыре платформы"
type: service
repo_url: https://github.com/youndie/mani-kotlin-fullstack
module: ":composeApp"
tech_stack: [Compose Multiplatform, Kotlin Multiplatform, Ktor client, Koin, androidx.navigation, multiplatform-settings]
owner: unassigned
depends_on:
  - shared
  - server-native
publishes:
  - APK, десктопный дистрибутив, wasm-бандл (едет внутрь образа сервера)
---

# :composeApp — клиент под четыре платформы

## 1. Ответственность

**Весь интерфейс продукта, одним кодом на Android, iOS, десктоп и браузер.** Экраны, состояния,
навигация, сетевой слой, хранение токенов, кэш последнего известного списка.

Платформенного кода здесь на удивление мало — четыре пары файлов:

| Что | Зачем платформенное |
|---|---|
| `TokenStorageImpl` | у каждой платформы своё хранилище секретов; в браузере — `localStorage` |
| `authModulePlatform` | подключает этот `TokenStorageImpl` в граф |
| `ServerConfigPlatform` | откуда берётся адрес сервера (см. §3) |
| точка входа (`main.kt`) | десктоп и wasm запускаются каждый по-своему |

`:androidApp` и `:iosApp` — **тонкие пусковые модули** без логики: они лишь поднимают
`App()`. `:baselineprofile` — генерация baseline-профиля для Android. Отдельных документов у них
нет намеренно: описывать в них нечего, а пустой документ создаёт вид покрытия.

## 2. Контракты

Клиент ходит на сервер **теми же `@Resource`-классами**, которыми сервер разбирает путь, — они
лежат в [shared](shared.md). Разбор по кодам ответов: [endpoint-auth](../api/endpoint-auth.md),
[endpoint-transactions](../api/endpoint-transactions.md).

## 2a. Код

| Файл | Что там |
|---|---|
| `composeApp/src/commonMain/kotlin/io/github/youndie/mani/App.kt` | корень приложения |
| `composeApp/src/commonMain/kotlin/io/github/youndie/mani/appModule.kt` | список Koin-модулей фич |
| `composeApp/src/commonMain/kotlin/io/github/youndie/mani/navigation/` | граф (`ManiAppNavHost.kt`), список экранов (`ManiScreen.kt`), правило стрелки «назад» |
| `composeApp/src/commonMain/kotlin/io/github/youndie/mani/data/Network.kt` | `HttpClient`: Resources, ContentNegotiation, Bearer-плагин |
| `composeApp/src/commonMain/kotlin/io/github/youndie/mani/data/RefreshSession.kt` | обмен refresh-токена на новую пару |
| `composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/<фича>/` | `data/` → `domain/` → `ui/`, DI в `module.kt` |
| `composeApp/src/commonMain/kotlin/io/github/youndie/mani/Constants.kt` | адрес сервера по умолчанию |
| `composeApp/src/commonMain/kotlin/io/github/youndie/mani/uiState/UiState.kt` | общие интерфейсы состояний: `LoadingState`, `ErrorState`, `DataState` |
| `composeApp/src/desktopTest/kotlin/io/github/youndie/mani/screenshots/Screens.kt` | набор скриншот-тестов |
| `composeApp/src/desktopTest/snapshots/` | голдены (записаны **на Linux**) |
| `composeApp/src/commonMain/kotlin/ir/ehsannarmani/compose_charts/` | вендоренный `compose-charts` с местными правками |

Раскладка фичи одинаковая: `data/` (репозиторий, источник данных, кэш) → `domain/` (use case'ы) →
`ui/` (ViewModel, состояние, компоненты), плюс `module.kt` с проводкой.

## 3. Как это устроено

**Адрес сервера ищется в три хода.** В браузере клиент говорит с тем origin, что отдал страницу, —
поэтому правок в исходниках для локального запуска не нужно. Десктоп берёт переопределение из
`MANI_SERVER`. Остальные падают на умолчание из `Constants.kt` (`mani.kotlin.website`).

**Обновление токена вынесено из настройки клиента отдельной функцией.** Тело `refreshTokens { }`
внутри `HttpClient` проверяется только живым сервером; `refreshSession()` проверяется подставным
движком, как и всё остальное в слое (`RefreshSessionTest`). Пометка `markAsRefreshTokenRequest()`
приходит параметром, потому что этот метод существует только внутри области `refreshTokens { }`, а
без неё плагин пытался бы обновить токен для самого запроса обновления — то есть закольцевался бы.

**Отказ обновления разобран по видам, и это не педантизм:**

| Ответ на `/auth/refresh` | Что делает клиент |
|---|---|
| `401` | сессия кончилась: токены сбрасываются, поднимается событие `expired` |
| прочий не-2xx | сессию **не** трогает: 500 у сервера её не отменяет |
| `2xx` | новая пара ложится в хранилище |

Раньше на `401` токены сбрасывались, а тело всё равно читалось — которого у 401 нет. Разбор падал,
и наружу выходило не «сессия истекла», а отказ сети: экран «сервер недоступен» с обратным отсчётом,
из которого нет пути на витрину.

**Точка входа навигации читается один раз и на токен не подписана.** Подписка пересобирает граф, а
новый граф сбрасывает навигацию на свою точку входа — то есть каждый приход токена молча
перекидывал экран. Истечение сессии приходит **событием** (`TokenRepository.expired`, `replay = 0`),
переходы «вошёл»/«вышел» делаются явно. Подробности — в §1.9
[research-architecture](../research/research-architecture.md).

**Без сети показывается последний известный список** с отметкой времени его снятия. Правила — не
лента событий: вчерашний список верен и сегодня. Состояние экрана различает «показываю кэш»
(`showingCacheFrom`) и «показать нечего» (`unreachable`), и это разные экраны, а не разные значения
одного поля.

## 4. Зависимости

| Вид | Что | Зачем |
|---|---|---|
| Модуль | [shared](shared.md) | ресурсы, модель, симуляция баланса |
| Сервис | [server-native](server-native.md) | API стенда |
| Библиотека | Ktor client (`resources`, `auth`, `content-negotiation`, `logging`) | HTTP |
| Библиотека | Koin (`koin-compose`) | DI, модули подключаются экраном через `rememberKoinModules` |
| Библиотека | `androidx.navigation` (Compose) | граф экранов |
| Библиотека | `multiplatform-settings` | токены и кэш списка |
| Библиотека | `kotlinx.collections.immutable` | состояния экранов стабильны для Compose |
| Библиотека | [viddik](https://github.com/youndie/viddik) | скриншот-тесты |
| Вендоренный код | `compose-charts` | график; правка внутри канваса — маркер дня обнуления |

## 5. Инфраструктура и выкат

Отдельного стенда нет. Wasm-бандл собирается в выкате сервера
(`:composeApp:wasmJsBrowserDistribution`), сжимается на сборке образа и едет внутрь образа
[server-native](server-native.md). Android и десктоп собираются отдельными workflow
(`.github/workflows/build_android.yml`, `build_desktop.yml`).

## 6. Локальный запуск

```bash
./gradlew :composeApp:run
```

```bash
MANI_SERVER=http://localhost:8080 ./gradlew :composeApp:run
```

```bash
./gradlew installDebug
```

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

iOS — открыть `iosApp/iosApp.xcodeproj` в Xcode.

Тесты:

```bash
./gradlew :composeApp:desktopTest
```

```bash
./gradlew :composeApp:viddikVerify
```

```bash
./gradlew :composeApp:viddikRecord
```

## 7. Конфигурация

| Ключ | Где действует | Описание |
|---|---|---|
| `MANI_SERVER` | десктоп | переопределяет адрес сервера |
| — | браузер | адрес берётся из origin страницы, настраивать нечего |
| — | Android, iOS | умолчание из `Constants.kt` |

## 8. Особенности

* **Скриншот-тесты не гоняются в PR, и это решение.** Голдены записаны на Linux; тот же код на
  macOS рисует текст иначе — разница 1–4 % пикселей, далеко за любым разумным допуском. Голден,
  воспроизводимый на одной ОС, не годится в проверку, гейтящую слияния.
* **Скриншот из `README.md` — это сам голден**, а не его копия. Перезапись голденов перерисовывает
  и картинку в README, так что она не может тихо разойтись с интерфейсом.
* **`:composeApp:wasmJsTest` требует ChromeHeadless.** В CI браузер есть; на машине без него задача
  падает на запуске, хотя компиляция wasm проходит целиком.
* **Свой `.editorconfig`, а не общий.** Отключено правило имени файла (файлы собраны по смыслу:
  `module.kt` — DI одной фичи) и разрешены унаследованные звёздочные импорты — 80 штук в 35 файлах.
  Причина записана в `gradle.properties`, у ключа `sborka.editorconfig`.
* **`TransactionUiState.categoriesExpanded` возвращает константу `true`.** Свойство есть, решения
  за ним нет — в отличие от соседнего `periodsExpanded`, которое сравнивает список с умолчанием.
