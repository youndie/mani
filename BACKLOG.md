# Бэклог: mani как эталон KMP-проекта

> Роль документа: что в репозитории расходится с ролью «эталон архитектуры, подходов и библиотек»
> и в каком порядке это закрывать. Снято с кода 2026-09-08, ветка `refactor/kotlin-result`.
>
> Один пункт — один `M<веха>-<NN>`. Веха закрывается целиком и получает строку итога: что вышло
> сверх плана и какая гипотеза не подтвердилась. Приоритет и размер — поля пункта, не разделы:
> перестановка приоритета не должна ломать ссылку на номер.

## Цель

Проект уже делает то, ради чего заведён: один контракт на клиента и две сборки сервера, общий
`TokenService`, тесты по сырому документу, релизный прогон native. Бэклог убирает то, что мешает
показывать его как образец: две дыры в авторизации, токен, живущий месяц вместо часа, краш
клиента без сети и модуль `:shared`, в котором контракт перемешан с клиентской базой.

Порядок вех — по цене ошибки: сначала то, что открыто на `mani.kotlin.website`, потом то, что
роняет клиент, потом то, что путает читателя.

## Метки

`[ ]` открыт · `[~]` в работе · `[x]` сделан · `[?]` открытый вопрос · `[-]` отклонён

Приоритет: `P0` дыра на публичном стенде · `P1` ошибка, которую видит пользователь · `P2` расхождение
с эталоном · `P3` гигиена. Размер: `XS` строки · `S` файл · `M` несколько файлов · `L` модуль.

---

## M0 — Авторизация не течёт

Всё, что позволяет одному пользователю стенда трогать данные другого или обходить срок токена.
Одним PR: правки маленькие, а тест на чужие данные проверяет их вместе.

- [x] **M0-01 · P0 · S** — Чужую транзакцию можно перезаписать и присвоить через `PATCH`
  - Сейчас: маршрут проверяет владельца по `path.id`, а обновляет по `id` из тела. Обе реализации
    делают `replaceOne` по `_id` из тела и пишут `userId` вызывающего: запрос
    `PATCH /transactions/<своя>` с телом `{id: <чужая>}` переписывает чужую запись и делает её своей.
  - Решение: `id` из пути — единственный источник; тело копируется с `id = path.id`, а
    репозиторий фильтрует по `_id` **и** `userId`, чтобы дыра не вернулась с новым маршрутом.
    Не делать: менять контракт `Transaction` (клиент шлёт тот же объект).
  - AC: `ManiApiTest` — второй пользователь получает 403 на `PATCH` чужой записи, и запись не
    изменилась; свой `PATCH` с чужим `id` в теле не трогает чужую запись.
  - Якоря: `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/transaction/TransactionRouter.kt`,
    `server/src/main/kotlin/io/github/youndie/mani/feature/transaction/data/MongoTransactionRepository.kt`,
    `server-native/src/linuxX64Main/kotlin/io/github/youndie/mani/feature/transaction/data/MongknTransactionRepository.kt`

- [x] **M0-02 · P0 · S** — Чужую категорию можно переименовать через `PATCH`
  - Сейчас: проверка принадлежности по `path.id`, обновление по `categories._id` из тела без
    фильтра по пользователю — в обеих реализациях.
  - Решение: как в M0-01 — `id` из пути, фильтр по `_id` пользователя и `categories._id` в
    репозитории. Сигнатура `CategoryRepository.update(category, userId)`.
  - AC: `ManiApiTest` — переименование чужой категории даёт 403 и не меняет её имя.
  - Якоря: `server-common/.../feature/category/CategoryRouting.kt`,
    `server-common/.../feature/category/CategoryRepository.kt`,
    `server/.../feature/category/data/MongoCategoryRepository.kt`,
    `server-native/.../feature/category/data/MongknCategoryRepository.kt`

- [x] **M0-03 · P0 · M** — Refresh-токен принимается как access
  - Сейчас: `TokenService.verify` не различает вид токена, `ManiJwtProvider` пускает любой
    подписанный. Refresh живёт месяц, и часовой срок access-токена ничего не защищает.
  - Решение: claim `kind` (`access` / `refresh`). Провайдер пускает только `access`; `/auth/refresh`
    принимает `refresh` **и** токены без claim — старые refresh-токены из базы выданы без него и
    истекают сами в течение месяца. После этого лазейку для «без claim» убрать отдельным пунктом.
    Отвергнуто: разные секреты для двух видов — два места с секретом ради того, что делает одно поле.
  - AC: refresh-токен в `Authorization: Bearer` даёт 401; access-токен в `/auth/refresh` даёт 401;
    `LegacyTokenCompatibilityTest` по-прежнему зелёный на refresh-пути.
  - Якоря: `server-common/.../security/TokenService.kt`, `server-common/.../security/ManiAuth.kt`,
    `server-common/.../feature/auth/data/AuthService.kt`,
    `server-common/src/commonTest/.../security/TokenServiceTest.kt`

- [x] **M0-04 · P1 · XS** — `currentUserId()` после 401 возвращает пустую строку и код идёт дальше
  - Сейчас: внутри `authenticate` ветка мёртвая, но следующий маршрут вне блока получит `""` как
    владельца и второй `respond` поверх первого.
  - Решение: `call.principalOrThrow()`, возвращающий `ManiPrincipal`; отсутствие principal внутри
    `authenticate` — ошибка проводки, а не состояние запроса.
  - AC: в `server-common` нет вызовов, продолжающих работу после `respond(Unauthorized)`.
  - Якоря: `server-common/.../feature/user/User.kt` и все `currentUserId()` в маршрутах.

- [x] **M0-05 · P1 · S** — Нет `StatusPages`: мусорный `ObjectId` даёт 500, а на native исключение не логируется
  - Сейчас: `DELETE /transactions/not-an-id` — `IllegalArgumentException` на JVM и
    `BsonObjectId.parse` на native, оба в 500 без записи в лог. У нативной сборки логгера нет вовсе,
    и причина 500 на стенде невидима.
  - Решение: `ktor-server-status-pages` (мультиплатформенный) в `configureManiPlugins`:
    `IllegalArgumentException`, `SerializationException`, `BadRequestException` → 400;
    остальное → 500 с записью в stdout (на native это и есть лог k8s). Не делать: `CallLogging`,
    он JVM-only.
  - AC: `ManiApiTest` — невалидный id в пути даёт 400; в тесте сборки виден текст исключения.
  - Якоря: `server-common/.../ManiApp.kt`, `server-common/build.gradle.kts`,
    `gradle/libs.versions.toml`

**Итог вехи.** Пять пунктов закрыты, обе дыры в авторизации — тоже. Сверх плана вышло три вещи.

Тест из M0-04 переехал сюда, в M0-05: что незащищённый маршрут отдаёт наружу, до `StatusPages`
решал тест-хост, а не мы, и пинить стоило только детерминированный ответ. Случай «тело не
разбирается» оказался закрыт самим Ktor — мутация показала, что без `StatusPages` он проходит; он
оставлен сторожем не поведения Ktor, а того, что общий `exception<Throwable>` его не испортил: без
ветки `BadRequestException` ответ становится 500. Третья копия обвязки JVM-тестов завелась здесь же
и выписана отдельным пунктом — M3-06.

Посылка вехи подтвердилась дословно. Обе дыры жили на одном стыке: принадлежность проверялась по
пути, запись выбиралась телом. И каждая потребовала теста в КАЖДОЙ сборке — фильтр записи свой
у двух реализаций хранилища, так что починка одной оставляет вторую открытой.

---

## M1 — Сервер не доверяет входу

- [x] **M1-01 · P0 · S** — Секрет подписи по умолчанию — строка `secret`
  - Сейчас: `ManiConfig.fromEnv` подставляет её, когда `JWT_SECRET` не задан; `docker-compose.yaml`
    его не задаёт, k8s задаёт из Secret.
  - Решение: переменной нет — сгенерировать случайный секрет на процесс и написать об этом в лог:
    локальный стенд работает, сессии не переживают перезапуск, «secret» никогда не уезжает.
    Отвергнуто: падать без переменной — ломает `docker compose up` из README без выгоды.
  - AC: старт без `JWT_SECRET` печатает предупреждение; два процесса без переменной не принимают
    токены друг друга; `JWTConfig()` по умолчанию в тестах не меняется.
  - Якоря: `server-common/.../config/ManiConfig.kt`, `README.md` (таблица переменных)

- [x] **M1-02 · P1 · S** — Регистрация принимает пустое имя и пустой пароль
  - Сейчас: `POST /users` без единой проверки; `demo-` префикс тоже можно занять руками, и уборка
    песочниц унесёт такого пользователя через сутки.
  - Решение: имя 3–32 символа из `[a-z0-9_-]`, не начинается с `demo-`; пароль от 8 символов.
    Ответ 400 с текстом, который клиент показывает как есть.
  - AC: `ManiApiTest` на каждую границу; `AuthComponentTest` показывает текст ошибки.
  - Якоря: `server-common/.../feature/user/UserRouting.kt`,
    `composeApp/.../feature/auth/domain/SignupUseCase.kt`

- [x] **M1-03 · P1 · S** — Транзакция и категория принимаются без валидации
  - Сейчас: `amount` любого знака, `until < date`, пустое имя категории, комментарий без предела.
  - Решение: проверка в маршруте перед репозиторием, 400 с причиной. `amount > 0`
    (знак задаёт `income`), `until >= date`, имя категории непустое, комментарий до 200 символов.
    `isLenient = true` у серверного `Json` убрать: сервер не должен принимать JSON без кавычек.
  - AC: `ManiApiTest` на каждую границу; клиентская форма уже не даёт ввести такое, но сервер
    отвечает 400, а не 201.
  - Якоря: `server-common/.../feature/transaction/TransactionRouter.kt`,
    `server-common/.../feature/category/CategoryRouting.kt`, `server-common/.../ManiApp.kt`

- [x] **M1-04 · P1 · S** — `POST /demo` без предела при базе на 256 MiB
  - Сейчас: каждый вызов заводит пользователя с семью транзакциями; уборка запускается только
    при следующем вызове и уносит лишь старше суток. Цикл запросов заполняет диск стенда.
  - Решение: потолок живых песочниц (например, 500): при превышении сначала уборка, затем 503.
    Считается одним запросом по префиксу `demo-`. Отвергнуто: лимит по IP — за ingress один адрес.
  - AC: `DemoRoutingTest` — при достигнутом потолке ответ 503 и пользователь не создан.
  - Якоря: `server-common/.../feature/demo/data/DemoService.kt`,
    `server-common/.../feature/demo/data/DemoSandboxCleaner.kt`,
    `server-common/.../feature/user/data/UserRepository.kt`

- [x] **M1-05 · P3 · XS** — Пароль песочницы из `kotlin.random.Random`
  - Решение: `CryptographyRandom.nextBytes(...).toHex()` — он уже в графе и уже используется для
    соли и `jti`.
  - Якоря: `server-common/.../feature/demo/data/DemoService.kt`

**Итог вехи.** Пять пунктов закрыты. Сервер перестал доверять и входу, и себе: секрет больше не
лежит в исходниках, регистрация и правила проверяются, у витрины есть потолок, а случайность берётся
из одного источника.

Главное вышло за рамки плана. Пункт M1-02 был написан как серверный, а настоящая дыра оказалась на
клиенте: `SignupUseCase` подменял любой отказ строкой «User already exist», и человек, приславший
короткий пароль, читал про занятое имя. Проверка на сервере без этого была бы работой вхолостую —
текст всё равно не доезжал бы до того, кто его должен прочесть.

Посылка «валидация — серверная работа» подтвердилась только наполовину. Вторая половина ценности не
в том, чтобы отказать, а в том, чтобы отказ дошёл нетронутым.

Инструментальная находка вехи записана ниже, в открытых вопросах: нативные тестовые задачи умеют
оставаться `UP-TO-DATE` после перелинковки, а `--rerun` действует только на одну задачу за вызов.

---

## M2 — Клиент переживает отказ

- [ ] **M2-01 · P1 · XS** — Удаление категории без сети роняет приложение
  - Сейчас: в `DeleteCategoryUseCase` перезагрузка списка правил стоит в `also` снаружи
    `suspendRunCatching`; исключение уходит мимо `Result` в `viewModelScope.launch` без
    обработчика. К тому же перезагрузка идёт и после неудачного удаления.
  - Решение: перезагрузка внутри `suspendRunCatching` и только на успех.
  - AC: тест use case — отказ сети на перезагрузке даёт `Result.failure`, а не исключение.
  - Якоря: `composeApp/.../feature/categories/domain/DeleteCategoryUseCase.kt`

- [ ] **M2-02 · P1 · M** — Истёкшая сессия не ведёт на витрину
  - Сейчас: `refreshTokens` при 401 стирает токены и затем `body<Tokens>()` бросает на пустом теле.
    Экран остаётся на главной с «сервер недоступен» и тремя ретраями; события «сессия истекла» нет.
  - Решение: при 401 на refresh вернуть `null` без разбора тела и поднять событие в
    `TokenRepository` (поток «вышли»), на которое `App` реагирует переходом на `Welcome` тем же
    путём, что и `onLoggedOut`. Отвергнуто: подписка навигации на токен — уже ломала переходы,
    см. комментарий в `ManiAppNavHost`.
  - AC: `MainViewModelTest` — 401 на refresh даёт событие выхода, а не экран недоступности;
    в браузере просроченный refresh-токен в `localStorage` открывает витрину.
  - Якоря: `composeApp/.../data/Network.kt`, `composeApp/.../feature/auth/data/TokenRepository.kt`,
    `composeApp/.../App.kt`, `composeApp/.../navigation/ManiAppNavHost.kt`

- [ ] **M2-03 · P3 · XS** — `withContext(Dispatchers.Default)` вокруг сетевых вызовов
  - Сейчас: в `LoginUseCase`, `GetHealthUseCase`, `StartDemoUseCase`, `SeedDemoDataUseCase`;
    Ktor и так не блокирует вызывающий поток, в одном месте это уже убрано как no-op (`7ce11cf`).
  - Решение: убрать во всех четырёх; диспетчер остаётся там, где есть счёт — симуляция в VM.
  - Якоря: `composeApp/.../feature/auth/domain/`, `composeApp/.../feature/health/domain/`,
    `composeApp/.../feature/demo/domain/`

- [ ] **M2-04 · P2 · M** — История и главная по-разному переживают отказ сети
  - Сейчас: `MainViewModel` умеет кэш, ретраи с отсчётом и экран недоступности; `TransactionsViewModel`
    при том же отказе показывает строку ошибки без повтора.
  - Решение: вынести загрузку с ретраями в общий класс и дать истории то же поведение.
  - AC: `TransactionsViewModelTest` — отказ сети показывает кэш с отметкой времени, как на главной.
  - Якоря: `composeApp/.../feature/main/MainViewModel.kt`,
    `composeApp/.../feature/transaction/ui/TransactionsViewModel.kt`

- [ ] **M2-05 · P2 · M** — Токены лежат в открытом виде
  - Сейчас: `session.txt` в текущем каталоге на десктопе, `NSUserDefaults` на iOS, обычные
    `SharedPreferences` на Android, `localStorage` в браузере.
  - Решение: iOS — Keychain, Android — `EncryptedSharedPreferences`, десктоп — файл в каталоге
    настроек пользователя с правами 600. Браузер оставить: у SPA без cookie-сессии другого места нет,
    и это записать в README рядом с оговоркой про хеш паролей.
  - AC: файл сессии не появляется в CWD; на устройстве токен не читается `adb shell` без root.
  - Якоря: `composeApp/src/{androidMain,iosMain,desktopMain}/.../TokenStorageImpl.kt`, `README.md`

**Итог вехи:** _пусто, пока веха открыта_

---

## M3 — Границы модулей и одна версия

- [ ] **M3-01 · P2 · L** — `:shared` — не только контракт
  - Сейчас: рядом с `@Resource` и моделью лежат `UseCase`, `BaseFlowRepository` с зашитым
    `Dispatchers.Default`, никем не используемые `DataState.toDataState` и `UiState.kt`, и
    `Constants.kt` с адресом `192.168.1.230`. Сервер из-за этого тянет клиентские зависимости, а
    README называет модуль «API contract».
  - Решение: контракт остаётся (`@Resource`, модель, сериализаторы, `DemoSeed`,
    `TransactionsOperations` — он нужен обеим сторонам); `UseCase`, `BaseFlowRepository`,
    `suspendRunCatching`, `today` уезжают в `:composeApp` (или новый `:client-core`, если
    появится второй клиент); `DataState`, `UiState`, `local` удалить. `currentServerConfig` — в
    `composeApp`, с адресом стенда, а не домашней сети.
    Отвергнуто: отдельный `:shared-client` сейчас — второй клиент не планируется.
  - AC: в `:shared` нет `Dispatchers`, `MutableStateFlow` и адресов; `:server-common` не зависит
    от `kotlinx-collections-immutable`; таблица «What is where» в README верна.
  - Якоря: `shared/src/commonMain/kotlin/io/github/youndie/mani/`, `shared/build.gradle.kts`,
    `composeApp/build.gradle.kts`, `README.md`

- [ ] **M3-02 · P2 · S** — Четыре несвязанные версии
  - Сейчас: `MANI_VERSION = "1.4.2"` в `/health`, сервер `0.2.${BUILD_NUMBER}`, десктоп
    `packageVersion = "1.0.0"`, Android `versionName = "1.0"`, тег образа `0.2.<run_number>`.
  - Решение: одно число в `gradle.properties` (`mani.version`), из него `MANI_VERSION` через
    сгенерированный источник или `BuildConfig`, и оно же в десктоп, Android и тег образа. Номер
    сборки — суффикс, а не отдельная линия.
  - AC: `/health` и тег образа называют одну версию; `grep` по репозиторию находит её в одном месте.
  - Якоря: `server-common/.../feature/health/HealthRouting.kt`, `server/build.gradle.kts`,
    `composeApp/build.gradle.kts`, `androidApp/build.gradle.kts`, `.github/workflows/deploy.yml`,
    `.k8s-templates/deployment.yaml`

- [ ] **M3-03 · P3 · XS** — Мёртвый код и мёртвая конфигурация
  - `UserService` в `SignupUseCase.kt`; `MongoConfig.userName` и `password`, которые в строку
    подключения не попадают (либо подставлять, либо убрать вместе с `MONGO_USERNAME`/`MONGO_PASSWORD`);
    копия devServer-конфига wasmJs в `shared/build.gradle.kts` — у библиотеки нет dev-сервера;
    `allowHeader(AccessControlAllowOrigin)` в CORS — это заголовок ответа.
  - Якоря: `composeApp/.../feature/auth/domain/SignupUseCase.kt`,
    `server-common/.../config/ManiConfig.kt`, `shared/build.gradle.kts`, `server-common/.../ManiApp.kt`

- [ ] **M3-04 · P3 · XS** — `ChartViewModel` берёт репозиторий валюты напрямую
  - Сейчас: единственная VM, минующая `GetCurrentCurrencyUseCase`; при этом `GetChartUseCase`
    существует и зарегистрирован, но VM его не использует.
  - Решение: VM через оба use case, как остальные; либо признать, что use case-слой для чтения
    настройки лишний, и убрать `GetCurrentCurrencyUseCase` везде — но одно из двух.
  - Якоря: `composeApp/.../feature/chart/ChartViewModel.kt`, `composeApp/.../feature/chart/GetChartUseCase.kt`

- [ ] **M3-06 · P3 · S** — Три копии обвязки JVM-тестов сервера
  - Сейчас: `DemoRoutingTest`, `OwnershipTest` и `MalformedRequestTest` поднимают flapdoodle и
    собирают приложение каждый своим одинаковым блоком строк на тридцать. Третья копия завелась
    в M0-05, и это уже не совпадение, а образец, который репозиторий показывает читателю.
  - Решение: общая `maniTest { }` в `server/src/test/kotlin` — mongod и приложение той же
    проводкой, что в бою, — плюс общие клиентские помощники (`signIn`, `createTransaction`,
    `categories`). Три класса переходят на неё. Отвергнуто: оставить как есть, потому что каждый
    класс «самодостаточен», — самодостаточность здесь означает три места для одной правки.
  - AC: `Mongod.instance().start` встречается в `:server` ровно один раз.
  - Якоря: `server/src/test/kotlin/`

- [ ] **M3-05 · P3 · XS** — JVM-jar стартует в dev-режиме Ktor
  - Сейчас: `io.ktor.development=true` в `gradle.properties` уходит в `applicationDefaultJvmArgs`.
  - Решение: убрать из `gradle.properties`, оставить флаг для `:server:run` через `MANI_DEVELOPMENT`,
    которым уже управляется CORS.
  - Якоря: `gradle.properties`, `server/build.gradle.kts`

**Итог вехи:** _пусто, пока веха открыта_

---

## M4 — Стенд и документация

- [ ] **M4-01 · P2 · XS** — В деплойменте нет readiness и liveness при готовом `/health`
  - Решение: `readinessProbe` и `livenessProbe` на `GET /health`; `/health` при этом должен
    трогать Mongo одним `ping`, иначе проба зелёная при мёртвой базе.
  - AC: под с недоступной базой не получает трафик; `kubectl rollout status` ждёт пробу.
  - Якоря: `.k8s-templates/deployment.yaml`, `server-common/.../feature/health/HealthRouting.kt`

- [ ] **M4-02 · P2 · XS** — Push в `main` деплоится без тестов
  - Сейчас: `main.yml` бежит только на `pull_request`, `deploy.yml` — на `push` в `main`.
    Прямой push уезжает на стенд непроверенным.
  - Решение: branch protection на `main` с обязательными `ktlint`, `test-common`, `test-native`;
    либо `deploy.yml` через `workflow_run` после `Test`. Первое проще и не дублирует прогон.
  - AC: прямой push в `main` отклоняется; PR без зелёных проверок не вливается.
  - Якоря: настройки репозитория, `.github/workflows/deploy.yml`

- [ ] **M4-03 · P2 · S** — Нет `CLAUDE.md`
  - Сейчас: `.claude/` пуст; всё, что агент должен знать (где гонять native, что `:shared` —
    контракт, что goldens пишутся на Linux, что версия одна), живёт в README и комментариях.
  - Решение: короткий `CLAUDE.md` с правилами, которых нет в коде: сборка на WSL, native только
    Linux, стиль коммитов, язык (код по-английски, документация по-русски), ссылка на этот бэклог.
  - Якоря: `CLAUDE.md`

- [ ] **M4-04 · P2 · L** — Слой документации под скиллы
  - Сейчас: README хороший, но это витрина; описания экранов, эндпоинтов и фич в форме, из
    которой можно собирать скиллы, нет.
  - Решение: `docs/` по `docs-bootstrap`: `features/` с BDD-сценариями, `screens/`, `api/`,
    `services/` с якорями в код и проверками в CI. Это основа для скиллов «клиентская фича на
    mani», «серверная фича на mani», «тесты на mani».
  - Заблокировано: M3-01 — описывать границы модулей до того, как они выровнены, значит
    переписывать документы через неделю.
  - Якоря: `docs/`, `README.md`, `.github/workflows/`

**Итог вехи:** _пусто, пока веха открыта_

---

## Решения, которые не стоит пересматривать

**SHA-256 с солью остаётся до отдельной миграции.** Формат воспроизведён байт в байт за прежней
реализацией, и в базе стенда лежат такие записи. Переход на PBKDF2/Argon2 — это самоописывающий
формат плюс перехеширование при входе, отдельная задача, а не пункт M1.

**`TokenService` и хеш — один код, а не expect/actual.** Две реализации разъехались бы молча:
токен одной сборки не приняла бы другая. Любое предложение «сделать нативную версию быстрее»
проверяется тестом совместимости, а не заменой класса.

**Native — только `linuxX64`.** mongkn публикуется под один таргет; macOS-разработчик собирает
JVM-сборку, и это не долг, а граница библиотеки.

**Свой `.editorconfig`, а не общий портфельный.** Перевод на `ktlint_official` переписал бы
форматирование всего репозитория; это правка кода, и она ждёт своего PR —
[youndie/mani#142](https://github.com/youndie/mani-kotlin-fullstack/issues/142).

**Голдены скриншотов не в PR-проверке.** Записаны на Linux, на macOS расходятся на 1–4 %
пикселей; проверка, воспроизводимая на одной ОС, не гейт для слияния.

## Открытые вопросы вне бэклога

- **Нативная тестовая задача умеет молча не запускаться.** При инкрементальном локальном
  прогоне Gradle отмечает `linuxX64Test`/`linuxX64ReleaseTest` как `UP-TO-DATE` даже после того,
  как задача линковки отработала: замечено 08.09.2026 дважды — на M1-01 (релизный набор) и на
  M1-05 (набор `:server-common`). Сборка зелёная, а в `build/test-results` лежат результаты
  прошлого прогона, и зачесть их за проверку очень легко.

  Второе, важное для команды: **`--rerun` действует только на ту задачу, за которой стоит.**
  `./gradlew a b c --rerun` перезапустит `c` и оставит `a` и `b` как есть — проверено на этих же
  трёх наборах. Значит, гнать принудительно надо по одной задаче за вызов.

  В CI не проявляется: там каждый раз чистая копия. Пунктом не заводится — причина в поведении
  Gradle-плагина, и объём работы отсюда не виден. Практика: смотреть на время файлов
  результатов, а не на `BUILD SUCCESSFUL`.
- **Поблажка для токенов без `kind` (см. M0-03).** `TokenService.verify` принимает как refresh
  токен без claim `kind` — иначе выкат разлогинил бы всех, чьи refresh-токены лежат в базе. Все
  они выданы со сроком в месяц, поэтому через месяц после выката M0-03 условие
  `kind == null && expect == Refresh` можно снять вместе с тестом `acceptsTokenIssuedByJavaJwt`.
  Отдельным пунктом бэклога не заводится: его нельзя закрыть работой, только календарём.
- [youndie/mani#146](https://github.com/youndie/mani-kotlin-fullstack/issues/146) —
  `NavGraphStabilityTest.graphIsBuiltOnce` нестабилен на wasmJs.
- Пять открытых PR renovate (#145, #155, #156, #158, #159) — бампы, не пункты бэклога, но M3-02
  и M4-02 упростят их приёмку.
