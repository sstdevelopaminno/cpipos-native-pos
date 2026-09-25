# Development Status

Last verified: 2026-09-22

## Verified baseline

- Repository status was clean before the first foundation edits in this workspace.
- `.\gradlew.bat --no-daemon :app:assembleDebug` succeeds with JDK 17.
- GitHub Actions debug APK workflow exists at `.github/workflows/native-foundation.yml`.
- Android app id remains isolated as `com.cpipos.pos.next` with debug suffix `.dev`.
- Current app surface is a mobile POS preview flow: login, branch selection, sale screen, cart, and checkout placeholder.
- `MobilePosPreviewScreen.kt` is the single active Compose UI surface for the preview flow; the older standalone auth screen has been removed to avoid duplicate UI ownership.
- Supabase project `CpiPOS-001` / `deejlitaivfnsbwqdugy` was confirmed active through the Supabase plugin.
- Supabase wiring remains read-only foundation code; no sale, payment, stock, shift, or production mutation path is exposed.

## UI preview workflow

- Open `app/src/main/java/com/cpipos/pos/next/ui/MobilePosPreviewScreen.kt` in Android Studio to view Compose previews for Login, Branch, Sale, and Checkout.
- Run `.\gradlew.bat --no-daemon :app:assembleDebug` before handing off UI work.
- Keep preview data local/mock-only until the trusted authentication and session bootstrap contracts are reviewed.

## Safe next development items

1. Define the trusted Store Code + employee PIN authentication RPC or Edge Function contract.
2. Add a production `NativeAuthGateway` implementation only after the trusted boundary is reviewed.
3. Add session bootstrap models for tenant, employee, branch, device, package/features, and initial products.
4. Add unit tests around PIN clearing, gateway result handling, and read-only repository contracts.
5. Improve the active mobile POS preview UI in `MobilePosPreviewScreen.kt` before wiring real production data.

## Do not change without a reviewed plan

- Production Android 1.0.23 repository.
- Production app id `com.cpipos.pos`.
- CpiPOS-001 schema, RLS, triggers, or production data.
- Supabase service-role keys, database passwords, or Android signing material.
