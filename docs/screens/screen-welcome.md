---
id: screen-welcome
title: Витрина (Welcome)
type: client_screen
platform: [android, ios, desktop, web]
status: active
entry:
  all: "маршрут ManiScreen.Welcome; точка входа графа, если refresh-токена нет"
parent_feature: feature-auth
calls_api:
  - endpoint-auth
source: composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/welcome/
---

# Экран: витрина

## 0a. Код

| Что | Файл |
|---|---|
| ViewModel и состояние | `composeApp/.../feature/welcome/WelcomeViewModel.kt` (`WelcomeUiState` там же) |
| Композиция | `composeApp/.../feature/welcome/WelcomeComponent.kt` |
| Вход в песочницу | `composeApp/.../feature/auth/domain/StartDemoUseCase.kt` |
| Строка о сборке | `composeApp/.../feature/health/domain/GetHealthUseCase.kt` |
| Голден | `composeApp/src/desktopTest/snapshots/screens_welcome_wide.png` |

## 0. Точка входа и видимость

* **Точка входа:** точка входа навигационного графа, когда в хранилище нет refresh-токена
  (`ManiAppNavHost.kt:52`). Сюда же уводит выход и истечение сессии.
* **Показывается:** только неавторизованному.

## 1. Состояния

Поля `WelcomeUiState`:

* **Обычное** — `loading = false`: заголовок, образец прогноза графиком, кнопка «Try the demo»,
  ссылки на вход и регистрацию.
* **`loading = true`** — песочница заводится: кнопка в состоянии загрузки.
* **`errorMessage != null`** — песочницу завести не удалось; текст берётся у сервера как есть
  (например `The demo is full right now, try again later`).
* **`server`** — строка вида `ktor · kotlin/native · 1.4.2`; пусто, пока `/health` не ответил.
* **`success = true`** — вход состоялся, экран уводит на главный.

## 2. Обращения к API

| Вызов | Контракт | Документ |
|---|---|---|
| `GET /health` | `HealthResource` | — (описан в [server-native](../services/server-native.md)) |
| `POST /demo` | `DemoResource` | [endpoint-auth](../api/endpoint-auth.md) |

## 3. Инициализация

Входных параметров нет.

| Вызов | Когда | Результат |
|---|---|---|
| `GET /health` | при создании ViewModel | заполняется `server` |

Отказ `/health` **молчаливый**: строка о сборке — украшение витрины, и её отсутствие не должно
мешать войти в демо.

## 4. Элементы

### 4.1. Образец прогноза (график)

Рисуется по демонстрационному набору `DemoSeed` — **до всякого входа**, симуляцией из
[shared](../services/shared.md). День отсчёта прибит, а не берётся из часов: иначе диапазон графика
уезжал бы каждые сутки вместе со снимком (`TransactionsOperations.kt:29`).

### 4.2. «Try the demo»

Главная дорожка витрины: посетителю нечего вводить.

| Случай | Обработка | Состояние |
|---|---|---|
| `201` | токены в хранилище | `success = true` → переход на главный |
| `503` / `500` / сеть | текст сервера в `errorMessage` | `loading = false` |

### 4.3. Строка о сборке

`ktor · <build> · <version>` — то же, что отдаёт `/health`, а не зашитая строка. Отсюда видно, что
запрос обслужил нативный бинарь.

### 4.4. Вход и регистрация

Текстовые ссылки на [screen-auth-form](screen-auth-form.md).

## 5. Навигация

* «Try the demo» успешно ──▶ `screen-main` (витрина уходит из стека, точка входа графа переезжает)
* «Sign in» ──▶ `screen-auth-form` (Login)
* «Sign up» ──▶ `screen-auth-form` (Signup)

Стрелки «назад» здесь нет: `Welcome` — корневой экран (`ManiScreen.kt:20`).
