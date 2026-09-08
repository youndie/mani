# mani — how to work in this repository

## How to start a session

1. **[docs/research/research-architecture.md](docs/research/research-architecture.md)** — why the
   system is built the way it is. A task not read against this file looks like "do the obvious
   thing", and the obvious thing here has frequently been tried and rejected already: what and why
   is written down there.
2. **The document of the layer the task belongs to** — [docs/README.md](docs/README.md) holds the
   coverage map and an honest list of what is not in it yet. Changing behaviour: start with
   `features/`. Changing a route: `api/`. Changing a screen: `screens/`. Changing the build or the
   deploy: `services/`.
3. **Code anchors** are in every document: a path to the feature directory, the handler, the view
   model. One hop and you are in the right file.
4. **[docs/TESTING.md](docs/TESTING.md)** before writing a test — where it belongs, why fakes rather
   than mocks, and which run-time traps turn a green build into an unverified one.

When you change code, change the document that makes claims about it. A divergence between a
document and the code is a defect of the same weight as a broken test: `docs/` describes what
**is**, not what was intended.

## Layout

| Module | What it is | Targets |
|---|---|---|
| `:shared` | the wire contract: resources, model, serializers, balance simulation | android, ios, jvm, wasmJs, linuxX64 |
| `:composeApp` | the whole interface, one body of code | android, ios, desktop, wasmJs |
| `:server-common` | the whole server, **except** database calls | jvm, linuxX64 |
| `:server` | the JVM build; the only one that compiles on macOS | jvm |
| `:server-native` | the native binary — the image that runs deployed | linuxX64 |
| `:androidApp`, `:iosApp` | thin launcher modules, no logic | |

## Rules that have already been paid for here

* **Everything that is not a database call lives in `commonMain` of `:server-common`.** A
  superfluous `expect/actual` pair is two implementations that will diverge silently. There are
  exactly two right now: `readEnv` and `serverBuildKind`.
* **`TokenService` and `Sha256HashingService` are shared, not platform-specific.** A token issued by
  one build must be accepted by the other, and the hash format must match byte for byte what is
  already in the deployed instance's database.
* **Validation sits on the server, not only in the form.** A form is a convenience; behind it is
  open HTTP.
* **The id of the record being changed comes from the path, not from the body.** The reverse was a
  real hole.
* **"Not yours" and "does not exist" answer the same** (`403`): different answers would say which
  ids are taken.
* **Neither build has a logger in the common source set.** Diagnostics are `println` to stdout.
* Everything is written in **English** — comments, KDoc, test names, exception texts, `docs/`, this
  file, and commit messages (Conventional Commits).

## Commands

The full set is in [README.md](README.md). What gets asked for most often is here.

The set that runs on every pull request:

```bash
./gradlew :shared:jvmTest :server-common:jvmTest :server:test :composeApp:desktopTest
```

The native server runs **on Linux only**, and needs a real `mongod`:

```bash
./gradlew :server-native:linuxX64Test :server-native:linuxX64ReleaseTest
```

The release run is not optional: Kotlin/Native omits type-cast checks in release builds, and the
binary that ships in the image is the release one.

Style:

```bash
./gradlew ktlintCheck
```

Screenshots must be recorded and verified **on Linux**: the same code on macOS renders text
differently, by 1–4 % of the pixels.

Documentation checks:

```bash
make check
```

## What not to do

* **Do not bump the Ubuntu version in one place only.** The CI runner, the deploy runner and the
  `FROM` in `server-native/Dockerfile` are a pair through `libmongoc`: the soname is shared across
  branches, a substitution is caught neither by the build nor by startup, and it surfaces as a
  missing symbol on the first call into Mongo.
* **Do not introduce a second product version.** `mani.version` in `gradle.properties` is the only
  one; both the `/health` response and the image tag come from it.
* **Do not subscribe navigation to the token.** A subscription rebuilds the graph, and a new graph
  resets the screen to the start destination. Session expiry arrives as the
  `TokenRepository.expired` event.
