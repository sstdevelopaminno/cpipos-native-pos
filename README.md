# CpiPOS Native POS 2.x

Native Android POS application by **CUTTING POINT INNOVATION CO., LTD. / บริษัท คัตติ้ง พอยท์ อินโนเวชั่น จำกัด**.

## Distribution and source of truth (27 October 2026)

- **Native Mobile Android 2.0.0**: a signed APK is published by the existing CpIPOS release authority in [CpIPOS Releases](https://github.com/sstdevelopaminno/CpIPOS/releases/tag/native-mobile-v2.0.0). Its pinned source revision is `09c095232c9b2082fde73728f2fb34647788f09d` from this repository. Android 8.0+ (API 26).
- **Android Tablet / legacy POS 1.0.25**: distributed separately from [CpIPOS Releases](https://github.com/sstdevelopaminno/CpIPOS/releases/tag/android-runtime-modern-1.0.25-print-health-auto-update). Do not confuse this with the Native Mobile APK.
- [Company download center](https://www.cuttingpointinnovation.com/downloads) lists both Android variants; publish new URLs only after verifying the release assets and installer signatures.
- **Development baseline:** `main`; use feature branches and CI to introduce changes. Older `feature/native-auth-phase1` is historical and is not the current release source.
- **Existing CpIPOS Web POS + IT remain authoritative** for authentication, packages/renewals, permissions, shift scope, MDM, canonical billing and print-job dispatch. Native must reuse their authenticated APIs rather than create a second ledger or backend.
- **Production database:** existing `CpiPOS-001`, never changed destructively from the APK.

## Native capabilities and QA

Current source includes authenticated Store Code/branch/employee/device entry, shift and sales flows, server-backed catalog and receipts, billing and payment-slip requests, Thai/English/Lao menu selection, subscription runtime lock/recovery, MDM command handling, and Android printer transports.

**Source code and passing CI are not proof of customer readiness.** Verify on a registered physical Android device:
1. Sign in with a test merchant/branch/device, open and close a shift, complete takeaway and dine-in transactions, verify server order/payment totals and safe retries.
2. Print receipts on 58/80 mm printers (Bluetooth, USB, LAN as applicable), verify duplicate protection, reconnects, and print-job acknowledgements.
3. Lock/unlock a device through IT and confirm immediate user-visible response; test offline/reconnect handling.
4. Exercise subscription expiry -> runtime lock -> owner payment/IT confirmation -> entitlement recovery without bypassing lock on sale routes.
5. Switch Thai, English and Lao through settings and reboot; check that translations persist and long text does not overlap bottom navigation.
6. Verify Android release certificate identity, package name, versionCode, checksum, and actual install/upgrade behavior before publishing a new version.

## Build and previews

- Workflow: `.github/workflows/native-foundation.yml` runs unit tests and builds a **debug** APK for PRs.
- Signed public APK is generated from a pinned Native source SHA by the established [CpIPOS release authority workflow](https://github.com/sstdevelopaminno/CpIPOS/blob/main/.github/workflows/build-native-mobile-release.yml) using the existing stable Android signing certificate.
- This repository also contains `native-mobile-release.yml`, but its repository-specific four signing secrets are independent. Do **not** assume they are configured merely because the CpIPOS release-authority workflow succeeds.
- For local Windows/USB/ADB preview, see [Android device guide](docs/WINDOWS_VSCODE_ANDROID_DEVICE.md) and `scripts/android-device-preview.ps1`.

## Security rules

- Never commit signing keys, Supabase service-role credentials, database passwords, or production secrets.
- Native APK never grants additional server permissions. Authoritative checks must be applied server-side to tenant, branch, device, role, active shift, and package.
- Preserve idempotency for payments/printing and leave uncertain-sale recovery available.
- Do not publish a new Android release before CI, signature verification, and physical-device acceptance.
