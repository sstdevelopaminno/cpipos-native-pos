# Open CpIPOS Native in VS Code and preview on a real Android phone (Windows)

**Verified source branch:** `feature/native-auth-phase1` (not the nearly empty `main` handoff branch). Follow [Cross-project handoff](../docs/CROSS_PROJECT_HANDOFF_2026-09-25.md) and [Issue #4](https://github.com/sstdevelopaminno/cpipos-native-pos/issues/4). This workflow **does not call Vercel**, deploy the website, alter Supabase records, or replace Android production 1.0.23.

## 1 — Get the code on your own Windows computer

Run these commands in the **VS Code PowerShell terminal**. The GitHub connector cannot run commands in your personal Windows PC or physically connect to its USB port, so these are local steps.

**If the folder does NOT exist:**

```powershell
cd E:\
git clone --branch feature/native-auth-phase1 https://github.com/sstdevelopaminno/cpipos-native-pos.git cpipos-native-pos-latest
code E:\cpipos-native-pos-latest
```

**If `E:\cpipos-native-pos-latest` already exists:**

```powershell
cd E:\cpipos-native-pos-latest
git status --short
git remote -v
git fetch origin
git switch feature/native-auth-phase1
git pull --ff-only origin feature/native-auth-phase1
code .
```

If local work is uncommitted or the branch does not exist locally, **do not use `reset --hard` or force a pull**. First preserve your edits, then create the branch with:

```powershell
git switch --track origin/feature/native-auth-phase1
```

Check `git remote -v` points to `sstdevelopaminno/cpipos-native-pos.git`, not the Web POS or IT repositories.

## 2 — Check Android build environment

Install JDK **17**, Android SDK Platform Tools, Android SDK platform `android-37.0` and Build Tools `37.0.0` (the project currently uses AGP 9.3.0, Gradle 9.5.0 and compileSdk 37). Android Studio can install SDK components even if you edit the project in VS Code.

```powershell
java -version
$env:JAVA_HOME
adb version
```

If `adb` is not in PATH, the script checks `ANDROID_HOME`, `ANDROID_SDK_ROOT` and `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`. Keep `local.properties` private and untracked; `sdk.dir=...` may be needed to locate the SDK for Gradle.

**Supabase is optional for UI preview:** The current trusted PIN gateway is intentionally disabled. A project URL/publishable key alone will NOT enable production employee login or real sales. Do not put a service-role key, employee PIN, or database password into `local.properties` or GitHub.

## 3 — Connect Android phone by USB

On the phone, enable Developer options → USB debugging, connect a **data-capable** USB cable, unlock the phone, and accept the **Allow USB debugging** prompt. In VS Code terminal:

```powershell
.\scripts\android-device-preview.ps1 -ListOnly
```

Only a device with state `device` (not `unauthorized` or `offline`) can receive the APK. If multiple devices are connected use `-DeviceSerial` with the serial shown *locally* by ADB. Do not post or commit private device serials.

## 4 — Build, install, launch and mirror

From `E:\cpipos-native-pos-latest`:

```powershell
.\scripts\android-device-preview.ps1 -Mirror
```

This runs `gradlew.bat --no-daemon :app:assembleDebug`, installs `app\build\outputs\apk\debug\app-debug.apk`, launches **`com.cpipos.pos.next.dev`**, and opens `scrcpy` if available.

On a system without scrcpy, omit `-Mirror` and view the app directly on the phone. Once the debug APK has been built:

```powershell
.\scripts\android-device-preview.ps1 -SkipBuild -Mirror
```

If PowerShell blocks scripts, use this **only in the current terminal** (do not change machine-wide settings):

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

Then retry the script.

**Manual fallback:**

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug
adb devices -l
adb -s <serial-from-adb> install -r -t .\app\build\outputs\apk\debug\app-debug.apk
adb -s <serial-from-adb> shell monkey -p com.cpipos.pos.next.dev -c android.intent.category.LAUNCHER 1
scrcpy -s <serial-from-adb>
```

Replace `<serial-from-adb>` with the identifier displayed **on your computer**. `-s` can be omitted if exactly one device is connected.

## 5 — Faster alternative: existing GitHub Actions APK

GitHub Actions run [Native Foundation Build #35756961237](https://github.com/sstdevelopaminno/cpipos-native-pos/actions/runs/35756961237) passed for source commit `2b07c5d`; artifact **`cpipos-native-preview-debug-apk`** (artifact ID `10708368370`) was confirmed unexpired at handoff. Download ZIP from **Artifacts** at the bottom of that run, extract `app-debug.apk`, then use the manual `adb install -r -t` command pointing to the extracted file. This option installs the *older build tied to that commit*; use local Gradle for the newest branch source. GitHub artifacts expire (this one was marked for 2026-09-29), so rebuild locally or trigger new CI after that.

## 6 — What to test / known limitations

Walk through native UI: store-code entry → branch picker → PIN field → counter → choose **กลับบ้าน / นั่งโต๊ะ** → product list → cart → mock cash/transfer → back/cancel. Enter **only dummy PIN/test inputs** to inspect the preview, not a real employee PIN. The guard gateway returns BackendUnavailable and does not send credentials. Any screen progression after that is for UI preview, **not proof of successful employee authentication**. Products/checkout are mock Compose data; no real payment, stock deduction, receipt, shift or sync writes.

Development APK has its own package ID `com.cpipos.pos.next.dev`; it installs alongside production `com.cpipos.pos` (Android 1.0.23). Never uninstall/replace production Android to fix the preview.

Please send screenshots of login, mode picker, sale grid, cart and checkout from the installed debug app before requesting detailed UX changes. Verify device resolution, touch targets, Thai text wrapping and bottom navigation on your actual phone.
