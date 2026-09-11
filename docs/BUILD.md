# Build Baseline

CpIPOS Native 2.0 foundation targets:

- JDK 17
- Android Gradle Plugin 9.3.x
- Gradle 9.5.x
- compileSdk 37 (Android 17 / Cinnamon Bun preview SDK)
- targetSdk 36 until Android 17 targeting is intentionally adopted and validated
- minSdk 26
- Jetpack Compose stable BOM line

CI installs the Android 17 preview platform package as `platforms;android-37.0` and Build Tools `37.0.0`.

The Gradle wrapper should be generated and committed from a verified local environment before CI is considered authoritative.

Development builds use `com.cpipos.pos.next` (with a debug suffix where configured) and must remain installable alongside the production Android 1.0.23 application.
