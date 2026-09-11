# Build Baseline

CpIPOS Native 2.0 foundation targets:

- JDK 17
- Android Gradle Plugin 9.3.x
- Gradle 9.5.x
- compileSdk 37
- targetSdk 37
- minSdk 26
- Jetpack Compose stable BOM line

The Gradle wrapper should be generated and committed from a verified local environment before CI is considered authoritative.

Development builds use `com.cpipos.pos.next` (with a debug suffix where configured) and must remain installable alongside the production Android 1.0.23 application.
