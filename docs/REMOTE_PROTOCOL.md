# Inspektor remote protocol, v1

This document specifies how a desktop tool reads an app's recorded traffic and edits its overrides
while the app runs. The tool is [Eagly](https://github.com/ShreyashKore/eagly), the desktop viewer
for Android and iOS devices; the app is anything that ships Inspektor. **This is the canonical
spec.** Every server implementation conforms to it, and clients follow it.

| Side | Implementation |
|---|---|
| Server, Kotlin (Android, iOS, desktop) | `inspektor-core`, package `com.gyanoba.inspektor.remote` |
| Server, Dart (Flutter) | Planned. It speaks this same protocol; nothing is shared but the wire format |
| Client | Eagly, `lib/features/network/services/inspektor/` |

## Turning it on

The server is off until the app starts it:

```kotlin
@OptIn(UnstableInspektorAPI::class)
InspektorRemote.start()   // idempotent, returns at once; the server runs in the background
```

`InspektorRemote` also exists in the no-op artifacts, where it does nothing. Code that starts it
compiles in production builds, and nothing listens there.

## Scope and trust model

Inspektor is a development tool. Production releases ship the no-op artifacts. The server runs
only in builds that never reach real users (debug, UAT, dev flavours), and only after
`InspektorRemote.start()`.

**There is no authentication.** Whoever reaches the socket can read every recorded transaction and
create or delete overrides. That includes Eagly, through adb or usbmux, and also any other app on
the same device: iOS apps can connect to each other's loopback ports, and Android apps can connect
to each other's abstract sockets. React Native's Metro makes the same trade.

What bounds it:

- **Production releases contain no server.** The no-op artifacts have no transport code.
- **The server is opt-in,** through `InspektorRemote.start()`.
- **It is reachable only on the device.**
  - The listener is an abstract Unix socket on Android and `127.0.0.1` everywhere else, so the
    network can't reach it. That is unlike Metro, which listens on every interface.
  - It isn't HTTP, so a web page can't drive it (see the rules below).
- **It is visible.** On start, the server writes one line to the device log naming its socket or
  port: logcat on Android, the unified log (`NSLog`) on iOS, stdout on desktop.

Two rules keep "a web page can't drive it" true. Every server must follow both:

1. **Close the connection, without replying, on a frame that is too large or isn't valid JSON.**
   - A browser can only put an HTTP request on a loopback socket, and its first four bytes are
     text.
   - Read as a length, `GET ` is about 1.1 GiB and `POST` about 1.25 GiB, far over the frame cap.
   - The page controls only the request body, which comes after headers the browser writes, so it
     can never forge a valid first frame.
   - It can't read anything back either: `hello` isn't an HTTP response, and a WebSocket attempt
     never gets `101 Switching Protocols`.
2. **Any HTTP or WebSocket endpoint added later needs a token,** as the Dart VM service has.
   Adding one removes the protection above.

## Transport and discovery

The app listens and the client connects. That is the only direction that works over USB on both
platforms: iOS has no way for a device to open a connection to the host.

| | Android | iOS | Desktop (JVM) |
|---|---|---|---|
| Listener | Abstract Unix socket `inspektor_<pid>_<runtime>` | TCP on `127.0.0.1` | TCP on `127.0.0.1` |
| Port or name | `runtime` is `kotlin` or `dart` | First free port of 38470–38477 | First free port of 38470–38477 |
| How a client finds it | `@inspektor_` in `/proc/net/unix` | Probing the port range | Probing the port range |
| How a client reaches it | `adb forward tcp:0 localabstract:<name>` | usbmux, which connects to the device's loopback | Directly |

- **Why the pid and runtime are in the Android name:** separate processes of one app get separate
  sockets, and a Dart server in the same process as a Kotlin one gets its own.
- **Never end a socket name in `_devtools_remote`.** Chrome's `chrome://inspect` would list it and
  then fail to speak the Chrome DevTools Protocol to it.
- **On iOS, the server reopens its listener whenever accepting fails,** because iOS can reclaim a
  listening socket while the app is suspended.

## Framing

Each frame is a 4-byte big-endian unsigned length followed by that many bytes of UTF-8 JSON, one
object per frame. A frame is at most **16 MiB**; larger bodies are read in ranges with `body.get`.

When either side receives a frame over the cap, or one that isn't a JSON object, it closes the
connection without replying. For a server that is rule 1 above. For a client, it means a broken
peer fails fast instead of being buffered.

## Handshake

The server speaks first, with `hello`. It carries only public identity:

```json
{"type": "hello", "protocol": 1,
 "sdk":   {"name": "inspektor", "runtime": "kotlin", "adapters": [], "overrides": true},
 "app":   {"id": "com.example.app", "name": "Example", "version": "1.4.0 (51)",
           "pid": 12345, "process": "com.example.app"},
 "store": {"id": "7c0e2f4a-…", "persistent": true, "oldestId": "1201"}}
```

| Field | Meaning |
|---|---|
| `protocol` | The version the server speaks. A client that can't speak it closes the connection and says which side to update. |
| `sdk.runtime` | `kotlin` or `dart`. |
| `sdk.adapters` | The HTTP stacks recording in this process. Kotlin doesn't track them yet, so it sends `[]`. |
| `sdk.overrides` | Whether the `overrides.*` methods work. |
| `app.id` | The application id or bundle id. `name`, `version`, `pid` and `process` are optional. |
| `store.id` | Identifies the transaction store; see [Backfill and reconnect](#backfill-and-reconnect). |
| `store.persistent` | Whether the store outlives the process. |
| `store.oldestId` | The oldest transaction still stored, when there is one. |

The client may send requests as soon as `hello` arrives. There is no authentication step.

## Requests and replies

The shape follows JSON-RPC:

- **Request:** `{"id": 7, "method": "…", "params": {…}}`. `id` is an integer chosen by the client;
  `params` may be omitted.
- **Success:** `{"id": 7, "result": …}`.
- **Failure:** `{"id": 7, "error": {"code": "…", "message": "…"}}`. The connection stays open.

Transaction ids travel as strings. Servers accept a number as well.

| Method | Params | Result |
|---|---|---|
| `subscribe` | `afterId?`, `inlineBodyLimit?` (bytes; default 0) | `{}`. The server then sends every stored transaction after `afterId` as `transaction.updated`, oldest first, followed by live events. A new `subscribe` replaces the previous one. |
| `transactions.list` | `afterId?` (default `"0"`), `limit?` (1–200; default 100) | `{"transactions": [...], "hasMore": bool}`, oldest first, without inline bodies |
| `transactions.get` | `ids` (array) | `{"transactions": [...]}`. Unknown ids are left out. |
| `body.get` | `id`, `part`, `offset?`, `length?` | `{"encoding", "data", "size", "truncated"}`; see [Bodies](#bodies) |
| `transactions.clear` | – | `{}`. Deletes every transaction from the app's store, not just from the client's view. Subscribers get `transactions.cleared`. |
| `overrides.list` | – | An array of [overrides](#overrides) |
| `overrides.put` | An override | The stored override. `id` 0 adds a new one; any other id replaces that one. |
| `overrides.delete` | `id` | `{}` |
| `ping` | – | `{}` |

Error codes: `invalid_params`, `not_found`, `unknown_method`, `internal`.

## Events

Events are server-initiated: `{"type": "event", "event": "…", "data": …}`.

| Event | Data |
|---|---|
| `transaction.started` | A transaction just recorded; usually only the request half is known |
| `transaction.updated` | A transaction as it stands now. Backfill sends this too. |
| `transactions.cleared` | None. The store is empty. |
| `overrides.changed` | Every override. Sent once on `subscribe` and after each change. |

- **Every event carries the transaction's whole current state, not a diff.** A client may receive
  the same transaction more than once, for example during backfill, and treats each as an update.
- **Clients ignore events they don't know.**

## Transaction object

```json
{"id": "1842", "method": "POST", "url": "https://api.example.com/v1/login", "protocol": "h2",
 "requestDate": "2026-10-18T09:12:03.412Z", "responseDate": "2026-10-18T09:12:03.643Z",
 "tookMs": 231,
 "request":  {"headers": [["content-type", "application/json"], ["authorization", "***"]],
              "contentType": "application/json", "size": 64, "body": "available"},
 "response": {"code": 200, "message": "OK", "headers": [["set-cookie", "a=1"], ["set-cookie", "b=2"]],
              "contentType": "application/json", "size": 1543, "body": "available",
              "tlsVersion": "TLSv1.3", "cipherSuite": "TLS_AES_128_GCM_SHA256"},
 "error": null,
 "override": {"original": {"response": {"code": 500, "headers": [], "body": "available"}}}}
```

- **Required:** `id`, `method` and `url`. Servers don't send a transaction that has no URL yet.
- **Optional:** every other field.
- **No `response` means none has arrived yet,** or the exchange failed. In that case `error`
  says why.
- **Headers are `[[name, value], …]`,** one pair per value, in recorded order. Duplicates such as
  `Set-Cookie` are kept. Header sanitisation (`sanitizeHeader`) has already run on the device.
- **`body` is one of four states:**
  - `available`: captured in full;
  - `empty`: the exchange had no body;
  - `truncated`: cut at the integration's `maxContentLength`;
  - `not_captured`: the log level skipped bodies, or the body could only be read once.
- **Inline bodies.** When a body is `available` or `truncated` and fits the subscriber's
  `inlineBodyLimit`, it also travels as `bodyData`, with `"encoding": "utf8"`.
- **`override`** appears when an override rewrote the exchange. `original.request` and
  `original.response` hold the values it replaced. Their bodies are fetched with the `original…`
  parts.
- **Reserved:** `adapter` (which HTTP stack recorded the exchange) and `timings` (`dnsMs`,
  `connectMs`, `tlsMs`, `sendMs`, `waitMs`, `receiveMs`). Clients already read both; the Kotlin
  server doesn't record them yet.

## Bodies

`body.get` takes an `id` and a `part`. The parts are `request`, `response`, `originalRequest` and
`originalResponse`.

- **Without a range,** the reply is `{"encoding": "utf8", "data": "<text>", "size": <bytes>,
  "truncated": <bool>}`.
- **With `offset` and/or `length`** (in bytes), `data` is that slice of the body's UTF-8 bytes,
  base64-encoded (`"encoding": "base64"`). Base64 is used because a byte range can split a
  character. `size` is always the whole body's size in bytes.
- **A missing transaction or body** is the error `not_found`.

## Overrides

The wire form is the JSON that kotlinx.serialization produces for `com.gyanoba.inspektor.data.Override`.
It is the same shape the on-device override store keeps:

```json
{"id": 1, "name": "Fail the items call", "enabled": true,
 "type": {"type": "http", "method": "Get"},
 "matchers": [{"type": "pathMatcher", "path": "/v1/items"}],
 "action": {"type": "FixedResponse", "statusCode": 500, "responseBody": "boom",
            "requestHeaders": {}, "requestBody": null, "responseHeaders": {}}}
```

| Part | Values |
|---|---|
| Matchers | `url` (`url`), `urlRegex` (`url`), `hostMatcher` (`host`), `pathMatcher` (`path`). All of them must match. |
| Action `type` | `FixedRequest`, `FixedResponse`, `FixedRequestResponse`, `None` |
| Methods | `Get`, `Post`, `Put`, `Delete`, `Patch` |

A Dart server must produce and accept exactly this JSON.

## Backfill and reconnect

- **The Kotlin store numbers transactions with `AUTOINCREMENT`,** so ids only grow, even after
  everything is deleted. A client remembers the highest id it has seen and resumes with
  `subscribe(afterId)`; nothing in between is missed.
- **`store.id` tells the client whether its remembered id still applies.**
  - The Kotlin server keeps it in `<app data dir>/inspektor-remote/store-id`, beside the database.
    It survives app restarts and goes away with the app's data.
  - If the file can't be written, the server uses a fresh id for each process.
- **When `store.id` changes, the client forgets its position and backfills from the start.**
- **An in-memory store** (the planned Dart server) sends `persistent: false`. Its ids are monotonic
  only within the process.
- **A suspended iOS app accepts no connections.** A client should show it as paused rather than
  gone until its process exits, then backfill when it resumes.

## Versioning

- **Additive changes keep the protocol number:** new fields, events, methods and error codes.
  Clients ignore what they don't know, and servers answer an unknown method with
  `unknown_method`.
- **Anything else bumps `protocol`.** A client that meets a version it can't speak tells the user
  which side to update: Eagly when the server is newer, the app's Inspektor when it is older.
