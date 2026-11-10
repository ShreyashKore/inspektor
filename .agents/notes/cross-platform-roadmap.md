# Inspektor across platforms: roadmap

> Written 2026-10-20 on `feature/remote-inspection`. This note sets the order of work for every
> Inspektor client. Per-client detail lives in its own plan:
> - [react-native-npm-plan.md](react-native-npm-plan.md): React Native;
> - [multi-client-modularization-plan.md](multi-client-modularization-plan.md), on branch
>   `refactor-into-smaller-modules`: the module split;
> - [docs/REMOTE_PROTOCOL.md](../../docs/REMOTE_PROTOCOL.md): the wire protocol.

## Goal

One HTTP inspector for six kinds of consumer:

| Consumer | How it captures | Where the code lives |
|---|---|---|
| Kotlin Multiplatform | the Ktor plugin | `:inspektor-ktor` |
| Native Android | an OkHttp interceptor | `:inspektor-okhttp` |
| Native iOS (Swift) | a URLSession `URLProtocol` | `:inspektor-urlsession`, shipped as an XCFramework |
| React Native | the two native ones above, plus glue | an npm package |
| Flutter | a Dart reimplementation | a pub.dev package |
| Eagly | reads any of the above over the remote protocol | the Eagly repo |

The Kotlin core serves the first four. Flutter does not bind it: it shares the protocol, the
transaction format and the override rules, not the code (see the modularization plan).

## Order

```
M0 shared contract ──┬─► M1 Kotlin targets (Android, KMP) ─► M2 native iOS ─► M3 React Native
                     └─► F  Flutter, in parallel ──────────────────────────────────────────────►
Eagly follows each milestone; E items below.
```

- **Native comes first because React Native has no capture code of its own.** It is OkHttp on
  Android and URLSession on iOS. Bugs found through React Native are slow to trace through its
  layers; the iOS `NSLog` crash was found that way, in a React Native app. Every iOS item the RN plan lists as
  a prerequisite is native iOS work.
- **Android before iOS** because it is nearly done. That gives a released, Eagly-compatible client
  quickly, and the release pipeline gets exercised on the easy case first.
- **Flutter runs in parallel** because it shares nothing but the contract. Starting it before M0 is
  frozen would mean reimplementing a moving target.
- **A React Native app can wire Inspektor in by hand until M3:** a short Swift file that installs
  the URLSession capture. Such an app is effectively a native-iOS consumer, so it is a good field
  test for M2.

## Where each target stands (2026-10-20)

| Target | Published | Missing |
|---|---|---|
| KMP / Ktor | `inspektor`, `inspektor-ktor` 1.0.1 | the remote server (unmerged) |
| Native Android | `inspektor-okhttp` + no-op 1.0.1 | the remote server (unmerged) |
| Native iOS | `inspektor-urlsession` 1.0.1, but only as a Kotlin klib, which Swift can't consume | a binary distribution, prod removal, three fixes (M2) |
| React Native | nothing; each app wires it in by hand | the package |
| Flutter | nothing; `inspektor` is free on pub.dev | everything |
| Eagly | reads Kotlin servers on Android and iOS | the conformance copy; `dart` runtime untested |

The remote server is 8 commits on `feature/remote-inspection`, ahead of `main`.

## M0: the shared contract

Everything every client must honour, written once and checked by tools, not by review.

1. **Merge `feature/remote-inspection` and freeze protocol v1.** After this, changes follow the
   spec's versioning rules: additive changes keep `protocol`, anything else bumps it.
2. **Record the adapter on every transaction now.** It needs a schema column (Eagly's
   NETWORK_INSPECTOR.md §5, item 4). Each adapter shipped before the migration is one more
   database the iOS migration has to carry forward safely. The values: `ktor`, `okhttp`,
   `urlsession`, `react-native`, `dart-io`, `dart-http`, `dio`.
3. **The conformance kit, in `conformance/`.**
   - **Golden transcripts:** JSON frames for the handshake, backfill then live events, body
     ranges, an override round trip, an unsupported protocol version, and the rejection of an
     oversized frame, a non-JSON frame and an HTTP request. These are the cases Eagly's plan §4.10
     lists, plus the three rejections that are the protocol's only safety.
   - **In-process use:** each server's own tests replay the transcripts. That is Kotlin `jvmTest`
     and `iosSimulatorArm64Test`, and Dart's `test`.
   - **Black-box runner:** `scripts/remote-client.py` grows a `conformance` mode. It serves a
     fixed set of HTTP endpoints itself, waits for the app under test to call them, then checks
     what the app's server reports against the transcripts. The test app only needs a dev build
     that calls a URL it is given at launch. On a device, `adb reverse` or usbmux brings that URL
     back to the runner. Standard library only, like today's script.
   - **Eagly** keeps a copy of the transcripts pinned to a protocol version, for its client tests.
     "Eagly-compatible" then means "passes the kit".
4. **The client contract,** a checklist in `docs/CLIENT_CONTRACT.md` that every client's README
   links to:
   - **Nothing in production.** A production build carries no Inspektor code; the audit below
     decides.
   - **Off-by-default remote.** The remote server starts only on an explicit call.
   - **Safe defaults.** `Authorization`, `Cookie`, `Set-Cookie` and `Proxy-Authorization` are
     sanitised unless the app says otherwise.
   - **Early capture.** It can start before the app's first request without app code ordering
     tricks (needed by React Native, useful everywhere).
   - **It composes.** It chains onto an existing OkHttp builder, URLSession configuration or
     `HttpOverrides`, rather than replacing it, so certificate pinning keeps working.
   - **A no-op twin** with an identical API, wherever the platform needs one to swap per build.
5. **One release audit for every client: `scripts/verify-release.py <apk|aab|ipa|app|so>`.**
   - It looks for markers that survive obfuscation: the socket name prefix `inspektor_`, the
     database name, the override store's file name, and a deliberate canary string that each SDK's
     start-up path references. Class names alone aren't enough, because R8 can rename packages.
   - Every client's CI runs it twice: on a production build, where it must find nothing, and on a
     dev build, where it must find the markers. The second run proves the first can see.
   - React Native's npm CLI wraps it; native and Flutter apps call it directly.

## M1: Kotlin targets (native Android and KMP)

Mostly release work, because the code exists.

- **Release 1.1.0** with the remote server. Check that `InspektorRemote` in the no-op modules
  still mirrors the real API (`apiDump`).
- **An OkHttp-only Android sample,** with no Ktor and no Compose, and a `prod` flavor using the
  no-op artifacts. It is the M0 audit's Android subject and the conformance kit's first runner
  target, on an emulator in CI.
- **A README section per consumer:** OkHttp without Ktor; KMP with Ktor; how to keep it out of
  release builds (the no-op artifacts, which Android already has).
- **The JVM desktop target** gets the conformance kit too. It needs no device, so it is the
  cheapest CI check of the Kotlin server.
- **Bake in what React Native will need from OkHttp:** `installInspektor` must be callable on a
  builder someone else created, without replacing their interceptors or certificate pinner.

## M2: native iOS

The biggest gap, and the one that unblocks React Native.

1. **Distribution, without CocoaPods trunk** (read-only from 2 December 2026):
   - `Package.swift` at the repo root with a `binaryTarget` pointing at the GitHub Release zip and
     its checksum. SPM requires the manifest at the root.
   - The existing `InspektorUrlSession.podspec`, consumed with `:podspec => <raw URL>` or `:git`
     and `:tag`, for CocoaPods users. It is never pushed to trunk.
   - Both are produced by the tag-triggered `publish.yaml`, next to Maven Central.
2. **Two framework flavours,** because two Kotlin/Native frameworks in one app means two runtimes
   and two databases. They are capture + core, and capture + core + UI. Every iOS consumer,
   React Native included, links exactly one of them.
3. **Fixes:**
   - **TLS challenges:** forward them from `InspektorUrlProtocol` to the client, so delegate-based
     pinning (TrustKit) still runs on captured requests.
   - **`openInspektor()`:** it reads `UIApplication.keyWindow`, which is `nil` in scene-based
     apps. Present over the foreground scene's top-most controller, on the main thread.
   - **A privacy manifest** in the framework, for the required-reason APIs it calls.
4. **Keeping it out of production on iOS.** Swift Package Manager can't limit a package to some
   build configurations, so the README offers three routes, best first:
   - **CocoaPods** with `:configurations => [...]`;
   - **the framework linked by hand** through a shipped `Inspektor.xcconfig`, which sets
     `OTHER_LDFLAGS[config=…]` and `FRAMEWORK_SEARCH_PATHS[config=…]` only for dev
     configurations. The framework is static, so there is nothing to embed;
   - **SPM** in a separate dev app target, for teams that already split targets.

   App code guards its calls with `#if canImport(InspektorUrlSession)`. The M0 audit checks the
   production `.ipa`.
5. **A Swift-only sample:** `iosApp/` with Debug, Release-dev and Release-prod configurations,
   using the shipped xcconfig. It is the audit's iOS subject and the conformance kit's simulator
   target.
6. **Field-test the released framework in a React Native app** that installs it with its own
   Swift glue, before the React Native package wraps the same glue.

## M3: React Native

As in [react-native-npm-plan.md](react-native-npm-plan.md). M1 and M2 deliver its native
prerequisites; what remains is the TurboModules, the per-variant linking, the Metro helper, the
Expo plugin and the npm release. Its release audit is the M0 tool, wrapped.

## F: Flutter, in parallel after M0

A pure-Dart package, so there is no native code to strip.

- **F0, spikes:**
  - Can a Dart `ServerSocket` bind an abstract Unix socket on Android
    (`InternetAddress('@inspektor_<pid>_dart', type: InternetAddressType.unix)`)? The protocol
    already reserves the `dart` runtime name for it.
  - **Proof of removal:** a release build whose production entry point never imports the package,
    checked with `--analyze-size` and the M0 audit on `libapp.so`. Fall back to a
    `const bool.fromEnvironment('INSPEKTOR')` gate that tree shaking removes.
- **F1, capture and serve:**
  - Capture: `HttpOverrides.global` for `dart:io`, which also covers `package:http`'s `IOClient`
    and dio's default adapter.
  - Serve: an in-memory ring buffer, and the protocol server.
  - It passes the conformance kit, and Eagly shows it with no Eagly changes beyond what E2
    covers.
- **F2, more adapters:**
  - `inspektor_http`, a `BaseClient` wrapper, which covers `cupertino_http`, `cronet_http` and
    `ok_http`;
  - `inspektor_dio`, an interceptor.

  They are separate packages, so an app doesn't pull dio to get the `http` wrapper.
- **F3, overrides:** a port of the four matchers and three actions. It is checked against the same
  override transcripts as Kotlin.
- **F4, in-app UI (optional):** Flutter widgets. Eagly is the viewer until then.
- **Publish** to pub.dev as `inspektor` (free as of today), with verified-publisher status.
- **Persistence** stays in memory at first. A database means a native plugin (sqflite) or a large
  dependency (drift), which would undo "pure Dart, nothing to strip". Revisit if users ask for
  history across restarts.

## E: Eagly, alongside

- **E1:** carry the pinned conformance transcripts; Eagly's client tests replay them.
- **E2:** a Dart server, found by the existing `inspektor_<pid>_<runtime>` parsing, is tested end
  to end on a device. On iOS it takes its own port in 38470–38477 next to a Kotlin server in the
  same app; check that a poll lists both.
- **E3:** show `sdk.runtime` and each transaction's `adapter` in the pane, once M0 item 2 lands.

## Release and versioning

- **One repo, one tag, for the Kotlin-backed clients.** Maven, the XCFramework zips (SPM and
  CocoaPods) and npm all release from `v*.*.*`. They share `VERSION_NAME`, so a React Native
  version names exactly one native version.
- **Flutter versions independently** on pub.dev. Its code is separate, and pub.dev's semver
  conventions differ. Every client reports its own version in `hello.sdk`, and the protocol number
  is what decides compatibility.
- **Protocol bumps are rare and loud:** a new transcript set, Eagly supporting both versions for a
  release, and a line in every client's changelog.

## Risks

- **The contract drifts as clients multiply.** The conformance kit and the audit are the defence;
  a client that can't run both isn't released.
- **Kotlin/Native weight on iOS,** especially with the UI. The no-UI flavour keeps Eagly-only
  teams light.
- **Platform churn:** React Native's autolinking (see its plan), Apple's App Store privacy rules,
  CocoaPods' sunset. The SPM-first distribution in M2 hedges the last.
- **Scope.** Five clients is a lot for one maintainer. If time runs short, Flutter F4 and the
  React Native Expo plugin are the first things to defer, because Eagly covers the viewing in both
  cases.
