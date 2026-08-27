<div align="center">
  <picture>
    <img width="120px" alt="Inspektor logo" src="https://raw.githubusercontent.com/Gyanoba/inspektor/dev/images/inspektor.png">
  </picture>
</div>

# Inspektor 🕵️‍♂️
![main status](https://github.com/ShreyashKore/inspektor/actions/workflows/publish.yaml/badge.svg?branch=main)
[![kotlin](https://img.shields.io/badge/Kotlin-2.0.21-8949FB.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![kotlin](https://img.shields.io/badge/ktor-3.0.1-8949FB.svg?style=flat&logo=kotlin)](https://github.com/ktorio/ktor)
[![latest version](https://img.shields.io/maven-central/v/com.gyanoba.inspektor/inspektor?color=blue&label=Version)](https://central.sonatype.com/artifact/com.gyanoba.inspektor/inspektor)

> [!CAUTION]
> This library is **not stable**, and the API may change. It is not advised to use it in
> production projects.

Inspektor is an HTTP inspection library for Ktor. It allows you to view HTTP requests and responses,
including basic information, headers, and bodies. Please note that this library is not stable, and
the API may change. Users are not advised to use it in production projects.

Here's an [introductory article](https://medium.com/@koreshreyash/inspektor-multiplatform-http-inspection-library-for-ktor-6c78ae5e5661) for those who are interested.

![Screenshots](images/screenshots.png)

## Installation

Add the following dependency to your `build.gradle.kts` file:

```kotlin
dependencies {
    implementation("com.gyanoba.inspektor:inspektor:latest-version")
}
```

### Keeping Inspektor out of production builds

Inspektor bundles a database, a Compose UI and everything needed to inspect traffic, so you almost
certainly do not want it in your release binary. A companion artifact,
`com.gyanoba.inspektor:inspektor-no-op`, exists for exactly that: it exposes the **same public API**
under the same package, but every part of it is empty. The Ktor plugin installs no hooks, so
requests and responses are passed straight through — nothing is read, buffered, persisted or logged,
and no UI, database, notification or Compose code is shipped at all.

Depend on the real library only in the variant you debug with, and on the no-op everywhere else:

```kotlin
dependencies {
    // Android build types
    debugImplementation("com.gyanoba.inspektor:inspektor:latest-version")
    releaseImplementation("com.gyanoba.inspektor:inspektor-no-op:latest-version")

    // ...or Android product flavors
    devImplementation("com.gyanoba.inspektor:inspektor:latest-version")
    prodImplementation("com.gyanoba.inspektor:inspektor-no-op:latest-version")
}
```

Your code does not change. `install(Inspektor) { ... }`, `openInspektor()` and `setApplicationId()`
all still compile and run in both variants — they just do nothing in the no-op one.

Because the no-op artifact contains no Activity, no `ContentProvider`, no resources and no
`androidx.startup` initializer, it contributes nothing to your merged manifest either. Its only
dependency is `ktor-client-core`, which your project already has, so it adds no transitive
dependencies of its own.

The iOS `-lsqlite3` linker flag is only required for the real library; the no-op artifact needs no
platform setup at all.

#### What it saves

The `:sample` module in this repo is built in both configurations (`dev` uses the real library,
`prod` uses the no-op). Comparing the two release APKs, without R8/minification:

| | dev (real) | prod (no-op) | saved |
|---|---:|---:|---:|
| **APK on disk** | **14.09 MiB** | **9.95 MiB** | **4.13 MiB (29%)** |
| dex (uncompressed) | 30.1 MB | 28.0 MB | 2.08 MB |
| bundled `.dylib` / `.dll` | 6.26 MB | 0 | 6.26 MB |
| assets | 212 KB | 151 KB | 61 KB |

The `prod` APK contains no Inspektor implementation whatsoever — no `InspektorDatabase`, no UI
screens, no override repository, no retention manager, no HAR export, and none of SQLDelight, KStore
or JsonTree. Only the API stubs (`InspektorConfig`, `LogLevel`, `openInspektor`) survive. Its merged
manifest has no `MainActivity`, no `InspektorFileProvider` and no `ContextInitializer` entry.

Your own savings will differ: much of the dex delta above is Compose UI that the sample app already
uses elsewhere, and R8 will strip some of the rest in a real release build.

#### Using it from Kotlin Multiplatform shared code

If you call `install(Inspektor)` or `openInspektor()` from `commonMain`, the API must be on the
common compile classpath for *every* target, so a per-variant `devImplementation` cannot express it.
Substitute the module for the production variants instead — see `sample/build.gradle.kts`:

```kotlin
configurations.matching { it.name.startsWith("prod") }.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("com.gyanoba.inspektor:inspektor"))
            .using(module("com.gyanoba.inspektor:inspektor-no-op:latest-version"))
            .because("Inspektor must not ship in production builds")
    }
}
```

## Usage

To use Inspektor, install the plugin in your `HttpClient` configuration:

```kotlin
// For Android this is enough
val client = HttpClient {
    install(Inspektor)
}

suspend fun apiCall() {
    client.get("http://example.com")
}
```

For ios you need to add the following `-lsqlite3` to the Other Linker flags under Build Settings.
See more details [here](https://github.com/cashapp/sqldelight/issues/1442#issuecomment-523435492)

For Desktop platforms, you need to specify the APPLICATION_ID using `setApplicationId` before using
Inspektor.
This is used to determine the location to store the database file.

```kotlin
fun main() {
    setApplicationId("com.example.myapp")
    // ...
}
```

## Configuration

You can customize Inspektor using the `InspektorConfig` object. Here are the available options:

- `level`: Specifies the logging level. Available options
  are `LogLevel.NONE`, `LogLevel.INFO`, `LogLevel.HEADERS`, and `LogLevel.BODY`.
- `maxContentLength`: Sets the maximum content length for logging request and response bodies.
- `filter`: Allows you to filter log messages for calls matching a predicate.
- `sanitizeHeader`: Allows you to sanitize sensitive headers to avoid their values appearing in the
  logs.
- `showNotifications`: If set to `true`, notifications will be shown for HTTP requests. Defaults to `true`.
- `retentionDuration`: Specifies the duration for which the logs will be retained. Defaults to 30 days.

### Example

```kotlin
install(Inspektor) {
    level = LogLevel.HEADERS
    maxContentLength = 100_000
    filter { request -> request.url.host.contains("example.com") }
    sanitizeHeader { header -> header == "Authorization" }
    showNotifications = true
    retentionDuration = 30.days
}
```

## Overriding Requests and Responses

This feature is meant to be used for testing purposes. It allows you to override the request
and response bodies.
To use this feature, open the overrides page from the menu. Add new overrides by specifying matchers for the request; then select the action to perform, i.e.
override the request or response. Only the values specified are replaced in the request or response.
Empty values are ignored.



https://github.com/user-attachments/assets/d779934d-3e9c-447e-8f37-94869c251717



You can easily add new overrides by clicking on the Edit icon in the Transactions List screen.
Default override with original info gets created which you can then modify to get your desired result.

## Viewing the logs

Inspektor provides a UI to view the logs. You can access it by invoking `openInspektor` function.
This opens up a new activity in Android, a bottom sheet in iOS, and a new window in the Desktop.

On Android you can also open the UI by clicking on the generated notifications.

## HAR Export

Inspektor supports exporting the logs in HAR format. You can export the logs by clicking on the
"Export as HAR" button in the UI. This allows you to analyze the logs in detail using tools like
[HAR Viewer](https://toolbox.googleapps.com/apps/har_analyzer/) or [Fiddler](https://www.telerik.com/fiddler).

## Upcoming Features 🚀

- [x] Request-Response overriding functionality
- [ ] Pause and allow editing Request and Response
- [x] HAR export for detailed analysis
- [ ] More HTTP client support (OkHttp maybe?)

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Inspiration

This project is inspired by [Chucker](https://github.com/ChuckerTeam/chucker) - An HTTP inspector
for Android & OkHttp. It borrows many ideas (and some code 😉) from the project.

## Disclaimer ⚠️

This library is not stable, and the API may change. Users are not advised to use it in production
projects.
