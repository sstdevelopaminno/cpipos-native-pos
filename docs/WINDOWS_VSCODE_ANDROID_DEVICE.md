# CpiPOS Native 2.0 — VS Code, Windows, Android USB preview

Verified development source: `feature/native-device-pairing-preview-20261022`.

**App architecture:** This is a **100% Native Android mobile POS** (Kotlin +
Jetpack Compose), **not** a web wrapper. The API hostname is only the
authenticated networking backend shared with Tablet POS; both access the
same CpiPOS-001 Primary business data. No WebView or duplicate database.
See [architecture and feature parity](NATIVE_ANDROID_100_PERCENT_ARCHITECTURE.md).
**Do NOT clone the minimal `main` branch for Android builds.**
This app has its own debug identity `com.cpipos.pos.next.dev`, so it can install
beside Android POS 1.0.23 (`com.cpipos.pos`) without replacing it.

## A. Open code in Visual Studio Code

Run in VS Code's PowerShell terminal. Use an existing folder if you have one.

**First time — E: drive available:**

```powershell
cd E:\
git clone --branch feature/native-device-pairing-preview-20261022 https://github.com/sstdevelopaminno/cpipos-native-pos.git cpipos-native-pos-latest
cd E:\cpipos-native-pos-latest
code .
```

**If E: is unavailable:** replace `E:\` with `$HOME\Documents` and use the
matching directory in later commands.

**Existing checkout, without deleting edits:**

```powershell
cd E:\cpipos-native-pos-latest
git status --short
git remote -v
git fetch origin
git branch --list feature/native-device-pairing-preview-20261022
```

If the branch is not listed locally:

```powershell
git switch --track origin/feature/native-device-pairing-preview-20261022
```

If it is already listed, run:

```powershell
git switch feature/native-device-pairing-preview-20261022
git pull --ff-only origin feature/native-device-pairing-preview-20261022
```

If Git reports uncommitted/conflicting changes, preserve your own edits first.
**Never run `git reset --hard`, force-pull, or delete your old workspace.**

Check that `git remote -v` points to **cpipos-native-pos**, not Web POS
(`CpIPOS`) or IT.

## B. Build environment (one-time setup)

- JDK 17; check `java -version` and `$env:JAVA_HOME`.
- Android SDK platform 37, Build Tools 37.0.0, SDK Platform-Tools.
  Android Studio's SDK Manager can install these, even if you edit in VS Code.
- Git and VS Code. Optional: scrcpy to mirror your mobile screen on the computer.
- `gradlew.bat` and Gradle wrapper are committed (Gradle 9.5; AGP 9.3).
- Create or edit untracked `local.properties` if SDK location is not detected,
  e.g. `sdk.dir=C:\\Users\\YourUser\\AppData\\Local\\Android\\Sdk`.

```powershell
java -version
adb version
.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

For normal UI/employee login/device/shift testing, **leave device pairing off**.
This is the safe build default even if `local.properties` is absent.

Optional Web server override only when necessary:

```properties
CPIPOS_WEB_POS_API_URL=https://cp-ipos-web.vercel.app
CPIPOS_ANDROID_PAIRING_ENABLED=false
```

The live Native flow uses the existing HTTPS Web POS backend, which in turn uses
Supabase CpiPOS-001. A Supabase publishable key is NOT a bypass for real login.
Do not copy any service-role secret, employee PIN, database password, Android
signing key or production auth cookie to this repository.

## C. Connect the Android phone

On the phone: Developer options → enable USB debugging → attach USB data cable,
unlock phone, accept the USB debugging authorization popup.

```powershell
.\scripts\android-device-preview.ps1 -ListOnly
```

Your phone must say `device`, not `unauthorized` or `offline`.
If more than one is connected, supply the local ADB serial using `-DeviceSerial`.
Do not publish any device serials in GitHub.

## D. Compile, install and view the APK

```powershell
.\scripts\android-device-preview.ps1 -Mirror
```

This builds `app\build\outputs\apk\debug\app-debug.apk`, installs the
separate app id `com.cpipos.pos.next.dev`, launches it, and uses scrcpy if
installed. Without scrcpy:

```powershell
.\scripts\android-device-preview.ps1
```

After the first successful build, reinstall faster:

```powershell
.\scripts\android-device-preview.ps1 -SkipBuild -Mirror
```

If PowerShell blocks the script, apply to **this terminal only**:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

If running manually:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug
adb devices -l
adb install -r -t .\app\build\outputs\apk\debug\app-debug.apk
adb shell monkey -p com.cpipos.pos.next.dev -c android.intent.category.LAUNCHER 1
```

Do NOT uninstall existing `com.cpipos.pos` to troubleshoot this debug app.

## E. What you can actually inspect

- Branded CpiPOS login (Thai/English, Store Code) on opening the APK.
- With a **real authorized test Store Code**: branch → numeric back-office
  employee code → registered terminal → shift → mode → product grid/cart.
- With an approved test account/device/shift, cash checkout runs through the
  real Web POS and can create a **real sale/payment**. Do **not** press Confirm
  to test appearance alone, and do not use a real merchant with no QA approval.
- Dine-in/Table Bill is not enabled yet; bank transfer is not bank-verified;
  scanning, reliable offline-sync and hardware ESC/POS printer support are
  not production-complete.
- The app shows a receipt only after the server confirms a paid order; any
  uncertain checkout is locked for review to avoid double payments.
- This branch contains no user-facing fake `ทดลอง UI` shortcut, respecting the
  approved unified Native UI. Without test access you can still inspect the
  first Store Code screen and code, but cannot bypass login to view cash screens.

**Optional dedicated QA Android pairing (do not enable for ordinary previews):**

After an IT administrator provides a **separate QA cashier terminal** that is
not being used by Android 1.0.23, set in untracked `local.properties`:

```properties
CPIPOS_ANDROID_PAIRING_ENABLED=true
```

Rebuild/reinstall the APK; the app creates a per-install UUID in private Android
preferences and sends `android_install_id` / `android_app_version` and
`x-cpipos-android-pos:true` only during authorized terminal selection.
The server may change `branch_devices.metadata` during successful pairing.
Native requires the server's `paired`, `confirmed` or authorized
`lab-roaming` acknowledgment. If device is already paired elsewhere, stop and
let IT reconcile; do not remove bindings or retry on customer terminals.
Switch back to false and rebuild for routine UI previews.

## F. CI APK alternative and issue reporting

The branch CI runs `:app:testDebugUnitTest :app:assembleDebug` and uploads
`cpipos-native-preview-debug-apk` at GitHub Actions (7-day retention).
In the run's **Artifacts** section download ZIP and extract `app-debug.apk`.
Local Gradle/USB build remains the most direct workflow in VS Code.

Send screenshots of login, branch, cashier selection, mode and sale/cash screens
from the device, plus the *non-secret* error code and GitHub run link for
failures. Do not send customer account secrets, PINs, auth cookies or bank data.
