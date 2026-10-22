# CpiPOS Native Android POS 2.0

Native Android 2.x development repository for **CUTTING POINT INNOVATION CO., LTD.**
(บริษัท คัตติ้ง พอยท์ อินโนเวชั่น จำกัด).

## Development status (2026-10-22)

The Android source lives on the active development branch
[`feature/native-device-pairing-preview-20261022`](https://github.com/sstdevelopaminno/cpipos-native-pos/tree/feature/native-device-pairing-preview-20261022),
based on `feature/native-auth-phase1`. The repository's `main` currently holds
only the historical handoff documents; it is **not buildable as an APK** until a
reviewed integration merge. Do not overwrite it or merge the old draft PRs
blindly.

- Android: Kotlin, Jetpack Compose, AGP 9.3, Gradle 9.5, JDK 17.
- Existing production Android **1.0.23 (`com.cpipos.pos`) stays untouched**.
- Native 2.0 debug APK: `com.cpipos.pos.next.dev` can be installed alongside 1.0.23.
- Current visible app: branded Store Code → authenticated branch → numeric
  employee code → registered cashier terminal → live POS session → open shift →
  takeaway product/cart → server-confirmed cash payment/receipt.
- The Native app uses the **existing HTTPS CpIPOS Web POS backend**
  `https://cp-ipos-web.vercel.app`, which accesses authoritative Supabase
  **CpiPOS-001**; it does not require its own Vercel deployment.
- Read-only Supabase/Kotlin models and isolated offline SQLite scaffolds remain
  in source, but **a Web POS cookie is not a Supabase JWT**.
- Neither real offline sale synchronization nor physical printer support has
  been validated. Bank transfer/dine-in/barcode camera and Lao currency are
  not yet release-ready. Do not treat an Android Build success as field signoff.

## VS Code → Windows → USB Android installation

Detailed illustrated-free guide:
[Open in VS Code, build debug APK, install and view on phone](docs/WINDOWS_VSCODE_ANDROID_DEVICE.md).

First checkout (example drive E:):

```powershell
cd E:\
git clone --branch feature/native-device-pairing-preview-20261022 https://github.com/sstdevelopaminno/cpipos-native-pos.git cpipos-native-pos-latest
cd E:\cpipos-native-pos-latest
code .
```

Then connect an Android handset with USB debugging enabled and JDK 17 / Android
SDK platform 37 installed:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug
.\scripts\android-device-preview.ps1 -ListOnly
.\scripts\android-device-preview.ps1 -Mirror
```

If `scrcpy` is not installed, omit `-Mirror` and view the APK on the phone.
The actual debug APK path is `app\build\outputs\apk\debug\app-debug.apk`.

## Registered Android terminal pairing (opt-in QA)

This development branch adds a versioned Android installation UUID and validates
the server's explicit pairing acknowledgement. **Pairing defaults OFF** in all
normal APK builds to avoid changing registered terminals used by existing
Android 1.0.23. Only after IT approves an isolated QA cashier device, set
`CPIPOS_ANDROID_PAIRING_ENABLED=true` in ignored `local.properties`, rebuild
and follow the [device guide](docs/WINDOWS_VSCODE_ANDROID_DEVICE.md).

Pairing on an in-use cashier terminal may change `branch_devices` metadata
and affect old MDM/Android usage; never enable it to merely view UI. Server
credentials, service-role keys, signing secrets and PINs must never enter Git
or the APK.

## Implementation / release gates

- Existing Web POS API and Supabase CpiPOS-001 remain the authority for user,
  tenant, branch, terminal, package, active shift, price, order and payments.
- Cash receipts are returned after the server confirms the order is completed.
  Current Web payment records still need canonical persistence of **actual
  cash tendered and change** before a full Native cashier release.
- Any uncertain server payment is journaled for manual reconciliation, **not**
  retried under a new bill UUID. A tested server-side recovery API is still needed.
- Real shop/device and printer UAT, offline outbox synchronization and receipt
  reprint verification remain open.
- No new paid cloud services, Supabase migration, Vercel production deployment,
  production signing or Android 1.0.23 replacement is part of this branch.

See [development status](docs/DEVELOPMENT_STATUS.md),
[roadmap](docs/ROADMAP.md), and [security](docs/SECURITY.md).
