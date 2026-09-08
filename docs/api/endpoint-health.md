---
id: endpoint-health
title: Живость и готовность
type: api_endpoints
status: active
services:
  - server-common
  - server
  - server-native
contract_source:
  - "mani-kotlin-fullstack::shared HealthResource, Health"
parent_feature: feature-health
---

# API: живость и готовность

> **Полный перечень маршрутов** ресурса `/health`. Сгенерированной схемы у продукта нет
> (см. [endpoint-auth](endpoint-auth.md)), этот документ и есть справочник.

## Маршруты — все

| Метод и путь | Ярус | Назначение |
|---|---|---|
| `GET /health` | открытый | жив ли процесс; заодно называет сборку и версию |
| `GET /health/ready` | открытый | отвечает ли хранилище |

Открыты намеренно: их зовут kubelet и витрина, а содержимое — не данные пользователя.

## Обработчики

| Маршрут | Обработчик |
|---|---|
| оба | `server-common/.../feature/health/HealthRouting.kt` |
| порт хранилища | `server-common/.../feature/health/StorageHealth.kt` |
| тип сборки | `server-common/src/jvmMain/.../HealthRouting.jvm.kt`, `server-common/src/linuxX64Main/.../HealthRouting.linuxX64.kt` |

## Тела ответов

| Что | Класс |
|---|---|
| ответ `GET /health` | `shared/.../feature/health/HealthResource.kt` (`Health`) |
| ответ `GET /health/ready` | обычный текст, не JSON |

## Ответы

### `GET /health`

| Условие | Код | Тело |
|---|---|---|
| процесс жив | `200` | `Health` — `build`, `version`, `uptimeSeconds` |

Зависимостей не трогает: другого кода у него нет. `build` — `jvm` либо `kotlin/native`; `version`
— `mani.version` из `gradle.properties`, то есть то же число, из которого собран тег образа.

### `GET /health/ready`

| Условие | Код | Тело |
|---|---|---|
| хранилище ответило | `200` | `ready` |
| хранилище не ответило либо драйвер отказал | `503` | `storage unreachable` |

Тело — обычный текст: пробе нужен код ответа, а не разбор причины. Отмена запроса в `503` не
превращается.

## Как их зовут в бою

Из `.k8s-templates/deployment.yaml`:

| Проба | Путь | Период | Порог | Таймаут |
|---|---|---|---|---|
| liveness | `/health` | 10 с | 3 | — |
| readiness | `/health/ready` | 5 с | 2 | 3 с |

Таймаут задаёт **kubelet**, а не сервер: своего у пробы нет намеренно — обёртка вокруг
блокирующего вызова его не даёт, а решать, сколько ждать, должен тот, кто ждёт.

## Особенности

* **Ни одна из двух проб не авторизуется**, поэтому `/health` — самый дешёвый способ снаружи
  узнать версию стенда и то, сколько он работает без перезапуска.
* **`uptimeSeconds` считается от инициализации файла маршрутов**, а не от `main()`; в тесте,
  поднимающем приложение несколько раз, счётчик общий на прогон.
* **`/health/ready` ходит в базу на каждый вызов** — каждые 5 секунд на реплику.
