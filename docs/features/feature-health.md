---
id: feature-health
title: Liveness, readiness and "which build answered"
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

# Liveness, readiness and "which build answered"

## 1. Overview

Two probes and one line on the welcome screen — three things that are easy to confuse, which is why
they are kept deliberately apart.

**`GET /health`** answers whether the process is alive, and **does not touch the database**.
**`GET /health/ready`** answers whether it has anything to work with, and really does ask the
database. The difference is not formal: a pod without a database should stop receiving traffic, not
go into a restart.

The third is part of why the project exists at all. `/health` names the **build**: `jvm` or
`kotlin/native`. The welcome screen prints that string instead of a hard-coded one, so a visitor can
see that their request was served by the native binary rather than by a sentence in the README.

## 2. Business rules

* `/health` depends on no dependency at all. A liveness probe that depends on the database turns its
  outage into a restart of every pod — which cures precisely nothing and merely adds cold starts to
  a problem that has already happened.
* `/health/ready` **must go to the database** rather than return the client's cached state: the
  driver considers a connection alive until the first failed operation, and a probe built on that
  answer would go green against a dead database.
* A driver failure means "not ready" (`503`), not `500`: a probe needs a status code, not an
  analysis of the cause.
* A cancelled request does not mean "not ready": the check goes through `suspendRunCatching`.
* The readiness probe deliberately has **no timeout of its own** — a wrapper around a blocking call
  does not give you one, and the kubelet bounds the probe with its own `timeoutSeconds`, which is
  the party that decides how long to wait.
* Both probes are **open**: this is the welcome screen and operations, not data.
* The version in the response is the same `mani.version` the image tag is built from. The server's
  answer locates the image it was started from.
* `uptimeSeconds` counts from the start of **this process**.

## 3. Flow

```
kubelet ──GET /health───────▶ 200 always, while the process is alive  (livenessProbe, 10 s, 3 misses)
kubelet ──GET /health/ready─▶ a query to the DB ─┬─ answers ──▶ 200 "ready"
                                                 └─ does not ─▶ 503 "storage unreachable"
                                                                (readinessProbe, 5 s, 2 misses)

welcome ──GET /health───────▶ "ktor · kotlin/native · 1.4.2"
```

The readiness probe has no `initialDelaySeconds`: the native binary answers 87 ms after startup.

## 4. Code anchors

| Service | Code |
|---|---|
| shared | `shared/src/commonMain/kotlin/io/github/youndie/mani/feature/health/HealthResource.kt` — the paths and `Health` |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/health/HealthRouting.kt` — both routes |
| server-common | `server-common/src/commonMain/kotlin/io/github/youndie/mani/feature/health/StorageHealth.kt` — the "does storage answer" port |
| server-common | `server-common/src/jvmMain/kotlin/io/github/youndie/mani/feature/health/HealthRouting.jvm.kt` — `"jvm"` |
| server-common | `server-common/src/linuxX64Main/kotlin/io/github/youndie/mani/feature/health/HealthRouting.linuxX64.kt` — `"kotlin/native"` |
| server | `server/src/main/kotlin/io/github/youndie/mani/MongoStorageModule.kt` — the `StorageHealth` implementation |
| server-native | `server-native/src/linuxX64Main/kotlin/io/github/youndie/mani/MongknStorageModule.kt` — the same on mongkn |
| infrastructure | `.k8s-templates/deployment.yaml` — both probes with their periods and thresholds |
| client | `composeApp/src/commonMain/kotlin/io/github/youndie/mani/feature/health/domain/GetHealthUseCase.kt` |

## 5. Scenarios (BDD)

### Scenario: readiness answers while the database answers

* **Given:** the database is reachable.
* **When:** `GET /health/ready`.
* **Then:** `200` with the body `ready`.
* **Automated:** `HealthReadinessTest`

### Scenario: the database does not answer — the pod is taken out of traffic

* **Given:** storage is unreachable.
* **When:** `GET /health/ready`.
* **Then:** `503` with the body `storage unreachable`.
* **Automated:** `HealthReadinessTest`

### Scenario: liveness does not depend on the database

* **Given:** storage is unreachable.
* **When:** `GET /health`.
* **Then:** `200` — the process is alive and there is no reason to restart it.
* **Automated:** `HealthReadinessTest`

### Scenario: the same on the native build

* **Given:** the native binary and a real `mongod`.
* **When:** `GET /health/ready`.
* **Then:** `200`.
* **Automated:** `ManiApiTest`

### Scenario: the welcome screen shows who answered

* **Given:** the server answers `/health`.
* **When:** the welcome screen is opened.
* **Then:** below the heading is the line `ktor · <build> · <version>` from the response, not a
  hard-coded one.
* **And:** a failing `/health` is **silent** — the line does not appear, and that does not stop
  anyone entering the demo.

## 6. Out of scope

* The product has no metrics (`/metrics`).
* There is no readiness check other than "the database answers": the server has no other
  dependencies either.
* There is no `startupProbe` — the native binary starts in 87 ms.

## 7. Quirks

* **`startedAt` is a top-level property evaluated when the file is initialised.** The moment
  `uptimeSeconds` counts from is initialisation, not the start of `main()`. For a single process the
  difference is imperceptible, but in a test that brings the application up several times the
  counter is shared across the whole run.
* **The readiness probe goes to the database on every request**, every 5 seconds per replica. There
  is no cache deliberately: a cached answer is exactly the probe that goes green against a dead
  database.
* **`GET /health` is the only route returning a typed object without authentication.** It is also
  the only place where `expect/actual` is declared for a **string** rather than for a platform call:
  the build kind is the one thing the builds are obliged to differ on.
* **A `livenessProbe` without the database means a dead database yields a pod that is alive and not
  ready** — exactly what is wanted: traffic drains away, nothing restarts, and when the database
  comes back the pod takes traffic again on its own.
