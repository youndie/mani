# Testing mani

How tests are written and run in this repository. This document is the target: where the code
disagrees with it, the code is wrong.

One source tree builds into six artifacts — clients for Android, iOS, desktop and the browser, and
a server compiled twice, to the JVM and to a native Linux binary. That is what makes testing here
different from the usual: **shared code does not have one test suite**, and where a test lives is
decided by the platforms it must run on, not by convenience.

## The stack

| For | We use |
| :--- | :--- |
| Annotations and assertions | `kotlin.test` |
| Coroutines | `kotlinx-coroutines-test` (`runTest`) |
| Server routes | `ktor-server-test-host` (`testApplication`) |
| Client networking | `ktor-client-mock` (`MockEngine`) |
| A database for server tests | flapdoodle on the JVM, a containerised `mongod` for the native build |
| The dependency graph | `koin-test` (`verify()`) |
| Client settings | `multiplatform-settings-test` (`MapSettings`) |
| Screens | `compose.uiTest` (`runComposeUiTest`) |
| Screenshots | [viddik](https://github.com/youndie/viddik) |

What the project does not have, and does not gain without a conversation first:

**A mocking library.** Fakes are written by hand. The reason is not taste: `commonTest` in
`:server-common` compiles for the JVM *and* for linuxX64, and mocking libraries are JVM-only. A
single `mockk()` in a shared test makes that test JVM-only — which leaves half the server unchecked
on the build that actually ships. There is [a section on fakes](#fakes-instead-of-mocks).

**A Flow-testing library.** A `StateFlow` is read directly: `runCurrent()` until the state is
there, then assert on `.value`. That is enough, and an extra dependency in the shared suite runs
into the same platform limit.

## Annotations: `kotlin.test`, never `org.junit`

Always `kotlin.test.{Test, BeforeTest, AfterTest}`, including in JVM-only tests.

```kotlin
import kotlin.test.Test          // ✅
import kotlin.test.BeforeTest
import kotlin.test.AfterTest

import org.junit.Test            // ❌
```

`org.junit.*` nails a test to the JVM, and moving it into a shared suite later means rewriting it.
`kotlin.test` expands into whichever runner the build selects and travels with it.

One module is exempt, and only one: `:baselineprofile`. Macrobenchmark and baseline-profile
generation are built on JUnit 4 rules — `@get:Rule` with `BaselineProfileRule`, `@RunWith` — which
`kotlin.test` cannot express. Those files are Android instrumentation, not unit tests, and they
never move anywhere.

## Seven suites and what belongs in each

| Suite | Platforms | What it covers |
| :--- | :--- | :--- |
| `:shared:jvmTest` | JVM | The contract: forecast simulation, the demo seed. Pure functions, no network, no database |
| `:server-common:jvmTest` | JVM | Shared server code, plus token compatibility with the old format |
| `:server-common:linuxX64Test` | Kotlin/Native | The same shared code on the other platform |
| `:server:test` | JVM | The JVM build end to end — routes, storage, DI — against a real `mongod` |
| `:server-native:linuxX64Test` | Kotlin/Native | The native build end to end, debug binary |
| `:server-native:linuxX64ReleaseTest` | Kotlin/Native | The same, **release** binary — the one that ships |
| `:composeApp:desktopTest` | JVM (desktop) | View models, use cases, the cache, token storage, the Koin graph |
| `:composeApp:commonTest` | desktop, wasmJs, iOS | Pure functions and Compose tests that must run on every target |

Alongside them: `:composeApp:viddikVerify`, the screenshot comparison.

### Where to put a new test

1. **Shared server code goes in `commonTest` of `:server-common`.** It then runs on both platforms
   and catches the difference between them. Token issuing and verification, password hashing and
   the validation rules are all checked this way.
2. **A test needing `java.*`, a JVM library or a reference implementation goes in `jvmTest`.**
   `LegacyTokenCompatibilityTest` signs a token with `java-jwt` to prove our verifier still accepts
   tokens issued before we replaced it.
3. **A storage implementation is tested in its own build's suite** (`:server:test` or
   `:server-native:linuxX64Test`). The implementations share no code.
4. **Client logic goes in `:composeApp:desktopTest`.** View models and use cases are not
   platform-specific, but one suite is enough — running them four times buys nothing.
5. **A test that must run on every client target goes in `:composeApp:commonTest`.** That is for
   what breaks per platform: navigation graph stability, number formatting.

## Naming and the shape of a test

A name is an English sentence in backticks, stating the property under test:

```kotlin
@Test
fun `a stranger cannot patch a foreign transaction through the id in the body`()

@Test
fun `a rule the product cannot honour is refused`()
```

**No commas in a name** — Kotlin/Native rejects them when compiling the test. Where a name wants to
be long and listy, write it in camelCase instead: `refreshTokenIsRefusedWhereAccessIsExpected`.

Above the test sits a KDoc naming **the failure the test guards**, not restating the code:

```kotlin
/**
 * The id is taken from the path, not from the body.
 *
 * Ownership was checked against `path.id` while the document to write was picked by the `id` in
 * the body: sending a PATCH to YOUR OWN record with a stranger's in the body rewrote theirs and
 * moved it to the caller. It answered 200 while doing it, so from outside everything looked fine.
 */
```

This is not decoration. Six months on, the value of a test is what it catches; the mechanics are
visible in the body. If there is no failure to name, ask what the test is guarding at all.

Assertions carry a message whenever "did not match" does not explain itself:

```kotlin
assertEquals(1, ownersNow.size, "the foreign record changed owner")
```

## Fakes instead of mocks

A fake is an ordinary class implementing a port. It lives in the test suite next to whoever uses it.

```kotlin
private class FakeUserRepository(initial: List<User>) : UserRepository {
    val stored = initial.toMutableList()
    var searchedPrefix: String? = null

    override suspend fun findByUsernamePrefix(prefix: String): List<User> {
        searchedPrefix = prefix
        return stored.filter { it.username.startsWith(prefix) }
    }

    override suspend fun delete(userId: String) {
        stored.removeAll { it.id == userId }
    }
    // …the rest of the port
}
```

What that buys over a mock:

* **The compiler watches the contract.** Change the port and the fake stops compiling, showing you
  every test whose assumptions went stale. A mock keeps answering a method that no longer exists in
  that shape.
* **A fake holds state.** `stored` after the run shows what survived, so you rarely need to assert
  "was this called" — the result is visible.
* **It runs everywhere.** A test built on fakes can move into `commonTest` unchanged.

Fakes to reuse rather than reinvent:

| Fake | Where | For |
| :--- | :--- | :--- |
| `FakeTransactionsRepository` | `composeApp/src/desktopTest/…/transaction/data` | The rule list; `shouldCrash` turns on a network failure |
| `StateFlowDataSource`, `FakeCategoriesDataSource` | `composeApp/src/desktopTest/…/category` | A `StateFlow`-backed data source; `withError` turns on a failure |
| `MapSettings` | from `multiplatform-settings-test` | In-memory settings for cache tests |

The storage fakes in `DemoSandboxCleanerTest` are `private` and stay in their file. That is
deliberate: while one test uses them, making them public only invites bending them to a second case
and breaking the first. When a second test needs them, move them into their own file in the same
suite rather than copying them.

Swapping one dependency inside an assembled graph is not a fake's job — use a Koin module, see
[The Koin graph](#the-koin-graph).

## Storage is tested against a real database

A fake is no good where storage itself is what you are testing. Everything that can break here
breaks **silently**: a filter on `_id` sent as a string matches nothing; an amount written as text
instead of `decimal128` is stored without complaint and read back as the wrong type. A fake answers
correctly in both cases — it knows nothing about BSON.

So storage tests run against a real `mongod`.

**JVM.** The shared harness `maniTest` in `server/src/test/kotlin/ManiTestServer.kt` starts an
embedded `mongod` (flapdoodle) and assembles the application exactly as production does:

```kotlin
@Test
fun `a malformed id in the path is a bad request`() = maniTest {
    val client = createClient { }
    val token = client.signIn("malformed", "hunter22")

    assertEquals(HttpStatusCode.BadRequest, client.deleteTransaction(token, "not-an-id").status)
}
```

The client helpers live there too — `signIn`, `createTransaction`, `categories`, `startSandbox`. A
new test starts from those, not from a harness of its own.

**Kotlin/Native.** A `mongod` runs alongside; the address comes from `MANI_TEST_MONGO_HOST` and
defaults to `127.0.0.1:27017`. Each run works in its own database (`TestMongo.uniqueDatabaseName`)
and drops it afterwards: there are many runs and one `mongod`.

```bash
docker run -d --name mani-mongo -p 27017:27017 mongo:8
```

## Two builds mean two checks

The server is compiled twice from one source tree. Hence the rule worth keeping in mind for every
server test:

> **Shared code is tested once. Code with two implementations is tested twice.**

Routes, services, validation rules and token issuing live in `:server-common` and need a single
test in the shared suite. Storage, and anything that reaches a driver, needs a test in each build:

* the hole that let a record be written by the id in the body lived on the seam between a shared
  route and a write filter belonging to each implementation — fixing one would have left the other
  open;
* a malformed `ObjectId` is rejected by different exceptions: the `ObjectId` constructor on the
  JVM, a field serializer on native. All they share is how the refusal is turned into a response.

Paired tests should point at each other in their KDoc, or the second one gets lost when the first
is edited.

## The release run of the native build is mandatory

Kotlin/Native **omits type-cast checks in release builds**. Code that fails with a catchable
exception in debug reaches undefined behaviour in release. That is how `/auth/refresh` answered 500
on the stand with a fully green run: the test binary was the debug one, and the image ships the
release one.

`:server-native:linuxX64ReleaseTest` is therefore not an extra check but a required one.

## Client: view models

`Dispatchers.Main` is replaced by a test dispatcher in `@BeforeTest` and reset in `@AfterTest`. The
replacement must happen **before** the view model is constructed: its `init` already launches into
`viewModelScope`.

State is read directly, without a Flow-testing library: `runCurrent()` drives the scheduler to the
moment you care about, then assert on `.value`.

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelCacheTest {
    private val repository = FakeTransactionsRepository()
    private lateinit var viewModel: TransactionsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repository.showingCacheFrom.value = Instant.fromEpochSeconds(1_800_000_000)
        startKoin { modules(testModule(repository)) }
        viewModel = get()
    }

    @Test
    fun cachedDataSaysWhenItWasTaken() = runTest {
        while (viewModel.observe.value.data.isEmpty()) {
            runCurrent()
        }

        assertNotNull(viewModel.observe.value.showingCacheFrom, "history passed stale data off as fresh")
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }
}
```

Wait with a loop over a condition, not with `advanceTimeBy` and a guessed number: the condition
describes what you are waiting for, the number describes today's implementation.

## Client: networking

The network layer is tested with `MockEngine`, not a live server. Importantly, **the test client
must mirror the production one**. A missing default content type makes the request fail before it
is ever sent, and the test then fails somewhere other than the bug.

```kotlin
private fun client(status: HttpStatusCode, body: String) = HttpClient(
    MockEngine { respond(ByteReadChannel(body), status, headersOf("Content-Type", "application/json")) },
) {
    install(Resources)
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    // The same default the production client sets.
    defaultRequest { contentType(ContentType.Application.Json) }
}
```

When the logic under test lives inside the client's own configuration — as the refresh-token
exchange did, inside the `refreshTokens` block — extract it into a function; otherwise a live
server is the only thing that can exercise it. Whatever the function cannot call for itself, such
as marking a request as the refresh request, comes in as a parameter.

## Compose: screens

Screen tests use `runComposeUiTest`, not JUnit rules. What gets tested is the **stateless** Content
function: it receives a ready state and callbacks, and no view model enters the test.

```kotlin
@OptIn(ExperimentalTestApi::class)
class AuthComponentTest {
    @Test
    fun authComponentTest() = runComposeUiTest {
        setContent { AuthComponentImpl(state = state.value, onUsernameChanged = { … }) }

        onNodeWithTag("username").performTextInput("TESTER")
        onNodeWithTag("errorMessage").assertTextEquals("Error!")
    }
}
```

Nodes are found by `testTag`, not by visible text: copy changes often, tags do not. The tag goes
where the element is drawn.

The exception is text that **is** the property under test. The ledger labels today's row with the
word TODAY rather than a date, and a filter chip's caption is how the toggle reports its state —
there is nothing else about either to assert. `SimpleMainTest` and `FiltersTest` read those by text
deliberately. The rule is about locating a node while checking something else; it is not a ban on
asserting what a screen says.

## Screenshots

Screenshots are recorded and compared by [viddik](https://github.com/youndie/viddik). The scenes
live in `composeApp/src/desktopTest/kotlin/…/screenshots/Screens.kt`, the goldens in
`composeApp/src/desktopTest/snapshots`.

```bash
./gradlew :composeApp:viddikVerify   # compare
./gradlew :composeApp:viddikRecord   # rewrite the goldens
```

**Record and verify on Linux only.** The same code renders text differently on macOS — 1–4 % of
the pixels differ, far past any tolerance worth keeping. That is also why the comparison is not a
pull-request check: a golden that reproduces on one operating system does not belong in a merge
gate.

The tolerance is deliberately strict (0.01 % of pixels, zero per channel): a change to the colour
of amounts once slipped through a looser one.

A screen drawn from the current date must take it as a parameter, or the golden lives a day:

```kotlin
fun TransactionsListContent(state: …, today: LocalDate = today())
```

## The Koin graph

Three tests cover the graph: `ClientKoinModuleTest` (client), `DiTest` (JVM server),
`DiNativeTest` (native server). An unsatisfied dependency is otherwise found not by a test but by a
black screen.

Two traps worth knowing:

1. **`verify()` walks only the modules you list.** View models registered inside components via
   `rememberKoinModules` never reach the application graph and have to be added to the check by
   hand. That is how `SeedUseCase` once slipped through: green tests, and the app died on opening
   the main screen.
2. **Generic types are erased.** To Koin, `DataSource<Category>` and `DataSource<Transaction>` are
   the same key, and whoever registered last wins for everybody. The sources are bound by name
   (`named(TRANSACTIONS_SOURCE)`), and `DataSourceBindingTest` makes sure an unnamed binding does
   not come back.

Swapping one dependency in a test is done with a module declared **after** the shared ones: Koin
takes the last definition.

```kotlin
val small = module { single { DemoService(get(), get(), get(), get(), get(), maxLiveSandboxes = 2) } }

maniTest(overrides = listOf(small)) { … }
```

## Running the suites

The set that runs on every pull request:

```bash
./gradlew ktlintCheck :shared:jvmTest :server-common:jvmTest :server:test :composeApp:desktopTest :composeApp:wasmJsTest
```

```bash
./gradlew :server-common:linuxX64Test :server-native:linuxX64Test :server-native:linuxX64ReleaseTest
```

**The native suites need Linux.** mongkn publishes for linuxX64 and nothing else, so the native
server cannot be linked on macOS at all — it compiles there and fails at link time. Screenshot
goldens are recorded on Linux for the same practical reason: they only reproduce there. What stays
on macOS is what Linux cannot build — the `ios*` targets, `xcodebuild` and the simulator.

## Traps that turn a green run into an unchecked one

Worth knowing before they cost you an afternoon.

**A native test task can quietly skip.** On an incremental run Gradle marks `linuxX64Test` and
`linuxX64ReleaseTest` as `UP-TO-DATE` even after the link task has just executed. The build is
green while `build/test-results` still holds the previous run's results.

**`--rerun` applies only to the task it follows.** `./gradlew a b c --rerun` re-runs `c` and leaves
`a` and `b` alone. Force one task per invocation.

**Trust the timestamps of the result files, not `BUILD SUCCESSFUL`.** It is the only reliable way
to tell a run from the absence of one:

```bash
find . -path '*/build/test-results/*' -name '*.xml' -newermt '-5 minutes' | wc -l
```

**A pipeline swallows the exit code.** `./gradlew … | tail` returns the status of `tail`, not of
the build. Write the output to a file and check `$?` before filtering anything.

**`:composeApp:wasmJsTest` needs a browser.** The task launches `ChromeHeadless`; without one it
fails even though the wasm compilation succeeded. CI has one.

## Test the test

A green test does not prove it guards anything — it might pass equally well without the change it
accompanies. So check a new test by **mutation**: undo the change and confirm the test goes red,
and that it is the one going red.

```bash
# disable the check, run, put it back
./gradlew :server-native:linuxX64Test --tests '*ManiApiTest*'
```

Two lessons from doing this:

* if the mutation breaks **more** tests than expected, the change reaches further than the commit
  message claims;
* if the mutation breaks **nothing**, the test is not guarding what it was written for. That is how
  we learned the unparseable-body check passes without `StatusPages` — Ktor already answered that
  way, and the test actually guards something else: that the blanket exception handler does not
  spoil it. The comment on the test had to be corrected.

And the reverse: an assertion that cannot fail is not a check. An `assertEquals` on a status
followed by an `assertTrue` derived from that same status only creates the appearance of coverage.
