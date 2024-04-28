# inspektor

Kotlin Multiplatform Library

### Publish to MavenCentral

1) Registering a Sonatype account as described here: 
   https://dev.to/kotlin/how-to-build-and-publish-a-kotlin-multiplatform-library-going-public-4a8k
2) Add developer id, name, email and the project url to
   `/convention-plugins/src/main/kotlin/convention.publication.gradle.kts`
3) Add the secrets to `local.properties`:

```
signing.keyId=...
signing.password=...
signing.secretKeyRingFile=...
ossrhUsername=...
ossrhPassword=...
```

4) Run `./gradlew :inspektor:publishAllPublicationsToSonatypeRepository`

### Build platform artifacts

#### Android aar

- Run `./gradlew :inspektor:assembleRelease`
- Output: `/inspektor/build/outputs/aar/inspektor-release.aar`

#### JVM jar

- Run `./gradlew :inspektor:jvmJar`
- Output: `/inspektor/build/libs/inspektor-jvm-1.0.jar`

#### iOS Framework

- Run `./gradlew :inspektor:linkReleaseFrameworkIosArm64`
- Output: `/inspektor/build/bin/iosArm64/releaseFramework/inspektor.framework`

#### JS file

- Run `./gradlew :inspektor:jsBrowserProductionWebpack`
- Output: `/inspektor/build/dist/js/productionExecutable/inspektor.js`

#### Wasm binary file

- Run `./gradlew :inspektor:wasmJsBrowserDistribution`
- Output: `/inspektor/build/dist/wasmJs/productionExecutable/inspektor-wasm-js.wasm`
