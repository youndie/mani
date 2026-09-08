---
id: feature-health
title: Живость, готовность и «какая сборка ответила»
type: feature
status: active
owner: unassigned
involved_services:
  - shared
  - server-common
  - server
  - server-native
client_entries:
  - screen-welcome
api:
  - endpoint-health
tags: [ops]
---

# Живость, готовность и «какая сборка ответила»

## 1. Обзор

Две пробы и одна витринная строка — три вещи, которые легко перепутать, поэтому они разведены
намеренно.

**`GET /health`** отвечает, жив ли процесс, и **базу не трогает**. **`GET /health/ready`**
отвечает, есть ли ему с чем работать, и базу спрашивает по-настоящему. Разница не формальная: под
без базы должен перестать получать трафик, а не уйти в перезапуск.

Третье — то, ради чего проект отчасти и существует. `/health` называет **сборку**: `jvm` или
`kotlin/native`. Витрина печатает эту строку вместо зашитой, так что посетитель видит, что его
запрос обслужил нативный бинарь, а не текст в README.

## 2. Правила

* `/health` не зависит ни от одной зависимости. Проба живости, зависящая от базы, превращает её
  падение в перезапуск всех подов — и лечит этим ровно ничего, только добавляет холодных стартов
  к уже случившейся беде.
* `/health/ready` **обязан сходить в базу**, а не вернуть закэшированное состояние клиента: драйвер
  считает соединение живым до первой неудачной операции, и проба на таком ответе зеленела бы при
  мёртвой базе.
* Отказ драйвера — это «не готов» (`503`), а не `500`: пробе нужен код ответа, а не разбор причины.
* Отмена запроса «не готов» не означает: проверка идёт через `suspendRunCatching`.
* Своего таймаута у пробы готовности **нет намеренно** — обёртка вокруг блокирующего вызова его не
  даёт, а ограничивает пробу kubelet своим `timeoutSeconds`, то есть тот, кто и решает, сколько
  ждать.
* Обе пробы **открыты**: это витрина и эксплуатация, а не данные.
* Версия в ответе — та же `mani.version`, из которой собирается тег образа. По ответу сервера
  находится образ, из которого он запущен.
* `uptimeSeconds` считается от старта **этого процесса**.

## 3. Ход

```
kubelet ──GET /health───────▶ 200 всегда, пока процесс жив   (livenessProbe, 10 с, 3 промаха)
kubelet ──GET /health/ready─▶ запрос в базу ─┬─ отвечает ──▶ 200 "ready"
                                             └─ нет ───────▶ 503 "storage unreachable"
                                                             (readinessProbe, 5 с, 2 промаха)

витрина ──GET /health───────▶ «ktor · kotlin/native · 1.4.2»
```

`initialDelaySeconds` у пробы готовности нет: нативный бинарь отвечает через 87 мс после старта.

## 4. Код

| Сервис | Код |
|---|---|
| shared | `shared/src/commonMain/kotlin/io/github/youndie/mani/feature/health/HealthResource.kt` — пути и `Health` |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/health/HealthRouting.kt` — оба маршрута |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/health/StorageHealth.kt` — порт «отвечает ли хранилище» |
| server-common | `server-common/src/jvmMain/kotlin/io/github/youndie/mani/feature/health/HealthRouting.jvm.kt` — `"jvm"` |
| server-common | `server-common/src/linuxX64Main/kotlin/io/github/youndie/mani/feature/health/HealthRouting.linuxX64.kt` — `"kotlin/native"` |
| server | `server/src/main/kotlin/io/github/youndie/mani/MongoStorageModule.kt` — реализация `StorageHealth` |
| server-native | `server-native/src/linuxX64Main/kotlin/io/github/youndie/mani/MongknStorageModule.kt` — она же на mongkn |
| инфраструктура | `.k8s-templates/deployment.yaml` — обе пробы с их периодами и порогами |
| клиент | `composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/health/domain/GetHealthUseCase.kt` |

## 5. Сценарии

### Scenario: готовность отвечает, пока отвечает база

* **Given:** база доступна.
* **When:** `GET /health/ready`.
* **Then:** `200` с телом `ready`.
* **Automated:** `HealthReadinessTest`

### Scenario: база не отвечает — под снимается с трафика

* **Given:** хранилище недоступно.
* **When:** `GET /health/ready`.
* **Then:** `503` с телом `storage unreachable`.
* **Automated:** `HealthReadinessTest`

### Scenario: живость не зависит от базы

* **Given:** хранилище недоступно.
* **When:** `GET /health`.
* **Then:** `200` — процесс жив, и перезапускать его незачем.
* **Automated:** `HealthReadinessTest`

### Scenario: то же на нативной сборке

* **Given:** нативный бинарь и настоящий `mongod`.
* **When:** `GET /health/ready`.
* **Then:** `200`.
* **Automated:** `ManiApiTest`

### Scenario: витрина показывает, кто ответил

* **Given:** сервер отвечает на `/health`.
* **When:** открыта витрина.
* **Then:** под заголовком строка `ktor · <build> · <version>` из ответа, а не зашитая.
* **And:** отказ `/health` **молчаливый** — строка не появляется, но войти в демо это не мешает.

## 6. Вне охвата

* Метрик (`/metrics`) в продукте нет.
* Проверки готовности к приёму трафика, отличной от «база отвечает», нет: других зависимостей у
  сервера тоже нет.
* Пробы старта (`startupProbe`) нет — нативный бинарь стартует за 87 мс.

## 7. Особенности

* **`startedAt` — верхнеуровневое свойство, вычисляемое при загрузке файла.** Момент, от которого
  считается `uptimeSeconds`, — это момент инициализации, а не старта `main()`. Для одного процесса
  разница неразличима, но в тесте, поднимающем приложение несколько раз, счётчик общий на весь
  прогон.
* **Проба готовности ходит в базу на каждый запрос**, каждые 5 секунд на реплику. Кэша нет
  намеренно: закэшированный ответ и есть та самая проба, которая зеленеет при мёртвой базе.
* **`GET /health` — единственный маршрут, отдающий типизированный объект без авторизации.** Он же
  единственное место, где `expect/actual` объявлен ради **строки**, а не ради платформенного
  вызова: тип сборки — единственное, чем сборки обязаны отличаться.
* **`livenessProbe` без базы означает, что мёртвая база даёт под, который жив и не готов** —
  ровно то, что нужно: трафик уходит, перезапусков нет, и когда база вернётся, под примет трафик
  сам.
