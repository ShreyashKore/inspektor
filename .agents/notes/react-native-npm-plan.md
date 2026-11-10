# Inspektor for React Native: npm package plan

> Written 2026-10-20 on `feature/remote-inspection`. The facts in §1 come from reading React Native
> 0.84.1's own sources and Inspektor's code. Items marked *spike* still need
> checking on a real build; §8 lists them.
>
> **This is milestone M3 of [cross-platform-roadmap.md](cross-platform-roadmap.md).** Native Android
> (M1) and native iOS (M2) come first, and they deliver this plan's native prerequisites: the
> umbrella XCFramework flavours, the iOS fixes and the release pipeline. The roadmap's M0 also
> provides two things this plan uses: the conformance kit and the shared release audit
> (`scripts/verify-release.py`).

## TL;DR

- **One npm package, `@gyanoba/inspektor-react-native`**, kept in this repo under `react-native/`
  and versioned in lockstep with the Maven artifacts. It is a thin layer: a TypeScript API, a
  Kotlin TurboModule over `inspektor-okhttp`, and an Objective-C++ TurboModule over a Kotlin/Native
  XCFramework.
- **Capture starts natively, before React Native makes its first request.** No JS call is needed
  to record traffic. JS only configures, opens the UI and starts the remote server.
- **Production builds carry no Inspektor code.** Removal happens in three layers, each closed by
  default:
  - **iOS:** the pod is linked only into the configurations you list.
  - **Android:** the dependency is added only to the variants you list, and a small Gradle plugin
    fills two gaps in React Native's autolinking.
  - **JS:** a Metro helper swaps the package for a no-op file when bundling a release.

  A CI audit then fails the build if any Inspektor class, library, symbol or string survives in
  a release binary. The same audit ships as a CLI, so apps can run it too.
- **The in-app UI is Inspektor's existing Compose UI.** It is on by default and can be turned off,
  for teams that only use Eagly and want lighter debug builds. JS opens it with
  `Inspektor.open()`, or through an optional floating launcher.
- **Native binaries do not ship inside the npm tarball.**
  - Android: the AARs come from Maven Central, as today.
  - iOS: the XCFramework is downloaded at `pod install` from GitHub Releases, against a SHA-256
    pinned in the package. This is the pattern `hermes-engine` uses. CocoaPods trunk is not an
    option, because it goes read-only on 2 December 2026.

## 0. What "gone from production" means

The requirement is that a production build contains nothing of Inspektor. To make that testable, a
release binary built from a production configuration must have:

- no classes in `com.gyanoba.inspektor.*`, no Kotlin/Native runtime from Inspektor, and no
  Inspektor `.so`, `.framework` or `.a`;
- no manifest entries (`Activity`, `provider`, `androidx.startup` initializer, permissions) and no
  Info.plist keys;
- no Inspektor JS module in the bundle;
- no listening socket, no database file and no notification channel at runtime.

§4.5 turns this list into an automated audit. Anything the audit cannot check doesn't count as
removed.

## 1. Facts the plan rests on

| Fact | Where | Consequence |
|---|---|---|
| RN's Gradle plugin turns a dependency's `buildTypes` into `<buildType>Implementation`, and takes variant names too (`devRelease`). | `ReactExtension.kt` 195–216 | Android variant scoping of the *Gradle dependency* works out of the box. |
| `PackageList.java` is generated once, into `src/main`, for every variant. It lists every Android dependency **whatever its `buildTypes`**, by fully qualified class name. | `GeneratePackageListTask.kt` (`filterAndroidPackages`, `composePackageInstance`) | With `buildTypes: ['debug']` alone, a release build references a class that isn't on its classpath, so it should fail to compile (*spike S1*). |
| The C++ autolinking files (`Android-autolinking.cmake`, `autolinking.cpp`) are also shared across variants. They `add_subdirectory` each library's codegen JNI and call `<lib>_ModuleProvider`. | `GenerateAutolinkingNewArchitecturesFileTask.kt` | The codegen glue of a TurboModule is compiled into every variant's `libappmodules.so` unless the library's CMake gates it. |
| iOS autolinking passes the `configurations` key to `pod … :configurations`. | `react-native/scripts/cocoapods/autolinking.rb` 57, 167 | iOS configuration scoping works out of the box. |
| `RCTModuleProviders.mm` maps module names to class *names* and resolves them with `NSClassFromString`. A missing class logs `RCTLogError` and is skipped. | a 0.84 app's generated `RCTModuleProviders.mm` | Declaring `ios.modulesProvider` leaves two strings, and an error log, in production. Avoid it if possible (*spike S3*). |
| An empty `configurations` array (CocoaPods) and an empty `buildTypes` array (RN) both mean **all** builds. | CocoaPods; `ReactExtension.kt` 206 | Defaults must never be empty. The tooling rejects an empty list. |
| Android: `NetworkingModule` builds its client from `OkHttpClientProvider.createClient`, then applies the one static `setCustomClientBuilder` slot. Fresco images use `OkHttpClientProvider.createClient()` (`FrescoModule.kt` 156). WebSockets have their own slot. | RN 0.84 sources | Hook the custom-builder slot, and chain whatever was there before. `react-native-ssl-public-key-pinning` already chains it by reflection. |
| iOS: `RCTSetCustomNSURLSessionConfigurationProvider` scopes capture to RN's own session. | a hand-written integration in a React Native app | Already proven on an iPhone 16. Costs TLS pinning (see §3.2). |
| `InspektorUrlSession.xcframework` (core + URLSession, no UI) is 24 MB for two slices. The UI adds Compose Multiplatform and Skiko. | local build | Too big for an npm tarball, and far too big with the UI. |
| Two Kotlin/Native frameworks in one app means two runtimes and two copies of every singleton, the database included. | Kotlin/Native | Everything iOS needs must be **one** framework. "UI or no UI" is two flavours of that framework, not two frameworks. |
| RN 0.82 made the New Architecture the only runtime. 0.84 removes legacy classes release by release, and `RCT_REMOVE_LEGACY_ARCH` is now the iOS default. The interop layer stays "for the foreseeable future". The latest RN is 0.88.0. | RN blog; `npm view` | Build proper codegen TurboModules. Use the interop layer only as a fallback. |
| `react-native-inspektor`, `@gyanoba/inspektor-react-native` and `@inspektor/react-native` are all unclaimed on npm. | `npm view` | The name is open (see §9). |

## 2. Package shape

```
react-native/                          # in the Inspektor repo
  package.json                         # name, exports, codegenConfig, peerDeps, files
  react-native.config.js               # safe defaults: ios ['Debug'], android ['debug']
  app.plugin.js                        # Expo config plugin (phase 5)
  src/
    index.ts                           # the real API
    noop.ts                            # same API, empty; `satisfies typeof import('./index')`
    NativeInspektor.ts                 # codegen spec; TurboModuleRegistry.get (never getEnforcing)
    ui/InspektorLauncher.tsx           # optional floating button
  metro/index.js                       # withInspektor(config, { enabled })
  cli/verify-release.js                # `npx @gyanoba/inspektor-react-native verify-release <apk|aab|ipa|app>`
  android/
    build.gradle                       # depends on com.gyanoba.inspektor:inspektor-okhttp (+ inspektor-ui)
    src/main/…/InspektorModule.kt, InspektorPackage.kt, InspektorStartup.kt
    jni/CMakeLists.txt                 # gates the codegen glue per variant (§4.3)
    gradle-plugin/                     # com.gyanoba.inspektor.react-native (§4.3)
  ios/
    InspektorReactNative.podspec       # downloads + verifies the XCFramework
    InspektorModule.mm, generated/     # codegen output shipped (includesGeneratedCode)
  example/                             # Debug / Release-dev / Release-prod; debug / dev / release
```

- **Scaffold it with `create-react-native-library`** (react-native-builder-bob), using a Kotlin and
  Objective-C TurboModule. That gives the codegen wiring, the example app and the ESM and type
  build.
- **`package.json`:**
  - `exports` with `types`, `react-native` and `default` conditions, plus `./noop`, `./ui` and
    `./metro`;
  - `sideEffects: false`;
  - `files` as an allowlist, so the example and tests stay out;
  - `codegenConfig` with `includesGeneratedCode: true`, so the iOS spec glue lives in our pod and
    leaves with it;
  - `peerDependencies`: `react-native >=0.81`, the floor to be confirmed by the CI matrix, and
    `react`;
  - **no `postinstall` or other install scripts.** pnpm, Yarn Berry and more and more company npm
    configs block them.
- **New Architecture only.** RN has nothing else since 0.82.

## 3. Native integration

### 3.1 Android

- **Module:** `InspektorModule`, a Kotlin TurboModule built from the `NativeInspektor` spec. It
  delegates to `inspektor-okhttp` and `inspektor-core`. `inspektor-ui` is added only when the UI is
  on (`inspektor.ui=true` in `gradle.properties`, the default).
- **Capture is installed before React Native starts,** by an `androidx.startup` initializer in the
  library manifest. That manifest only merges into enabled variants, because the dependency is
  only there.
  - It calls `NetworkingModule.setCustomClientBuilder` and **chains the previous builder**, the
    way `react-native-ssl-public-key-pinning` does. That keeps an app's own factory and its
    certificate pinning, and covers `fetch`, XHR and Axios.
  - Images: Fresco builds its client from `OkHttpClientProvider` and never sees the custom
    builder. Image capture is therefore a second, opt-in hook (`captureImages: true`). It sets
    `OkHttpClientProvider.setOkHttpClientFactory` and refuses to do so if the app already set one.
  - WebSockets: `WebSocketModule.setCustomClientBuilder`, once Inspektor records frames
    (milestone I-4).
- **Order against other libraries** (*spike S4*). Our initializer runs in `Application.onCreate`.
  The pinning library chains whatever it finds when JS initialises it, so both should apply. Check
  this in an app that uses that library.
- **Defaults before JS runs** come from `<meta-data>` that the Gradle plugin writes into the
  enabled variants' manifests: level, header sanitisation, notifications, and whether to start the
  remote server. JS can change them later.

### 3.2 iOS

- **One Kotlin/Native umbrella framework, `InspektorReactNative.xcframework`, in two flavours:**
  core + URLSession, and core + URLSession + UI. These are the roadmap's M2 frameworks, the same
  ones native Swift apps get. React Native adds its own umbrella only if it needs exports they
  lack.
  - The podspec picks the flavour at `pod install` from `$InspektorUI` in the Podfile, or from the
    `INSPEKTOR_UI` environment variable. That is the convention of `$RNFirebaseAsStaticFramework`.
  - It replaces the current `InspektorUrlSession` pod for React Native apps. Swift-only apps keep
    that pod.
- **Module:** `InspektorModule.mm`, an Objective-C++ TurboModule. The codegen output ships inside
  the pod. It registers itself rather than through `modulesProvider` (*spike S3*).
- **Capture before the first request:** a `+load` in the pod sets
  `RCTSetCustomNSURLSessionConfigurationProvider`, through
  `InspektorUrlProtocolBridge.prepare/addTo`. That is the hand-written Swift integration
  already proven in a React Native app, moved into the package. CocoaPods' default `-ObjC` flag keeps `+load` linked.
  - **Apps that set their own provider:** they call `[InspektorReactNative
    decorateConfiguration:]` from their provider, under `#if __has_include(<InspektorReactNative/…>)`.
    That keeps the call out of production builds automatically.
- **TLS pinning, fixed on Inspektor's side:** forward `URLAuthenticationChallenge`s from
  `InspektorUrlProtocol` to the client (`URLProtocolClient.urlProtocol(_:didReceive:)`). Until
  then, the README says that delegate-based pinning (TrustKit) is not enforced while capture runs.
- **Fix `openInspektor()` before relying on it.** It reads `UIApplication.keyWindow`, which is
  deprecated and `nil` in scene-based apps. It should present on the foreground scene's key window,
  over the top-most presented controller, on the main thread.
- **Ship a privacy manifest** (`PrivacyInfo.xcprivacy`) in the pod. It is required for SDKs, even
  debug-only ones, that call required-reason APIs (file timestamps, `UserDefaults`).
- **Binary delivery:** the podspec's Ruby downloads
  `InspektorReactNative[-UI]-<version>.xcframework.zip` from the GitHub Release.
  - It checks the SHA-256 written into the package at release time, and caches the result in
    `~/Library/Caches/Inspektor/<version>`.
  - `INSPEKTOR_XCFRAMEWORK_DIR` points at a local build instead. That is how a local checkout is used
    today, and what air-gapped CI needs.
  - The framework is vendored *inside* our pod, not declared as a dependency pod. So it is linked
    exactly where our pod is, with no question of how CocoaPods scopes transitive configurations.
  - Swift Package Manager comes later, once React Native supports SPM dependencies for libraries
    without the experimental `spm_dependency` helper.

## 4. Removing it from production builds

### 4.1 One list of builds, closed by default

The app names the builds that get Inspektor once, in its `react-native.config.js`. That is the file
React Native's autolinking already reads, on both platforms:

```js
// <app>/react-native.config.js
module.exports = {
  dependencies: {
    '@gyanoba/inspektor-react-native': {
      platforms: {
        ios: { configurations: ['Debug-dev', 'Release-dev', 'Debug-prod'] },
        android: { buildTypes: ['debug', 'dev'] },
      },
    },
  },
};
```

- **Without this file, the package's own defaults apply:** `ios: ['Debug']` and
  `android: ['debug']`.
- **An empty list is a build error,** raised by the Gradle plugin and by the podspec, because both
  tools read an empty list as "everything".
- **Every other build gets nothing.**

### 4.2 iOS

- **The pod is linked only into the listed configurations.**
  - Our module, the vendored XCFramework and its codegen glue are not linked into other
    configurations.
  - The pod's headers aren't on their search path either, so `__has_include` is false there.
- **No entry in `RCTModuleProviders`,** provided spike S3 confirms that self-registration finds the
  module on RN 0.84 and 0.88.
  - If it doesn't, we fall back to `modulesProvider`. Production then keeps two strings and an
    `RCTLogError` line at startup.
  - In that case we send a fix upstream that makes the lookup skip silently when the class is
    absent.

### 4.3 Android

React Native already adds the dependency only to the listed variants (§1). Two shared generated
files still name the module in every variant. A small Gradle plugin,
`com.gyanoba.inspektor.react-native`, closes both gaps. It ships in the npm package and is
included with `includeBuild` in `settings.gradle`, the way RN's own plugin is.

1. **`PackageList.java`.**
   - The library's `react-native.config.js` sets `packageImportPath` and `packageInstance` to
     `com.gyanoba.inspektor.reactnative.InspektorPackages.create()`.
   - The plugin generates that class into each variant's source set. An enabled variant gets
     `return new InspektorPackage();`. Any other variant gets a `ReactPackage` that returns
     empty lists.
   - R8 inlines the factory, which leaves one empty, obfuscated class that names nothing.
2. **C++ autolinking.**
   - The library's `cmakeListsPath` points at our own `jni/CMakeLists.txt`.
   - That file compiles the codegen glue only when `-DINSPEKTOR_ENABLED=ON`, which the plugin
     passes per variant through `androidComponents.onVariants`.
   - Any other variant compiles one `InspektorSpec_ModuleProvider` that returns `nullptr`. It is
     hidden and stripped in release.
3. **One list.** The plugin reads the variants from `build/generated/autolinking/autolinking.json`,
   the same `buildTypes`, so the app never writes the list twice.

**What a production Android build then keeps:** one empty class, and one C++ function that returns
`nullptr`. Neither has an Inspektor name or string. If spike S2 shows the CMake gating is fragile,
the fallback is to write the Android module as a legacy-style module that runs through the interop
layer. That needs no codegen glue, so nothing is left except the empty class. The cost is a bet on
the interop layer's lifetime.

**Better, upstream:** send a pull request to RN's Gradle plugin that makes `PackageList` and the
C++ autolinking respect `buildTypes`, generating them per variant. React Native's own docs say
`buildTypes` exists so that development-only libraries stay out of release builds, so this is a
bug fix rather than a feature. Once it lands, the plugin does nothing on those RN versions.

### 4.4 JS

- **Correct without the native side, always.** `NativeInspektor.ts` uses
  `TurboModuleRegistry.get`, and every API call is a no-op when that returns `null`. This matters
  for over-the-air updates such as EAS Update or CodePush, because a bundle and a
  binary can come from different builds.
- **Out of the bundle when it's not wanted.** `withInspektor(metroConfig, { enabled })` chains
  `resolver.resolveRequest` so that `@gyanoba/inspektor-react-native` and `/ui` resolve to `/noop`.
  - `enabled` defaults to `process.env.INSPEKTOR === '1'` for release bundles. *Spike S5:* whether
    the resolver context can tell a dev bundle apart, so dev bundles default to on.
  - Re.Pack and Babel users get a `resolve.alias` recipe.
- **`noop.ts` can't drift.** It is typed `satisfies typeof import('./index')`, and a type test
  breaks the build when the two APIs differ.
- **`__DEV__` is not the switch.** A Release-dev build has `__DEV__ === false` and still wants
  Inspektor.

### 4.5 The audit, which is what enforces all of this

These checks run in CI against the example app's production configurations: Android `release`
with R8, and iOS `Release-prod`. The implementation is the roadmap's shared
`scripts/verify-release.py`, which every client uses and which looks for obfuscation-proof
markers. The npm package wraps it as `npx @gyanoba/inspektor-react-native verify-release
<artifact>`, for apps' own CI, and adds the JS-bundle check.

- **Android:**
  - `apkanalyzer dex packages` must list no `com.gyanoba.inspektor`;
  - the APK must contain no Inspektor `.so` and no assets;
  - the merged manifest must contain no Inspektor activity, provider, initializer or permission;
  - `strings` over the dex and `.so` files must find no match for `inspektor`, case-insensitive.
- **iOS:**
  - `strings` and `nm` over the app binary must find no `Inspektor` and no Kotlin/Native `kfun:`
    symbols;
  - there must be no Inspektor framework in `Frameworks/`.
- **JS:** a release bundle built with `INSPEKTOR` unset must contain no `inspektor`.
- **Positive control:** the same checks run on Release-dev must *find* Inspektor. That proves the
  audit can see it.

## 5. JS API

```ts
import { Inspektor } from '@gyanoba/inspektor-react-native';

Inspektor.isAvailable;              // false when the native side isn't linked
Inspektor.configure({
  level: 'body',                    // 'none' | 'info' | 'headers' | 'body'
  maxContentLength: 250_000,
  retention: '1w',
  sanitizeHeaders: ['Authorization', 'Cookie', 'Set-Cookie'],  // the default set
  showNotifications: false,         // Android; Eagly users rarely want them
  ignore: [{ host: 'sentry.io' }],  // declarative: JS callbacks can't run on the network thread
});
Inspektor.open();                   // the in-app UI
Inspektor.remote.start();           // opt-in, as the protocol doc requires
Inspektor.remote.stop();
Inspektor.clear();
```

- **Filters are declarative** (host, path or method patterns). Calling a JS predicate from
  OkHttp's or URLSession's thread for every request is not workable.
- **Later:** `exportHar(): Promise<string>`, and a change listener for a request-count badge.

## 6. In-app UI

- **Reuse the Compose UI,** with no new screens. A JS reimplementation would duplicate about 4,800
  lines, the override editor included. Revisit that only if iOS UI weight proves unacceptable.
- **On by default; off with `inspektor.ui=false` (Gradle) or `$InspektorUI = false` (Podfile).**
  With it off, you get Eagly-only capture and a framework about a third the size.
- **Ways in:**
  - `Inspektor.open()`;
  - `<InspektorLauncher />` from `/ui`, a draggable bubble, which the Metro helper also strips;
  - a Dev Menu item, through `DevSettings.addMenuItem`, in `__DEV__` bundles only;
  - the Android notification, which Inspektor already has.
- **Android caveats:**
  - `inspektor-ui` brings Compose. In apps with their own Compose, Gradle resolution picks one
    version. That only affects debug variants, but say so in the README.
  - Android 13+ needs `POST_NOTIFICATIONS` for the notification. It is declared only in the UI
    module's manifest, so it only exists in enabled variants.

## 7. Repository, release and CI

- **Location:** `react-native/` in the Inspektor repo.
  - A single `v*.*.*` tag releases Maven, the XCFrameworks and npm together.
  - The npm version equals `VERSION_NAME`.
  - Native coordinates and checksums are generated into the package at release time, never
    edited by hand.
- **Release workflow,** extending `.github/workflows/publish.yaml`:
  1. Maven Central, as today (vanniktech).
  2. A macOS job that builds both XCFramework flavours, zips them, records their SHA-256 and
     attaches them to the GitHub Release.
  3. Wait until Maven Central serves the new version. Publishing can lag.
  4. `npm publish --provenance` through npm trusted publishing (GitHub OIDC). No long-lived npm
     token; 2FA on the npm org. Pre-releases go to the `next` dist-tag.
- **CI matrix:** the lowest supported RN, 0.84 and the latest (0.88), on Android
  and iOS, plus one Expo prebuild once phase 5 lands. Jobs:
  - `tsc`, ESLint, Jest, and the noop parity type test;
  - example in a debug configuration: a Maestro flow makes a request, opens the UI and finds it;
  - example in production configurations: the §4.5 audit;
  - optionally, Eagly's `network_inspektor_*_device_test.dart` against the example on a device.
- **Docs:**
  - a README quick start for bare RN and for Expo;
  - "Check your release build" (the CLI);
  - troubleshooting: pinning, custom OkHttp factories and URLSession providers, OTA updates;
  - migrating from a hand-written Swift or Kotlin integration.

## 8. Phases

**Phase 0: spikes, in scratch apps on RN 0.84 and 0.88.**

- **S1:** `buildTypes: ['debug']` alone. Does `assembleRelease` fail at `PackageList.java`?
  Expected: yes.
- **S2:** the generated per-variant package factory plus CMake gating. Does release build, does R8
  leave only the empty class, and does debug still resolve the module?
- **S3:** iOS.
  - Is a TurboModule found without `modulesProvider` when `RCT_REMOVE_LEGACY_ARCH` is on?
  - Does `:configurations` keep the vendored XCFramework out of Release-prod?
  - Is `__has_include` false there?
- **S4:** the custom-builder chain against `react-native-ssl-public-key-pinning`, in both orders.
  Do Fresco images need the factory hook?
- **S5:** can a Metro `resolveRequest` tell a dev bundle from a release bundle?
- **S6:** the UI flavour of the XCFramework.
  - Its size and link time.
  - Does a Compose modal work over RN's root view controller?
  - Does it work next to a second Kotlin/Native framework from another vendor?

**Phase 1: Inspektor prerequisites, delivered by the roadmap's M0–M2.**

- from M0: `InspektorRemote` on `main`, the `adapter` column (`react-native`), the conformance kit
  and the release audit;
- from M2: the two XCFramework flavours, the `openInspektor()` scene fix, TLS challenge forwarding
  and the privacy manifest.
- **Still this plan's own work:** a `:inspektor-react-native-ios` umbrella, if the M2 frameworks
  need React Native-specific exports.

**Phase 2: the package.**

- the bob scaffold, the JS API and `noop`, and the Metro helper;
- the Android module, startup initializer and Gradle plugin;
- the iOS module, podspec and downloader;
- an example app with dev and prod configurations.

**Phase 3: enforcement.** The `verify-release` CLI, and the CI audit with its positive control.

**Phase 4: UI entry points.** The launcher, the Dev Menu item, and the defaults written into
manifest and Info.plist.

**Phase 5: Expo config plugin.**

- It adds the Gradle plugin and `$InspektorUI`.
- It maps EAS build profiles to configurations and variants.
- Expo apps usually have only Debug and Release. The plugin must therefore exclude the module
  from production *profiles* at prebuild time, not only by configuration. The design is to be
  confirmed in a spike.

**Phase 6: release.**

- `0.1.0` on `next`.
- Move a real app from a hand-written integration onto the package, and run the audit on its
  production IPA and release AAB.
- Then `1.0.0`.

**Phase 7: upstream.** The RN Gradle plugin pull request from §4.3, plus the `RCTModuleProviders`
one if S3 needs it.

## 9. Risks and open questions

**Risks:**

- **Autolinking changes between RN minors.** Our seams are `packageInstance`, `cmakeListsPath` and
  `configurations`. The latest-RN CI job is the alarm.
- **Kotlin version conflicts.** Android apps with their own KMP libraries may resolve a different
  Kotlin or Compose. Two Kotlin/Native frameworks on iOS duplicate the runtime (S6).
- **XCFramework download availability.** Mitigated by the cache and `INSPEKTOR_XCFRAMEWORK_DIR`.
- **URLProtocol limits on iOS.** Pinning is fixed in phase 1. Stream-bodied uploads and
  background sessions still need checking.
- **An unauthenticated socket in Release-dev builds.** It is opt-in (`remote.start()`), on
  loopback or an abstract socket only, and the README says so plainly.
- **CocoaPods' sunset.** The pod is local, from npm, and only fetches a zip, so trunk going
  read-only doesn't block us. SPM is the long-term path.

**Decisions for you:**

1. **Package name:** `@gyanoba/inspektor-react-native` (matches the Maven group) or
   `react-native-inspektor` (easier to find). All three candidates are free.
2. **UI default:** on (proposed) or off.
3. **Minimum React Native:** 0.81 is proposed; the matrix may move it.
4. **Android residue:** is §4.3's leftover acceptable (one empty, nameless class and one `nullptr`
   function)? Or must Android be byte-zero, which means taking the interop-layer fallback until
   the upstream fix lands?
