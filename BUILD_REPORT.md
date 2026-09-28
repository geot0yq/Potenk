# Build and verification report

## Baseline build before the automatic-build change

- Android Gradle Plugin: 8.7.3
- Gradle wrapper: 8.10.2
- Compile/target SDK: 35
- `:developer:test`: passed
- `:build-host:test`: passed
- `lint`: passed with no blocking errors
- Release APK build: passed for both applications before the automatic-build change
- `aapt` package/version validation: passed
- `apksigner verify`: passed for both APKs
- Developer package/version: `com.example.aideveloper`, versionCode 3
- Build Host package/version: `com.example.aideveloper.host`, versionCode 6
- Both delivered APKs share the same new local release certificate
- SHA-256 values: `deliverables/SHA256SUMS.txt`

## Security scans

- Dependency audit: 0 critical, high, moderate, low, or informational findings
- HoundDog privacy/data-flow scan: no findings
- SAST: the scanner process exited non-zero with
  `semgrep-pro-fast exited non-zero (exitCode=1)` and did not return findings.
  This is reported as an unavailable scanner result, not as a clean SAST pass.

## Installation test boundary

`adb devices` found no connected emulator or physical Android device in this
environment. Therefore no install or post-install update is claimed as
device-tested. The APKs are signed and statically validated as installable.
On a device, Android may still require user confirmation and the Build Host
will reject any candidate whose package, version, hash, parseability, or signer
does not match.

## Automatic build/update change

- Build Host now dispatches `.github/workflows/android.yml` after receiving a
  development request, waits for the matching workflow run, downloads only
  that run's `ai-anime-validation-apks` artifact, and passes `Developer.apk`
  through the existing update gate.
- The GitHub token is stored with Android Keystore-backed AES-GCM encryption.
- The validation build uses a timestamp-based `versionCode` so a successful
  build can be considered a newer candidate.

## Verification status after the change

The current workspace does not provide an Android SDK or cached Android Gradle
Plugin. Gradle was started with the available JDK, but the offline verification
stopped before compilation because `com.android.application:8.7.3` was not
available locally. No APK produced after this change is claimed.

## Remaining blocker for original-app upgrades

The supplied Developer APK is signed with a certificate whose SHA-256 digest
differs from the new local release certificate. The original signing private
key was not supplied. Build Host intentionally rejects a candidate signed by a
different key, so an update over the supplied installed APK cannot be claimed
until the original key is provided through secure signing secrets.

## GitHub publication status

The GitHub connection was readable and the repository was initialized, but
write attempts through both Git Data and Contents endpoints were blocked by a
Cloudflare 403/404 response from the connector path. The complete source,
workflow, and documentation remain in this workspace and are included in the
source ZIP deliverables. No GitHub commit, Actions run, or Release upload is
claimed from this session.