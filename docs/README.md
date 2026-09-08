# docs — mani

A budget planner written end to end in Kotlin: a Compose Multiplatform client for four platforms, a
Ktor server in two builds (JVM and Kotlin/Native), and one contract module shared by all of them.
The documentation is layered; the links run top to bottom.

```
[ Research — why the architecture is what it is ]
                     │
[ Feature (what it gives and why) + BDD ] ──▶ [ Client screen ]
                                                     │
                                                     ▼
                                     [ API: route, tier, status codes ]
                                                     │
                                                     ▼
                                     [ Module: what it owns, how it is built ]
```

| Layer | Directory | Answers | Source of truth |
|---|---|---|---|
| Research | `research/` | *why* it is this way; what is verified, what is a hypothesis | the artefacts each fact names |
| Feature | `features/` | *what* the system does and why; BDD scenarios | this repository |
| Client | `screens/` | what a person sees: states, actions, navigation | `composeApp/` |
| API | `api/` | method, path, auth tier, status codes | `shared/` plus the routes in `server-common/` |
| Module | `services/` | what it owns, dependencies, build, deploy | this repository |

The `services/` layer describes the **Gradle modules** of this repository rather than separate
services: the product lives in one repository, and a module here is the same unit of ownership a
service is in a distributed system.

**There is no backlog in the repository** — a decision, not an omission (commit `b00fafe`): the
working plan for one stretch of work is not something the repository owes its readers. If one is
ever wanted, the format is described in [SPEC.md](SPEC.md) §3.6, and the index generator
`backlog_index.py` comes from the `docs-bootstrap` skill and goes into `make check` next to the
others.

## Cross-cutting documents

The layers above describe the product. Alongside them sits a document about how that product is
checked:

- [TESTING.md](TESTING.md) — where a test lives, why hand-written fakes rather than mocks, why
  storage is checked against a real database, and which run-time traps turn a green build into an
  unverified one.

## Conventions

- **`id`** in the frontmatter equals the filename.
- Cross-layer links are ids in the frontmatter **and** ordinary markdown links in the body.
- One document, one entity. A feature touching four modules is **one** file with four entries in
  `involved_services`.
- BDD scenarios are written from the code, not from memory: status codes and refusal texts are
  checked against the source before they reach a scenario.
- **The primary reader is an agent.** Every document carries code anchors: a path to the feature
  directory, the handler, the view model. What lives in code (DTO fields, config keys) is not
  duplicated — a path is given instead. A copy rots, a path does not.
- **Language: English**, throughout the repository — code, documentation, build files and commit
  messages alike.

## Templates

`templates/` holds a copy of the document templates, so the format travels with the repository.
Sections marked `<!-- optional -->` may be deleted. The format contract is [SPEC.md](SPEC.md).

## Checks

```bash
pip install pyyaml
```

```bash
make check
```

The same, one by one:

```bash
python3 scripts/docs_check.py
python3 scripts/coverage_map.py --check
python3 scripts/bdd_report.py --repos ..
python3 scripts/code_anchors.py --repos ..
```

The first two are the gate; the last two are reports for a person and block nothing.

## Coverage map

The list below is **checked** against the files on disk: a document missing here, or a line with no
file behind it, fails `coverage_map.py`. The grouping and the descriptions are written by a person —
the machine only guards the membership. The section headings are markers for `coverage_map.py`, so
they are fixed strings; everything else in the map is ordinary text.

### Research (1)

- [x] [research-architecture](research/research-architecture.md) — verified facts, decisions, risks

### Services (5)

The server:
- [x] [server-common](services/server-common.md) — the whole server except database calls; jvm + linuxX64
- [x] [server](services/server.md) — the JVM build: the official Mongo driver, the development build
- [x] [server-native](services/server-native.md) — the native binary and the image that runs deployed

Shared and client:
- [x] [shared](services/shared.md) — the wire contract: resources, model, balance simulation
- [x] [composeApp](services/composeApp.md) — the whole interface, one body of code for four platforms

### Features (5)

Core:
- [x] [feature-transactions](features/feature-transactions.md) — budget rules, the forecast and the chart: what the product exists for
- [x] [feature-categories](features/feature-categories.md) — the label on a rule and the ledger filter

Getting in:
- [x] [feature-auth](features/feature-auth.md) — registration, sign-in, the session and its renewal
- [x] [feature-demo-sandbox](features/feature-demo-sandbox.md) — a throwaway account with ready data, one click away

Operations:
- [x] [feature-health](features/feature-health.md) — liveness, readiness and the "which build answered" line

### Screens / Flows (5)

Getting in:
- [x] [screen-welcome](screens/screen-welcome.md) — the welcome screen: a sample forecast and a one-click way into the sandbox
- [x] [screen-auth-form](screens/screen-auth-form.md) — the credentials form, shared by Login and Signup

Core:
- [x] [screen-main](screens/screen-main.md) — the forecast, the chart and the ledger of rules
- [x] [screen-history](screens/screen-history.md) — the same ledger with the current month's total
- [x] [screen-transaction-form](screens/screen-transaction-form.md) — creating and editing a rule, with the shift preview

### API (6)

- [x] [endpoint-transactions](api/endpoint-transactions.md) — the four budget-rule routes
- [x] [endpoint-categories](api/endpoint-categories.md) — the five category routes
- [x] [endpoint-auth](api/endpoint-auth.md) — registration, sign-in, session refresh
- [x] [endpoint-demo](api/endpoint-demo.md) — the sandbox, and seeding your own account
- [x] [endpoint-health](api/endpoint-health.md) — both probes, and how they differ
- [x] [endpoint-currencies](api/endpoint-currencies.md) — one route that nobody calls

## What is not here yet

Naming the gaps is more honest than leaving an impression of full coverage.

Every subject area of the server is now covered: each route `maniApiRouting()` registers is broken
down in the `api/` layer. What is not covered:

* **the product has no currency picker**, so there is no document for one either: why, in
  [endpoint-currencies](api/endpoint-currencies.md);
* **`:baselineprofile`** — Android baseline profile generation; it has no document of its own and is
  mentioned in [composeApp](services/composeApp.md);
* **theme and typography** (`composeApp/.../theme/`) — the styling decisions are written down
  nowhere;
* **the vendored `compose-charts`** is described only in terms of why it is vendored
  ([composeApp](services/composeApp.md)); what exactly was changed in it is not itemised.
