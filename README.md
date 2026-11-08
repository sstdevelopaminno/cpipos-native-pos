# CpiPOS Native POS 2.x

Native Android POS application by **CUTTING POINT INNOVATION CO., LTD. / บริษัท คัตติ้ง พอยท์ อินโนเวชั่น จำกัด**.

## Distribution and source of truth (verified 8 November 2026)

- **Native Mobile Android 2.1.0**: latest confirmed signed Stable APK, published 5 November 2026 by the [CpIPOS release authority](https://github.com/sstdevelopaminno/CpIPOS/releases/tag/native-mobile-v2.1.0). It includes automatic verified Stable APK download and Android user-confirmed installer; Android 8.0+ (API 26). **This version does not include the 8 November kitchen/item-details fixes.**
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

## 8 November 2026 — Mobile dine-in kitchen and item detail QA

A follow-up branch adds a per-product **รายละเอียด** action for free-text item notes in both takeaway and dine-in and a non-blocking central kitchen dispatch/repair check after an acknowledged dine-in order. This branch is **not yet a released APK**. See [dine-in kitchen and modifier QA matrix](docs/NATIVE_DINE_IN_KITCHEN_ITEM_DETAILS_QA_2026-11-08.md).

**Known blockers:** Existing customized table bills remain locked against unsafe full-cart edits; raw recipe ingredient changes do not yet have a supported structured selection contract; physical KDS/printer evidence is pending. The central backend's `NATIVE_ATOMIC_MENU_SALES_ENABLED` release flag must be verified and approved before any customer rollout. The Native repo's separate signing workflow has missing secret configuration; use the central CpIPOS release authority for final signed builds.


### Customer self-update (candidate, 8 November 2026)

The next Native candidate adds **เพิ่มเติม → อัปเดตแอป CpiPOS** so an employee can explicitly check Stable releases, watch download status and start the Android installer only when there are no unresolved sales or local drafts. The automatic 6-hour Stable check remains available. Updates require the same package name and exact Android signing certificate; **never uninstall or clear app data**. Android must obtain user confirmation to install a sideloaded APK. Do not distribute this unreleased branch to customers.
