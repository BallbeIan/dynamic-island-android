# Releasing to Google Play

## One-time setup

1. Create a Play developer account (one-time US$25 fee, identity verification). New personal accounts must run a **closed test with at least 12 testers for 14 days** before they can apply for production access: check the current rule in Play Console.
2. Upload key: `~/orbit-keys/orbit-upload.jks`, with its passwords in `keystore.properties` (git-ignored; template in `keystore.properties.example`). **Back both up somewhere safe.** If you lose the upload key you can ask Google to reset it, but keep a copy anyway. Play App Signing holds the real app-signing key.
3. Create the app in Play Console, then fill in everything under **App content** using `DECLARATIONS.md`, and the store listing using `LISTING.md`.

## GitHub releases (automatic)

`.github/workflows/release.yml` builds, signs and publishes the APK to GitHub Releases whenever a version tag is pushed.

One-time setup: store the signing key as four repository secrets (Settings → Secrets and variables → Actions, or with the GitHub CLI from the project folder):

```bash
base64 -w0 ~/orbit-keys/orbit-upload.jks | gh secret set KEYSTORE_BASE64
grep '^storePassword=' keystore.properties | cut -d= -f2- | tr -d '\n' | gh secret set KEYSTORE_PASSWORD
grep '^keyAlias='      keystore.properties | cut -d= -f2- | tr -d '\n' | gh secret set KEY_ALIAS
grep '^keyPassword='   keystore.properties | cut -d= -f2- | tr -d '\n' | gh secret set KEY_PASSWORD
```

To publish a release:

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`, commit and push to `main`.
2. `git tag v1.1 && git push origin v1.1` (the tag must be `v` + `versionName`, or the workflow stops). A tag with a hyphen, such as `v1.1-beta`, is published as a pre-release.
3. Watch the run under the repository's **Actions** tab. It lints, builds, checks the APK is signed with the release key (it refuses a debug-signed APK), and creates the release with `Orbit-<version>.apk`, its `.sha256`, install notes and auto-generated changelog.

If a secret is missing the workflow fails before building; it never publishes an unsigned or debug-signed APK.

## Every release (Google Play)

1. Bump `versionCode` (must increase every upload) and `versionName` in `app/build.gradle.kts`.
2. `export ANDROID_HOME=~/Android/Sdk && ./gradlew lint bundleRelease`
3. Upload `app/build/outputs/bundle/release/app-release.aab` to a testing track (Internal testing is instant), install from the Play link on a real phone and test: island appears, accessibility and notification disclosures show, gestures, alerts, foldable screens.
4. Promote to Closed testing, then Production.

## Requirements to keep an eye on

- **Target API level:** the app targets API 36, which Play required for new apps and updates from 31 August 2026. Google raises it every year, so bump `compileSdk` / `targetSdk` (and the Android Gradle plugin) each summer and retest.
- Play may ask for a video of the accessibility disclosure (see `DECLARATIONS.md`).
