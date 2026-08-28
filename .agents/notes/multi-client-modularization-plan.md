# Multi-client, multi-platform Inspektor

## Context

Inspektor is today a single KMP artifact (`com.gyanoba.inspektor:inspektor`) that is *structurally* a Ktor plugin: `install(Inspektor)` is the only way in, and the README calls it "an HTTP inspection library for Ktor". The goal is to make it a **capture-agnostic HTTP inspector** — the same on-device database, UI, override engine and HAR export driven by OkHttp, URLSession, Ktor, and eventually React Native and Flutter.

The reason to do this now, before building any of that, is that consumers currently pay for everything. `:inspektor` is one artifact carrying Compose Multiplatform, SQLDelight, KStore, Ktor and the whole UI. An Android team that wants an OkHttp interceptor would pull Ktor and Compose; a React Native app would pull the entire Compose UI onto iOS. Splitting is the prerequisite, not a parallel nicety.

**The load-bearing discovery from exploration: the library is barely coupled to Ktor at all.** Of 102 Kotlin files, 7 touch `io.ktor`, and only 2 (`Inspektor.kt`, `utils/KtorHooks.kt`, 555 lines) touch Ktor *client plugin machinery*. The 4,815-line Compose UI has zero Ktor imports. The 414-line data layer has zero. The SQLDelight schema stores headers as `Set<Map.Entry<String, List<String>>>` and dates as `kotlin.time.Instant` — no Ktor types anywhere, and it already has `responseTlsVersion` / `responseCipherSuite` columns that Ktor cannot populate but OkHttp can.

Most importantly, **the recorder seam already exists**. `HttpClientCallLogger` (`inspektor/src/commonMain/kotlin/com/gyanoba/inspektor/HttpClientLogger.kt`) has no Ktor imports; every parameter is `String`, `Long?`, `Instant`, `Throwable` or a header set. An OkHttp interceptor or a URLSession delegate can drive it as-is without one signature change. The work here is mostly *moving files and drawing module boundaries around a seam that is already cut*, not a redesign.

Intended outcome: ship the split plus OkHttp and URLSession integrations without breaking a single existing consumer, and leave a documented, de-risked path to React Native.

## Is this a good idea?

Graded, because the answer differs sharply by piece.

| Piece | Verdict | Why |
|---|---|---|
| Split core / ui / ktor | **Yes, clearly** | Low risk, immediate payoff, prerequisite for everything else. The seam is already cut. |
| OkHttp module | **Yes** | Largest Android audience; also gets React Native's Android half for free. |
| URLSession module | **Yes** | One integration covers Alamofire, Moya, Get, raw URLSession *and* React Native's iOS half. |
| React Native npm package | **Worth it, but it is the expensive one** | 3–5× the effort of everything above, and the riskiest surface. See risks. |
| Flutter | **Do not bind the Kotlin core** | Marshalling every request body over a platform channel to reach a Kotlin store is a bad trade against a Dart-native implementation. Share the HAR format, not the core. |
| Web / JS | **Skip** | Browser devtools already do this better. |

On the "Kotlin core + wrappers for other platforms" idea specifically: **yes, but only down to the JVM/Native boundary.** Android, JVM, iOS, Kotlin Multiplatform and React Native can all consume a Kotlin core through real language interop — OkHttp is JVM, URLSession is ObjC, and React Native's own networking is literally those two. Dart and JavaScript cannot; reaching them means marshalling every captured body across a channel, which is a worse deal than reimplementing capture natively and sharing only the format. So the wrapper strategy holds for the platforms in scope and stops cleanly where it stops being a good trade.

The thing that justifies the whole exercise is **request/response overriding**. Traffic *viewing* is commoditised — Chrome DevTools, Charles, Proxyman, Pulse, Chucker, and RN's own upcoming network panel all do it. On-device, cross-platform, no-proxy *mocking and overriding* is not commoditised. Every architectural decision below should be read as "does this keep the override engine shared and portable?", because that is the differentiator.

## Target module graph

Six modules, not more. Every one earns its place by being a dependency set someone genuinely wants without the others.

```
                      :inspektor-core                     (no Ktor, no Compose)
             ___________/   |   \___________________
            /               |                \       \
    :inspektor-ui   :inspektor-ktor   :inspektor-okhttp   :inspektor-urlsession
            \______     ____/                (android, jvm)      (ios)
                   \   /
                :inspektor        umbrella — unchanged coordinates & public API
```

| Gradle module | Artifact | Targets | Holds |
|---|---|---|---|
| `:inspektor-core` | `inspektor-core` | android, jvm, iosArm64, iosSimulatorArm64 | `data/` (`.sq`, data source, adapters, `Override`, `OverrideRepository`), `TransactionRecorder` (ex-`HttpClientCallLogger`), `RetentionManager`, `har/`, `platform/`, neutral config + sanitisers, override **matching** |
| `:inspektor-ui` | `inspektor-ui` | same | all 43 `ui/` files, `openInspektor()`, `MainActivity`, `InspektorFileProvider`, compose resources, `NotificationManager` |
| `:inspektor-ktor` | `inspektor-ktor` | same | `Inspektor` plugin, `KtorHooks`, `ObservingUtils`, `LoggedContent`, `KtorUtils`, Ktor-typed `filter` |
| `:inspektor-okhttp` | `inspektor-okhttp` | android, jvm | `okhttp3.Interceptor` implementation |
| `:inspektor-urlsession` | `inspektor-urlsession` | iosArm64, iosSimulatorArm64 | `URLSessionProxyDelegate` + optional `URLProtocol` |
| `:inspektor` | `inspektor` *(existing)* | same as today | **no source** — `api(core) + api(ui) + api(ktor)` |

**Rejected:** a separate `:inspektor-platform` (the `platform/` package is 41 lines of `expect` and belongs with its only consumer, core); a separate `:inspektor-har` (191 lines, one consumer); splitting storage from model (the UI is written directly against SQLDelight-generated types — see friction below — so a storage/model split is a much bigger refactor for no current benefit); an `:inspektor-alamofire` (URLSession covers it).

**No-op strategy.** A no-op twin is needed per *integration entry point*, because consumer code names those symbols. This is exactly Chucker's `chucker-library-no-op` pattern. `:inspektor-no-op` keeps its current coordinates and meaning (Ktor + `openInspektor()` + `setApplicationId()`), and `:inspektor-okhttp-no-op` / `:inspektor-urlsession-no-op` follow when those ship. Core and UI need no twins — nothing references them by name. This is the single biggest ongoing tax of the split and it should be stated plainly in the README.

## Core API the integrations code against

Three pieces, in order of importance.

**1. `TransactionRecorder`** — promote `HttpClientCallLogger` to public, renamed, in `:inspektor-core`. Its signature already works unchanged; the constraint is that it **must stay non-suspending and fire-and-forget**, because it is called from blocking OkHttp interceptor threads and from Objective-C callback queues, not just coroutines. The existing `GlobalScope.launch(ioDispatcher)` + atomicfu CAS + `Job`-join design (which guarantees the INSERT lands before the UPDATE regardless of caller ordering) is exactly right for that and should be kept. Only two edits: drop the hardcoded `"Recording Ktor Activity"` notification string, and expose a `RecorderFactory` so integrations obtain one without reaching a singleton.

**2. Neutral config and filtering.** Core gets `InspektorCoreConfig` with `level`, `maxContentLength`, `retentionDuration`, `showNotifications`, `sanitizeHeader` (already neutral — it is a `(String) -> Boolean` on the header name) and a filter over a neutral `InspektorRequest` view (method, url, host, path, headers). `:inspektor-ktor` keeps its existing `filter((HttpRequestBuilder) -> Boolean)` as an *additional* overload delegating to the neutral one, so existing consumer source keeps compiling. Sanitisation must be applied inside core, on the recorder's side of the boundary — not re-implemented per integration, where one of them will eventually forget.

**3. Override engine.** `Matcher.matches` generalises to `(method, url, host, path)` — the current Ktor implementations already only read `request.url.toString()`, `.host` and `.encodedPath`. So **matching is shared in core; applying is per-integration**, unavoidably, since swapping a body means constructing an `OutgoingContent` / `RequestBody` / `NSURLRequest` respectively. Core exposes `OverrideEngine.findRequestOverride(...)` / `findResponseOverride(...)` returning an `OverrideAction`; each integration applies it. Note the engine must stay **synchronously readable** — `Inspektor.kt` reads `overrideRepository.all` inside hooks today, which is why `OverrideRepositoryImpl` keeps a warm `StateFlow` cache.

This forces `Override`, `Matcher`, `OverrideAction`, `HttpMethod` and `HttpTransaction` — all `internal` today — to become public. That is the real cost: they stop being free to change. Mitigate by publishing them behind `@UnstableInspektorAPI` initially, and by removing `com.gyanoba.inspektor.data` from `apiValidation.ignoredPackages` so they are actually tracked (today that exclusion also silently hides `setApplicationId`, which is documented as a required consumer call).

## Migration sequence

Ordered so that each step is independently verifiable and nothing is broken in between. Steps 0–3 are the split and are pure refactoring — no behaviour change, no new public API, releasable at any point. Steps 4–5 add capability and each is independently shippable.

Every new module also needs the boilerplate the two existing ones have: a `<module>/gradle.properties` with `POM_ARTIFACT_ID`, the vanniktech publish plugin, `explicitApiWarning()`, an `api/` dump directory, and an entry in `settings.gradle.kts`. Easy to forget, and a missing `POM_ARTIFACT_ID` silently publishes under the wrong coordinates.

**Step 0 — clean up first, separately.** These are pre-existing and will otherwise tangle with the split: drop unused `paging-compose` / `androidx-paging3-extensions` deps (no references in `ui/`); move `ktor-client-logging` from `commonMain` to `commonTest` (only `ResponseDeserializationTest.kt` uses it); drop `ktor-client-okhttp` / `ktor-client-darwin` from the library's platform source sets (a library should not force engines on consumers); delete dead `ReceiveAfterHook` in `KtorHooks.kt`; fix `apiValidation`'s `ComposableSingletons$*Kt` filter, which does not currently match — 15 of 22 dumped classes are lambda-cache noise. Commit and `apiDump` here so the baseline is clean.

**Step 1 — extract `:inspektor-core`.** Move `data/`, `platform/`, `har/`, `RetentionManager.kt`, `HttpClientLogger.kt`, `utils/{Curl,DateTimeUtils,InternalLogger}.kt`, and the `.sq` + migrations. Package names do not change. Three mechanical problems:
- `har/Har.kt` line 4 imports `io.ktor.http.Url`, used once at line 147 for query-string parsing. Replace with a small hand-rolled splitter so core has no Ktor.
- `utils/HeaderSanitizer.kt` operates on Ktor `Headers`. Reshape to `Map<String, List<String>>`; `:inspektor-ktor` keeps a thin `Headers` adapter.
- `TestBase.kt` duplicates the `HttpTransaction.Adapter` wiring from `Db.kt`. Deduplicate while moving, and split the test fakes (`NoOpDataSource`, `KStoreInMemoryCodec`) into a fixtures source set both sides can use. `CurlTest.kt` and `RetentionManagerTest.kt` (220 lines) move to core unchanged — they are already headless.

**Step 2 — extract `:inspektor-ui`.** Move all 43 `ui/` files, `openInspektor()` and its three actuals, `MainActivity`, `InspektorFileProvider`, the Android manifest entries, `res/`, and `composeResources/`. Four mechanical problems:
- **`NotificationManager` moves to `:inspektor-ui`, not core.** Its Android actual targets `MainActivity` in a `PendingIntent` and its JVM actual loads `drawable/bell.png` through `composeDesktopResourcesPath()`. Both are UI concerns. Core keeps the `interface NotificationManager` and takes it as an optional injected dependency; the umbrella wires the real one. A headless core consumer then correctly gets no notifications, since tapping one opens a UI that is not there.
- **`ContextInitializer` (androidx.startup) is needed by six files across storage, platform and the UI entry point.** It must live in core (storage needs it first) and be re-exported; the manifest `<meta-data>` entry moves with it, while `MainActivity` and `InspektorFileProvider` entries go to the UI manifest.
- The four Composables that reach `InspektorDataSourceImpl.Instance` / `OverrideRepositoryImpl.Instance` directly (`TransactionListScreen.kt:73`, `TransactionDetailScreen.kt:56`, `OverridesListScreen.kt:45`, `EditOverrideScreen.kt:54-55`) still compile via core, so this step does **not** require fixing the DI. Leave it; it is a separate concern.
- `getAppDataDir()` is called when `OverrideRepositoryImpl.Instance` is constructed and throws on JVM unless `setApplicationId()` ran first. Unchanged by the split, but it will now fail from a new place, so keep the error message explicit.

**Step 3 — extract `:inspektor-ktor` and make `:inspektor` an umbrella.** The remaining Ktor files move; `:inspektor` keeps its coordinates and becomes source-free with `api(core) + api(ui) + api(ktor)`. `api()` (not `implementation()`, not `typealias`) is what preserves the transitive compile classpath. One iOS gotcha: a source-free KMP module produces an **empty framework**, so the umbrella's `binaries.framework` block needs explicit `export(project(":inspektor-core"))` etc. for anything to appear in the generated ObjC headers. Gradle/KMP consumers are unaffected; only direct Swift consumers of the framework hit this.

**Step 4 — `:inspektor-okhttp`.** New KMP module with `androidTarget()` + `jvm()` (not a plain Java library — it should share one source set and match the rest of the build). Add `okhttp`, `okio` and `mockwebserver` to `libs.versions.toml`; none are there today, only `ktor-client-okhttp`.
- **Expose both application and network interceptor modes, defaulting to application.** Application interceptors fire exactly once and see what the app wrote, but report cache hits as network calls and miss redirect hops. Network interceptors see real wire traffic including gzip and each redirect, but never fire on a cache hit. Chucker exposes both; copy that.
- Request body: `RequestBody.writeTo(Buffer())` — non-destructive for ordinary bodies. Guard `isDuplex()` and `isOneShot()` and skip capture for those rather than corrupting the consumer's request.
- Response body: `response.peekBody(maxContentLength)`, which exists precisely for this and does not consume the real body. This is simpler and safer than the Ktor `OutgoingContent.observe` tee in `utils/ObservingUtils.kt`.
- Populate `responseTlsVersion` / `responseCipherSuite` from `chain.connection()?.handshake()`. **These two schema columns exist today and are never filled** — OkHttp is the first integration that can.
- Overrides apply by returning a synthesised `Response` (fixed response) or by rebuilding the `Request` before `chain.proceed()` (fixed request).

**Step 5 — `:inspektor-urlsession`.** Kotlin/Native, `iosArm64` + `iosSimulatorArm64`, in its own artifact rather than core's `appleMain` so that non-iOS consumers and the umbrella do not carry it.
- **Ship the proxy delegate as the primary path and `URLProtocol` as an opt-in.** The delegate captures faithfully but requires the consumer to pass it when constructing their `URLSession`. `URLProtocol` is zero-config but must be registered before any request or those are invisible, cannot see `AVPlayer` traffic or background sessions, and is a global mutable registration that perturbs caching. Document both, recommend the delegate.
- Body capture: accumulate `didReceive data:` for responses; read `HTTPBody` for requests and accept that `HTTPBodyStream` uploads cannot be captured without consuming them.
- **Keep the exported surface ObjC-bridge-friendly**: no `suspend` functions (they become completion handlers), no generics, no default arguments, no sealed classes on the boundary. The recorder's non-suspending design already satisfies this. Swift export would make this much nicer but is Alpha as of Kotlin 2.2.20 — do not build on it yet.
- This step also forces XCFramework/SPM distribution, which does not exist today (the build produces `framework { baseName = "inspektor" }` and publishes nothing to SPM or CocoaPods). That work is on the critical path for React Native later.

## Verification

**The API must not change.** This is the one non-negotiable check, and a naive `apiCheck` will *not* prove it, for two reasons discovered while planning:
- Compose lambda-cache classes are named `getLambda$…$inspektor` — the suffix is the module name. Moving the UI renames all 15 of them into `:inspektor-ui`'s dump. They are noise, not API.
- `InspektorConfig` carries `public static final field $stable I`, injected by the Compose compiler plugin. Once it lives in a Compose-free `:inspektor-ktor`, that field disappears from the ABI. Real-world impact is nil (only Compose-generated code reads `$stable`, and consumers regenerate it), but it *will* show as a diff.

So the check is: capture the current dump, then after the split compare the **union of the new modules' dumps against it, filtering `ComposableSingletons` and `$stable`**:
```bash
./gradlew apiDump                     # before Step 1, on the cleaned-up baseline
git stash && cat inspektor/api/jvm/inspektor.api | grep -v 'ComposableSingletons\|\$stable' | sort > /tmp/before.txt
# after the split:
./gradlew apiDump
cat inspektor*/api/jvm/*.api | grep -v 'ComposableSingletons\|\$stable' | sort > /tmp/after.txt
diff /tmp/before.txt /tmp/after.txt   # must be empty
```

**Behaviour.** Existing tests must pass unmoved: `./gradlew :inspektor-core:jvmTest :inspektor-ktor:jvmTest :inspektor-no-op:jvmTest`. The seven `TestBase`/MockEngine tests move to `:inspektor-ktor`; `CurlTest` and `RetentionManagerTest` to core.

**OkHttp.** New tests against `MockWebServer`, covering both interceptor modes, a gzipped response, a redirect chain, a cache hit, a one-shot body, and TLS metadata capture.

**iOS.** CI runs JVM tests only today. Add `./gradlew :inspektor-urlsession:iosSimulatorArm64Test` to the existing macOS runner — this is the first iOS test target in the repo and will need the `-lsqlite3` linker flag that `linkSqlite()` currently defines but never calls (`inspektor/build.gradle.kts:150`).

**End to end.** Extend `:sample` to install all three integrations behind a switch, so `./gradlew :sample:assembleDebug` (which already builds both `dev` and `prod` flavors, catching API drift between real and no-op) also catches integration drift. The existing `dependencySubstitution` trick in `sample/build.gradle.kts:129-135` extends to the new no-op twins unchanged.

## Risks and downsides

### Structural / maintenance

1. **Module count multiplies the maintenance surface for a solo maintainer.** Each new module wants its own `api/*.api` dump, its own POM properties file, its own README section, its own CI job. Three modules becoming eight is not 2.6× the work but it is not 1× either. The no-op story is the sharpest version of this: naively, every integration module needs a no-op twin, doubling the count again.

2. **Binary compatibility surface grows.** Today the real public API is 7 classes and ~25 members, and one file (`Inspektor.kt`) owns almost all of it. Making `HttpTransaction`, `Override`, `Matcher` and a `TransactionRecorder` public — which integrations outside the repo need — turns internal data classes into a frozen contract. `binary-compatibility-validator` will then block changes that are currently free.

3. **Version skew between artifacts.** A consumer on `inspektor-core:0.6.0` + `inspektor-okhttp:0.5.0` will fail confusingly. Needs a BOM or a strict version constraint from day one.

4. **Moving classes between modules is a breaking change unless the umbrella is exact.** Keeping `:inspektor` as an aggregator solves this, but only if every public declaration still reaches consumers transitively. The dump will *not* be byte-identical (see Verification — Compose lambda names carry the module name, and `$stable` disappears), so the equivalence has to be checked deliberately rather than by a green `apiCheck`.

### Capture-correctness

5. **OkHttp interceptor placement is a genuine tradeoff, not a detail.** An application interceptor sees the request the app wrote and is invoked once, but misses redirects/retries and reports cache hits as if they were network calls. A network interceptor sees real wire traffic (including gzip and redirect hops) but never fires for a cache hit and can run multiple times per call. Chucker exposes both and lets the user choose; that is probably the right answer but it doubles the behaviour matrix to document and test.

6. **URLProtocol has hard limits.** It must be registered before any request is made or those requests are invisible. It does not see `AVPlayer` traffic (which bypasses the URL loading system), background sessions, or WebSockets. It is a global mutable registration that can change caching semantics. The `URLSessionProxyDelegate` alternative captures more faithfully but requires the consumer to actually pass the delegate in — a setup step, not zero-config.

7. **Body buffering is a memory and correctness risk on every integration.** `maxContentLength` defaults to 250 KB and the current code truncates, but duplex/streaming request bodies, one-shot bodies, multipart uploads and large downloads each need per-integration handling. Getting this wrong on OkHttp can break the consumer's actual network calls, not just the logs.

8. **Threading models differ per integration.** The recorder currently uses `GlobalScope.launch(Dispatchers.IO)` with atomicfu CAS guards and a `Job`-join to order INSERT before UPDATE. OkHttp interceptors are blocking and run on arbitrary pool threads; URLSession delegates run on their own queues; Kotlin/Native's memory model adds its own constraints. The recorder must stay non-suspending and fire-and-forget, and it must be safe to call from all of them.

9. **More capture surfaces means more chances to persist secrets.** Sanitisation (`sanitizeHeader`) must live in the core and be applied on the recorder's side of the boundary, not re-implemented per integration where one of them will forget.

### Platform / distribution

10. **Kotlin/Native → Swift interop friction is real.** Through the Objective-C bridge: `suspend` functions become completion handlers, generics collapse, default arguments vanish, sealed classes are awkward, and everything gets a module-name prefix. Swift export (direct, no ObjC bridge) is Alpha as of Kotlin 2.2.20 and JetBrains is targeting stable interop during 2026 — promising, but not something to build a release on today.

11. **XCFramework distribution is infrastructure you do not have yet.** The repo builds `framework { baseName = "inspektor" }` but publishes nothing to SPM or CocoaPods. Doing so means producing an XCFramework, zipping it, hosting it with a checksum (GitHub Releases), maintaining a `Package.swift` and/or a podspec, and versioning them in lockstep with Maven Central.

12. **Shipping Compose to a Swift-only or React Native iOS app is a heavy ask.** The UI is 59% of the source and drags Compose Multiplatform plus the Kotlin runtime into the consumer's binary. That is defensible for a debug-only tool but it is the single biggest objection an iOS team will raise.

### React Native specifically

13. **Two fragile native integration points.** Android needs `OkHttpClientProvider.setOkHttpClientFactory(...)` called before the networking module initialises — a global, order-sensitive hook that has broken before (RN once stopped honouring custom clients, breaking Stetho and others). iOS needs URLProtocol registration against `RCTHTTPRequestHandler`'s session, with all the URLProtocol caveats above.

14. **RN version churn and the old/new architecture split.** A Turbo Module targets the new architecture; the interop layer covers the old one, but supporting both across RN minor versions is ongoing tax, plus an Expo config plugin for managed workflows.

15. **A first-party competitor is coming.** RN DevTools (stable since 0.76) does not yet ship network inspection, but first-party support is explicitly in progress in the new C++ debugger stack, and Expo already exposes an unstable Network panel. By the time an npm package ships, plain traffic viewing may be built in. The differentiator has to be persistence, overriding, HAR export and the on-device UI — not "you can see requests".

16. **A JS-layer alternative undercuts the native approach for most users.** Patching `global.XMLHttpRequest` catches everything `fetch`/axios do with zero native code and no XCFramework. It misses native-module traffic and image loading, but it is dramatically cheaper. If the native path stalls, this is the fallback — and it is worth being honest that many RN users would be satisfied by it.

### Process

17. **The testing and CI matrix balloons.** CI today runs `:inspektor:jvmTest` and `:inspektor-no-op:jvmTest` on JVM only; instrumentation tests are disabled (`if: false`) and there are no iOS tests and no UI tests. Adding OkHttp (MockWebServer), iOS simulator tests, and eventually RN e2e means real macOS-runner minutes and real flakiness.

18. **Pre-existing debt gets multiplied by the split.** The DI is a global `IsTest` flag with a `// TODO: inject these dependencies` comment; four Composables reach hardcoded `*.Instance` singletons; `apiValidation`'s `ComposableSingletons$*Kt` filter does not actually match so 15 of 22 dumped classes are noise; `paging-compose` and `ktor-client-logging` appear unused; `TestBase` duplicates the column-adapter wiring. Splitting modules forces most of these into the open. That is a benefit, but it is unplanned work that will surface mid-migration.

## Longer-term direction (not scheduled here)

Documented so the module boundaries drawn now do not foreclose it.

**React Native.** Falls out of the two native integrations more than it looks: RN's Android networking *is* OkHttp, and its iOS networking *is* NSURLSession. The npm package is a Turbo Module wrapping `inspektor-okhttp` and `inspektor-urlsession`, plus `openInspektor()`, plus an Expo config plugin. The chosen viewer strategy is to reuse the existing native UI — no new screens — which means the RN iOS side must link an XCFramework. Item 11 above is therefore on the critical path for React Native, not optional polish.

**Flutter.** Recommend *not* binding the Kotlin core. Dart interception (Dio interceptors, an `http` `BaseClient` wrapper) is trivial, but every captured body would then cross a platform channel to reach a Kotlin store, and Dart already has Alice and Talker. If Flutter is ever served, share the **HAR format and a viewer**, not the core.

**A remote viewer.** Worth flagging as the strategic fork: as platform count grows, "one shared Compose UI" stops paying off, because Flutter, web and RN-without-XCFramework each want a different host. A device→desktop/browser stream over WebSocket (Flipper / Pulse Pro shape) scales to all of them and sidesteps shipping Compose everywhere. It is a whole second deliverable, so it is not scheduled — but if React Native's XCFramework requirement proves too painful in practice, this is the escape hatch, and the core API should not assume the UI is in-process.
