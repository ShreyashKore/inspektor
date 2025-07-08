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
