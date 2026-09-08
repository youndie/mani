---
id: screen-auth-form
title: Форма учётных данных (Login и Signup)
type: client_screen
platform: [android, ios, desktop, web]
status: active
entry:
  all: "маршруты ManiScreen.Login и ManiScreen.Signup"
parent_feature: feature-auth
calls_api:
  - endpoint-auth
source: composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/auth/ui/
---

# Экран: форма учётных данных

Один документ на два маршрута. `Login` и `Signup` — **одна композиция и одна ViewModel**:
различаются подставленным в граф Koin use case'ом, заголовком и надписью на кнопке. Разводить их
на два документа значило бы описать один и тот же файл дважды.

| Маршрут | Use case в графе | Заголовок | Кнопка |
|---|---|---|---|
| `Login` | `LoginUseCase` | `Mani` | `Login` |
| `Signup` | `SignupUseCase` | `Sign up` | `Create` |

## 0a. Код

| Что | Файл |
|---|---|
| ViewModel | `composeApp/.../feature/auth/ui/AuthViewModel.kt` |
| Состояние ViewModel | `composeApp/.../feature/auth/ui/model/AuthUiState.kt` |
| Состояние формы | `composeApp/.../feature/auth/ui/model/AuthComponentUiState.kt` |
| Общая композиция | `composeApp/.../feature/auth/ui/component/AuthComponentImpl.kt` |
| Обёртки маршрутов | `.../component/LoginComponent.kt`, `.../component/SignupComponent.kt` |
| Use case'ы | `composeApp/.../feature/auth/domain/LoginUseCase.kt`, `SignupUseCase.kt` |

## 0. Точка входа и видимость

* **Точка входа:** ссылки «Sign in» и «Sign up» с [витрины](screen-welcome.md); со Signup есть
  ссылка на Login.
* **Показывается:** неавторизованному.
* Верхняя панель на Login **выключается** (`appBarState.disable()`), Signup въезжает снизу
  анимацией.

## 1. Состояния

Поля `AuthUiState`:

* **Пустая форма** — два поля, кнопка активна.
* **`loading = true`** — запрос ушёл: кнопка в состоянии загрузки.
* **`demoLoading = true`** — нажата «Try the demo» (есть и здесь, на Login).
* **`errorMessage != null`** — текст под полями, под тестовым тегом `errorMessage`.
* **`success = true`** — переход дальше.

Локальной проверки ввода на форме **нет**: она вся на сервере, и человек видит его текст.

## 2. Обращения к API

| Вызов | Контракт | Документ |
|---|---|---|
| `POST /auth` (Login) | `AuthResource` | [endpoint-auth](../api/endpoint-auth.md) |
| `POST /users` (Signup) | `UserResource` | [endpoint-auth](../api/endpoint-auth.md) |
| `POST /demo` (кнопка демо на Login) | `DemoResource` | [endpoint-auth](../api/endpoint-auth.md) |

## 3. Инициализация

Входных параметров нет, запросов на открытии нет.

## 4. Элементы

### 4.1. Поля `username` и `password`

Ввод очищает `errorMessage`: старый отказ не должен висеть над уже исправленным вводом.

### 4.2. Кнопка действия

**Login:**

| Случай | Обработка | Состояние |
|---|---|---|
| `200` | токены в хранилище | `success = true` |
| `404` | `User not found or invalid password` в `errorMessage` (`UserNotFoundException`) | `loading = false` |
| прочее, включая отказ сети | `Network Error` — один текст на все остальные случаи | `loading = false` |

**Signup:**

| Случай | Обработка | Состояние |
|---|---|---|
| `201` | — | `success = true` → переход на Login |
| `400` | **текст сервера** в `errorMessage`, пустой — `Sign up refused` | `loading = false` |
| `500` | `Server error` | `loading = false` |

Текст `400` берётся у сервера, а не подставляется на клиенте: раньше на месте любого `400` стояло
«User already exist», и приславший короткий пароль читал, что имя занято.

### 4.3. «Try the demo» (только Login)

Та же дорожка, что на витрине, с подписью «your own sandbox — no account, no password».

## 5. Навигация

* Login успешно ──▶ `screen-main` (стек чистится)
* Signup успешно ──▶ Login **той же формы** — автоматического входа нет, данные вводятся заново
* «Sign up» с Login ──▶ Signup
* «Sign in» со Signup ──▶ Login
* «Try the demo» ──▶ `screen-main`
