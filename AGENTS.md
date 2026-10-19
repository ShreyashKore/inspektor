# AGENTS.md

This file provides guidance to AI coding agents (Claude Code, Codex, Cursor, etc.) when working with code in this repository. It is the single source of truth; `CLAUDE.md` only imports it.

## Overview

Inspektor is a Kotlin Multiplatform HTTP inspection library for Ktor (think Chucker, but multiplatform). It installs as a Ktor client plugin, persists every request/response to a local SQLite database, and ships its own Compose Multiplatform UI for browsing transactions. It also supports *overriding* outgoing requests / incoming responses for testing.

Targets: **Android, JVM (Desktop), and iOS** (iosArm64 + iosSimulatorArm64). The published artifact is `com.gyanoba.inspektor:inspektor`.

The repo has three modules: `:inspektor` (the library), `:inspektor-no-op` (an empty drop-in replacement, published as `com.gyanoba.inspektor:inspektor-no-op`), and `:sample` (a Compose Multiplatform demo app). `iosApp/` is the Xcode project for the sample.

## Commands

Use the Gradle wrapper (`./gradlew`). The toolchain expects **JDK 17**; the Android target compiles to JVM 1.8 bytecode.

```bash
# Compile the library for a single target (fast feedback loop)
./gradlew :inspektor:compileKotlinJvm

# Run all library unit tests (CI runs only the JVM target)
./gradlew :inspektor:jvmTest
./gradlew :inspektor-no-op:jvmTest

# Run a single test class / method (JVM)
./gradlew :inspektor:jvmTest --tests "RetentionManagerTest"
./gradlew :inspektor:jvmTest --tests "RetentionManagerTest.checkAndCleanUp should clean up after cleanupFrequency for short retention"

# Android lint
./gradlew lint

# Build the sample app (Android + desktop)
./gradlew :sample:assembleDebug
./gradlew :sample:run        # desktop (mainClass = "MainKt")
```

### Public API / binary compatibility (important)

The library uses `explicitApiWarning()` and the [binary-compatibility-validator](https://github.com/Kotlin/binary-compatibility-validator). Any change to the **public** API surface will fail `apiCheck`. If a public API change is intentional, regenerate the signature dump and commit it:

```bash
./gradlew apiCheck   # verify
./gradlew apiDump    # regenerate inspektor/api/*.api after intentional changes
```

Because of this, prefer `internal` visibility for anything not meant to be public. The `com.gyanoba.inspektor.data` package and the sample module are excluded from API validation (see `apiValidation {}` in the root `build.gradle.kts`).

## Architecture

### The plugin pipeline (`Inspektor.kt`)

`Inspektor` is a Ktor `ClientPlugin<InspektorConfig>` built with `createClientPlugin`. It hooks into custom interception points defined in `utils/KtorHooks.kt` (`SendStateHook`, `SendMonitoringHook`, `ReceiveStateHook`, `ResponseReceiveHook`) rather than the stock Ktor logging hooks, because it needs to *mutate* requests/responses for the override feature. The flow per call:

1. **SendStateHook** – decide whether to log (via `filter {}` predicates), create an `HttpClientCallLogger`, run retention cleanup, and apply any matching **request** override (swap body/headers, recording the originals).
2. **SendMonitoringHook** – capture request info/headers/body. The request body is read non-destructively by `OutgoingContent.observe()` (`utils/ObservingUtils.kt`), which tees the byte stream into a logging channel.
3. **ReceiveStateHook** – capture response info/headers, and apply any matching **response** override (replacing the response via `replaceResponse`).
4. **ResponseObserver** – read the response body (only installed when `level.body` is true).

`LogLevel` (NONE/INFO/HEADERS/BODY) gates how much is captured. A per-call `HttpClientCallLogger` (`HttpClientLogger.kt`) accumulates state into a `MutableHttpTransaction`, then writes it to the data source. It uses two `Job` monitors + atomic flags so the response log always waits for the request log to be inserted first (`closeResponseLog` joins `requestLoggedMonitor`), guaranteeing the row exists before it's updated.

### Data layer (`data/`)

- **SQLDelight** is the persistence engine. The schema and all queries live in `inspektor/src/commonMain/sqldelight/.../HttpTransaction.sq` (database name `InspektorDatabase`, package `com.gyanoba.inspektor.data`). Generated code is what `InspektorDataSourceImpl` calls. Header sets and `Instant` columns are stored via `ColumnAdapter`s in `data/adapters/` (JSON-encoded).
- `InspektorDataSource` / `InspektorDataSourceImpl` wraps the queries with coroutine `Dispatchers.IO`. `getTransaction` / `getTransactionFlow` return **nullable** types and use the `*OrNull` query variants.
- The SQL driver is created per-platform via `expect object DriverFactory` (`data/Db.kt`), with actuals in `data/Db.{android,jvm,apple}.kt`. The in-memory driver used by tests is **not** part of `DriverFactory` — it is `expect fun createTempDbDriver()` in the unpublished `:inspektor-test-fixtures` module (`src/commonMain/kotlin/utils/TempDbDriver.kt`, with actuals per platform), which every module's tests share along with `DbTestBase`. It has to sit in that module's `main` source sets, because Kotlin Multiplatform cannot export a test source set to another module. Keep it out of `:inspektor-core` and every published module: the Android actual needs `JdbcSqliteDriver`, which bundles ~6 MB of desktop SQLite binaries (macOS `.dylib`, Windows `.dll`), and declaring that in a published `androidMain` packages all of it into every consumer's APK. Depend on `:inspektor-test-fixtures` from test source sets only, and never give it the `vanniktech` publish plugin. `:inspektor-core`'s `androidUnitTest` declares `sqlDelight.driver.sqlite` itself for the same reason.
- **Overrides** (`data/Override.kt`, `data/OverrideRepository.kt`) are stored separately using **KStore** (a JSON file `overrideStore`), not SQLDelight. An `Override` has a `RequestType`, a list of `Matcher`s (`UrlMatcher`, `UrlRegexMatcher`, `HostMatcher`, `PathMatcher`), and an `OverrideAction` (FixedRequest / FixedResponse / FixedRequestResponse). Matching logic lives in `Matcher.matches(...)` in `Inspektor.kt`.

### UI (`ui/`)

Compose Multiplatform with `androidx.navigation.compose`. `ui/App.kt` is the `NavHost` (transactions → transaction detail → overrides → edit/add override). ViewModels (`*ViewModel.kt`) use AndroidX `ViewModel` and observe the data source via `Flow`/`StateFlow`. Entry point is `expect fun openInspektor()` — Android opens an Activity, Desktop opens a Swing `JFrame` hosting a `ComposePanel`, iOS shows a bottom sheet.

### Platform structure (`expect`/`actual`)

`commonMain` holds nearly all logic. Per-platform `actual`s live under `androidMain`, `jvmMain`, and `appleMain` (a hand-created source set shared by both iOS targets — see `dependsOn(commonMain)` wiring in `inspektor/build.gradle.kts`). Platform abstractions in `platform/`: `NotificationManager`, `FileSharer`, `FileUtils`, `AppName`, `Os`. Each has an `expect` in commonMain and actuals per target.

### The no-op module (`:inspektor-no-op`)

`:inspektor-no-op` mirrors `:inspektor`'s **public** API exactly — same package (`com.gyanoba.inspektor`) — with empty bodies. It owns the `Inspektor` plugin and `InspektorConfig`; `LogLevel`, `InspektorRequest`, `UnstableInspektorAPI` and `data.setApplicationId()` come from `:inspektor-core-no-op`, and `openInspektor()` from `:inspektor-ui-no-op`, both re-exported with `api(...)` the way `:inspektor` re-exports `:inspektor-core` and `:inspektor-ui`. `Inspektor` is `createClientPlugin("Inspektor", ::InspektorConfig) {}` with no hooks at all, so it cannot read, buffer, persist or log anything. Consumers use it as `releaseImplementation` / `prodImplementation` and keep the real library on the debug/dev variant.

Constraints for this module:

- Its only external dependency is **`ktor-client-core`**. Its other two, `:inspektor-core-no-op` and `:inspektor-ui-no-op`, are zero-dependency no-op modules themselves, and must stay that way. Never add Compose, SQLDelight, kstore, serialization, coroutines or any platform dependency to any no-op module — the whole point is that they drag nothing into a release binary. The same holds for `:inspektor-okhttp-no-op` (OkHttp and `:inspektor-core-no-op` only) and `:inspektor-urlsession-no-op` (`:inspektor-core-no-op` only).
- It contributes **no** AndroidManifest entries, resources, assets or `androidx.startup` initializers. Its Android namespace is `com.gyanoba.inspektor.noop` (deliberately different from `:inspektor`) so the two can never collide.
- **Any public API change to `:inspektor-ktor` or `:inspektor-ui` must be mirrored in its no-op twin**, otherwise consumers' code stops compiling when they swap variants. The umbrella `:inspektor` has no declarations of its own, so run `./gradlew apiDump` and diff `inspektor-ktor/api/*` against `inspektor-no-op/api/*`, and `inspektor-ui/api/*` against `inspektor-ui-no-op/api/*`.
- `src/commonTest/kotlin/NoOpInspektorTest.kt` is the behavioural contract: request/response pass through untouched, the body is re-readable, and user-supplied `filter` / `sanitizeHeader` predicates are never invoked. Keep those tests passing.
- The `:sample` app exercises both artifacts via Android `dev`/`prod` product flavors. Because the sample calls `install(Inspektor)` from `commonMain`, the swap is done with a `dependencySubstitution` on `prod*` configurations (see the bottom of `sample/build.gradle.kts`), not `prodImplementation` — KMP metadata compilation cannot express a per-flavor dependency. `./gradlew :sample:assembleDebug` builds both flavors, so CI catches API drift between the two modules.

### Remote inspection (`remote/`, in `:inspektor-core`)

Eagly, the desktop device viewer, reads a running app's transactions and edits its overrides
through a server in `com.gyanoba.inspektor.remote`. **[docs/REMOTE_PROTOCOL.md](docs/REMOTE_PROTOCOL.md)
is the spec; change it together with the code.** Key facts:

- **Off by default.** `InspektorRemote.start()` (`@UnstableInspektorAPI`) turns it on. Its no-op
  twin lives in `:inspektor-core-no-op`, so keep the two APIs identical (`apiDump`).
- **No authentication, on purpose.** The scope is development builds, like Metro. What keeps it
  safe:
  - production ships the no-op;
  - the listener is local to the device: an abstract socket on Android, `127.0.0.1` on iOS and
    JVM, never a network interface;
  - `FrameReader` rejects an oversized or non-JSON frame before reading it, which is what stops
    a browser's HTTP request.

  Don't add an HTTP or WebSocket endpoint without adding a token.
- **Live events come from `InspektorDataSourceImpl.changes`.** It's an internal `SharedFlow`
  emitted on every insert, update and delete. Each session re-reads the row by id. The flow never
  suspends the recording side (`DROP_OLDEST`). Writes that bypass `InspektorDataSourceImpl` are
  invisible to remote clients.
- **The platform code is small:** `openRemoteListener`, `remoteAppInfo` and `logToDevice`.
  - Android: a `LocalServerSocket`. Closing it doesn't wake `accept()`, so `close()` connects once
    first.
  - iOS: POSIX sockets, polled rather than blocking in `accept()`, with `SO_NOSIGPIPE` set.
    `iosSimulatorArm64Test` runs the iOS listener for real.
  - JVM: a `ServerSocket`.
- **Tests:** JVM tests in `src/jvmTest/kotlin/remote` drive sessions over in-memory pipes and the
  server over a real loopback socket on port 0.
- **Manual checks:** `scripts/remote-client.py PORT` (standard library only) prints the hello,
  follows events live, and fetches bodies (`--body ID`) and overrides (`--overrides`).
  - On Android, get a port with
    `adb forward tcp:0 localabstract:inspektor_$(adb shell pidof <pkg> | tr -d '\r')_kotlin`.
    If the device drops offline, adb removes its forwards; run it again.
  - `curl http://127.0.0.1:PORT/` must fail (`Received HTTP/0.9`), and the app's log must say
    `Closed a connection that broke the protocol`.

### HAR export (`har/Har.kt`)

`List<HttpTransaction>.toHarLogString()` serializes transactions to the [HAR 1.2](http://www.softwareishard.com/blog/har-12-spec/) format for external analysis.

## Conventions & gotchas

- **Dependency injection is manual and ad hoc.** `InspektorConfig` lazily wires real singletons (`InspektorDataSourceImpl.Instance`, `OverrideRepositoryImpl.Instance`, `NotificationManager()`) in its `init` block, guarded by the global `internal var IsTest` flag. Tests set `IsTest = true` and inject in-memory fakes (see `commonTest/utils/TestBase.kt`, which builds an `HttpClient(MockEngine)` with a temp DB and in-memory KStore).
- **Unstable APIs** are annotated with `@UnstableInspektorAPI` (an opt-in `RequiresOptIn` error). `retentionDuration`, `showNotifications`, `setApplicationId`, etc. are gated behind it.
- **Platform setup the consumer must do** (from the README): iOS requires adding `-lsqlite3` to *Other Linker Flags*; Desktop/JVM requires calling `setApplicationId("...")` before use (determines where the DB file is stored).
- **Logging** uses the simple `println`-based helpers in `utils/InternalLogger.kt` (`log` / `logErr`), not a logging framework.
- Versions are centralized in `gradle/libs.versions.toml` (Kotlin, Compose, Ktor, SQLDelight, etc.). The version badges in `README.md` may lag the catalog — trust the catalog. Mocking in tests uses **Mokkery**.
- Release/publish is via the vanniktech maven-publish plugin, triggered by `v*.*.*` git tags (`.github/workflows/publish.yaml`). `VERSION_NAME` lives in `gradle.properties`.
