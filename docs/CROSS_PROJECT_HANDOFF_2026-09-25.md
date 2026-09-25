# CpIPOS — Cross-project handoff / จุดพักงานและส่งต่องาน

**Recorded:** 2026-09-25 (Thailand, UTC+7). **Owner instruction:** STOP developing/deploying CpIPOS Web and IT for now. Save accurate status, then resume ONLY the separate **Native Android mobile POS** in a future chat. This document is a handoff, not an instruction to immediately implement anything.

## Read this first in any new ChatGPT / developer session

- Web POS release handoff: [CpIPOS issue #194](https://github.com/sstdevelopaminno/CpIPOS/issues/194).
- IT billing handoff: [CpIPOS-IT issue #56](https://github.com/sstdevelopaminno/CpIPOS-IT/issues/56).
- Native Android source: [cpipos-native-pos](https://github.com/sstdevelopaminno/cpipos-native-pos) and [PR #3](https://github.com/sstdevelopaminno/cpipos-native-pos/pull/3).
- Separate repos must remain separate:
  - POS Web/legacy Android production: `sstdevelopaminno/CpIPOS`
  - IT control plane: `sstdevelopaminno/CpIPOS-IT`
  - New native Android POS 2.x: `sstdevelopaminno/cpipos-native-pos`
  - Desktop POS, where applicable: a **separate** desktop repository, not Web or Native.
- Use existing Supabase **CpiPOS-001**, ID `deejlitaivfnsbwqdugy`; no new Vercel/Supabase project to work around limits.
- Native Android **does not use Vercel to compile, distribute or run the app**. Build APK with Android/Gradle locally or GitHub Actions. A separately reviewed Supabase Edge Function/RPC may be used for trusted server-side credentials/transactions; never store `service_role` in APK.
- **Do not touch production Android 1.0.23** (`com.cpipos.pos`) or production Web while developing Native 2.0 (`com.cpipos.pos.next` development application identity).

## Paused Web POS: precise deployment truth

- Repo `sstdevelopaminno/CpIPOS`, intended production branch `agent-docs-preflight-schema-drift`.
- Subscription UI/backend PR **#192 merged** to branch as commit `fc4e9e7213b80c04e19557c789a35a197e0033b8`. GitHub CI passed.
- LAST CHECKED Vercel `cp-ipos-web`: Production still READY **on old commit** `31e1e1b55db3fee5d6d2b740b7b92b17586e43ca`; newer commit not served. Status: `Deployment rate limited — retry in 24 hours.` Preview `39050ae7554b32f71bee020079a674b54d82a7a8` is older than merged code. **Do not promote old Preview as complete release.**
- Owner-provided 30-day Usage screen: CPU 1h34/4h, Edge 198k/1M, Function Invocations 107k/1M, Deployment Storage 220.59 MB/10GB; screenshot does NOT prove the team reached 100 deployments. Cause of Vercel deployment counter was not confirmed.
- Implemented but NOT publicly live: `/preview/pos/payments` package/subscription overview, renewal request, company account panel, owner-submitted private slip notice, history, docs placeholder. These must remain separate from cashier sales, paid orders, receipt printing and opening/closing shifts.
- Work left IF owner later reopens Web/IT: exact-code production deployment+smoke checks, independently bank-verified transaction settlement with idempotent monthly/yearly renewal, verified receipt vs quotation PDF (user to attach specimen), real email delivery, full end-to-end POS/IT isolation tests.
- Do not keep attempting Vercel deploy or making commits just to retrigger it without new permission.

## Paused IT: what exists vs what does not

- Repo `sstdevelopaminno/CpIPOS-IT` `main`; billing PR **#55 merged** as `e3c0340e3fb6b0eee8f623d6a5fa6e93b89793fc`. GitHub CI passed.
- Existing IT **ตารางชำระแพ็กเกจ** displays store/package/interval/status/dates/price/history; editable legal issuer / receiving bank / company/support contacts. Private slip bucket `subscription-payment-evidence` and one-open-request DB index are on existing CpiPOS-001.
- IT can accept for manual review or reject with reasons and audit; **cannot yet claim confirmed bank receipt, automated settlement/renewal or actual PDF issuance**. Paid billing-cycle status and uploaded slip are NOT a numbered issued receipt.
- IT Vercel Production project expected `cp-ipos-it-web`, but accessible Vercel linkage was not independently verified; do not create a substitute project.
- Corporate identity for current legal documents temporarily **บริษัท คัตติ้งพอยท์ เทค จำกัด / CUTTING POINT TECH CO., LTD.**; proposed new company name pending registration confirmation. Not yet VAT-registered; no automatic VAT tax invoices. Legal identity/registration/address and receiving account must be configurable from IT and snapshot into issued documents. Company: `cuttingpointtech@gmail.com`, Support: `cuttingpointtech.support@gmail.com`.
- Support Chat/Ticket is **Phase 2 and not started**; requirements include tenant/store branding, contact, assigned agent, case history and configurable 7- or 30-day chat deletion after closure.

## Next ACTIVE workstream: Native Android POS 2.0 — no Vercel

**Project snapshot independently checked in GitHub on 2026-09-25:**
- Repo `sstdevelopaminno/cpipos-native-pos`; active dev branch `feature/native-auth-phase1`, HEAD `2b07c5de5d329469a4f9e1d43643f344f113908d`; open PR #3. Default branch is `main`.
- Existing foundation: Kotlin/Jetpack Compose native UI, gradle wrapper, CI debug-APK workflow `.github/workflows/native-foundation.yml`, isolated development app ID `com.cpipos.pos.next` (debug suffix configured), source `app/src/main/java/com/cpipos/pos/next/ui/MobilePosPreviewScreen.kt`.
- Currently demonstrated UI: login, branch selection, product/sale grid, cart, checkout *placeholder*; **NOT** a production-capable sale or paid transaction app.
- Existing Supabase CpiPOS-001 read-only catalog foundation and guarded auth abstraction. `GuardedNativeAuthGateway` intentionally returns BackendUnavailable, does not authenticate against live backend. The app must not call product data without a reviewed authenticated tenant/branch session. No live sale, shift, payment, stock mutation paths are enabled.
- Existing developer docs on active branch: `README.md`, `docs/DEVELOPMENT_STATUS.md`, `docs/ROADMAP.md`, `docs/AUTH_PHASE1.md`, `docs/ARCHITECTURE.md`, `docs/PHASE1_SUPABASE_READONLY.md`, `docs/BUILD.md`, `docs/SECURITY.md`.
- Previously verified local Debug APK build using JDK 17 and `gradlew.bat --no-daemon :app:assembleDebug`; CI artifact `cpipos-native-preview-debug-apk`; prior local path `E:\cpipos-native-pos-latest\app\build\outputs\apk\debug\app-debug.apk`. **This is a past test fact, not a claim a current APK has just been generated.**
- Prior hardware test was on vivo V2417; for new local test obtain device serial afresh from `adb devices`, then `adb install -r <apk>` and `scrcpy -s <device serial>` (do not publish private identifiers to the repo).

**Next development priorities, only when owner explicitly starts Android work:**
1. Inspect current Native branch, PR #3 and GitHub CI; confirm user-approved mobile UI / sales mode requirements before editing.
2. Improve Kotlin Compose login/branch/product/cart/checkout UX in the **native repo only**, using safe mock/read-only data until backend auth is verified.
3. Define server-verified Store Code + employee PIN auth boundary (Supabase Edge Function or tightly scoped RPC); do not download PIN hashes, log raw PINs or embed server secrets. Then implement `NativeAuthGateway`, session+tenant+branch+device/package/bootstrap.
4. Implement offline-first Room/SQLite, explicit sync queue/idempotency/conflict audit. Then transactional sale, cashier cash/transfer and change, stock deduction, receipt printing, shift opening and closing, plus barcode support. These are future work, **not finished features**.
5. Build/debug APK locally or GitHub Actions, test on actual Android hardware, and preserve production Android 1.0.23 until a reviewed migration and signing/update path exists.

## Rule for any assistant reading this file

This is the single chronological checkpoint for a *paused* Web/IT project and a *planned next* Native POS workstream. **Do not assume web code merged to GitHub is already live on Vercel; do not claim Android mock flows are production-ready; do not start working on anything solely because it is listed as pending.** Ask only for actual new Android task/screenshot when the user resumes. Avoid Codex as a prerequisite; GitHub connector, Gradle and appropriate tools can be used directly.
