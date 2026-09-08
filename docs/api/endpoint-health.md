---
id: endpoint-health
title: Liveness and readiness
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

# API: liveness and readiness

> **The complete route reference** for the `/health` resource. The product has no generated schema
> (see [endpoint-auth](endpoint-auth.md)); this document *is* the reference.

## Routes — all of them

| Method and path | Tier | Purpose |
|---|---|---|
| `GET /health` | open | whether the process is alive; also names the build and the version |
| `GET /health/ready` | open | whether storage answers |

Open deliberately: they are called by the kubelet and by the welcome screen, and what they return is
not user data.

## Handlers

| Route | Handler |
|---|---|
| both | `server-common/.../feature/health/HealthRouting.kt` |
| the storage port | `server-common/.../feature/health/StorageHealth.kt` |
| the build kind | `server-common/src/jvmMain/.../HealthRouting.jvm.kt`, `server-common/src/linuxX64Main/.../HealthRouting.linuxX64.kt` |

## Response bodies

| What | Class |
|---|---|
| response of `GET /health` | `shared/.../feature/health/HealthResource.kt` (`Health`) |
| response of `GET /health/ready` | plain text, not JSON |

## Responses

### `GET /health`

| Condition | Status | Body |
|---|---|---|
| the process is alive | `200` | `Health` — `build`, `version`, `uptimeSeconds` |

It touches no dependencies: it has no other code. `build` is `jvm` or `kotlin/native`; `version` is
`mani.version` from `gradle.properties`, that is, the same number the image tag was built from.

### `GET /health/ready`

| Condition | Status | Body |
|---|---|---|
| storage answered | `200` | `ready` |
| storage did not answer, or the driver failed | `503` | `storage unreachable` |

The body is plain text: a probe needs a status code, not an analysis of the cause. A cancelled
request does not turn into a `503`.

## How they are called in production

From `.k8s-templates/deployment.yaml`:

| Probe | Path | Period | Threshold | Timeout |
|---|---|---|---|---|
| liveness | `/health` | 10 s | 3 | — |
| readiness | `/health/ready` | 5 s | 2 | 3 s |

The timeout is set by the **kubelet**, not by the server: the probe deliberately has none of its own
— a wrapper around a blocking call does not give you one, and how long to wait should be decided by
whoever is waiting.

## Quirks

* **Neither probe is authenticated**, which makes `/health` the cheapest way from outside to learn
  the deployed version and how long it has run without a restart.
* **`uptimeSeconds` counts from the initialisation of the routing file**, not from `main()`; in a
  test that brings the application up several times the counter is shared across the run.
* **`/health/ready` goes to the database on every call** — every 5 seconds per replica.
