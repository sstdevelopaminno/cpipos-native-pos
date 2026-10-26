# CpIPOS Native POS 2.0 — Android FCM setup

* Android notifications now have `FirebaseMessagingService`, a HIGH-importance
  notification channel (sound/vibration), Android 13+ permission handling and safe
  tenant/branch matching. Tapping the OS notification deep-links to the table
  after authenticated POS session and unpaid-bill checks. No app credentials
  or Firebase server credentials are shipped in the APK.
* Foreground remains served by existing native QR popup/polling.
* For background / process-not-running delivery, require Google Play Services,
  notification permission, internet connectivity, and no force-stop.
  On Huawei devices without Google services, FCM **cannot** be guaranteed.

## Setup in VS Code
1. Firebase Console: project (Spark free tier), Android app
   **com.cpipos.pos.next.dev** (the debug application ID).
2. Download Firebase Android public configuration as
   `app/google-services.json`. This file is ignored by Git and is never
   included in GitHub CI builds; the Gradle plugin is conditional.
3. In Backend preview, merge/migrate first the separate
   `feature/native-qr-fcm-notifications` service and configure
   `NATIVE_FCM_PROJECT_ID`, `NATIVE_FCM_CLIENT_EMAIL`,
   `NATIVE_FCM_PRIVATE_KEY` as server environment secrets.
   Never place the private key in Android code or local.properties.
4. Build with `./gradlew :app:assembleDebug`, install with the existing
   `scripts/android-device-preview.ps1` (same USB serial).
5. Login to an active POS session. Verify registration API response on
   `/api/pos/native-qr-push-registration` in the deployment logs.
6. Trigger a test QR order, call staff and checkout. Test app foreground,
   background, and process stopped (not force-stopped).
   Android 13+ notification permission must be granted. Check Play Services
   is present and power saving doesn't block background delivery.

CI intentionally builds without Firebase credentials and therefore cannot prove
actual device delivery. Backend preview is not Production.
