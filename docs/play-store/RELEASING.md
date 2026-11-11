# Releasing to Google Play

## One-time setup

1. Create a Play developer account (one-time US$25 fee, identity verification). New personal accounts must run a **closed test with at least 12 testers for 14 days** before they can apply for production access: check the current rule in Play Console.
2. Upload key: `~/orbit-keys/orbit-upload.jks`, with its passwords in `keystore.properties` (git-ignored; template in `keystore.properties.example`). **Back both up somewhere safe.** If you lose the upload key you can ask Google to reset it, but keep a copy anyway. Play App Signing holds the real app-signing key.
3. Create the app in Play Console, then fill in everything under **App content** using `DECLARATIONS.md`, and the store listing using `LISTING.md`.

## Every release

1. Bump `versionCode` (must increase every upload) and `versionName` in `app/build.gradle.kts`.
2. `export ANDROID_HOME=~/Android/Sdk && ./gradlew lint bundleRelease`
3. Upload `app/build/outputs/bundle/release/app-release.aab` to a testing track (Internal testing is instant), install from the Play link on a real phone and test: island appears, accessibility and notification disclosures show, gestures, alerts, foldable screens.
4. Promote to Closed testing, then Production.

## Requirements to keep an eye on

- **Target API level:** the app targets API 36, which Play required for new apps and updates from 31 August 2026. Google raises it every year, so bump `compileSdk` / `targetSdk` (and the Android Gradle plugin) each summer and retest.
- Play may ask for a video of the accessibility disclosure (see `DECLARATIONS.md`).
