# AI Anime Android Suite

This directory contains two native Android applications reconstructed from the
provided APKs and extended with a safer update workflow.

## Applications

- `developer` — package `com.example.aideveloper`
- `build-host` — package `com.example.aideveloper.host`

The supplied APKs were signed binaries. Their original Kotlin/Java source and
original signing keys were not present in the ZIP archives. The directory
`recovery-reference/` keeps the supplied APKs and recovered resources as
reference material only; it is not treated as source code.

The new source is intentionally marked in comments and documentation as
`RECONSTRUCTED` or `NEW`. No claim is made that the original source was
recovered.

## Security model

- API credentials are entered by the user and stored with Android Keystore
  backed AES-GCM encryption. They are not in source, APK resources, or Git.
- Developer requests are analyzed and serialized as a signed development
  request. A generated patch is never executed on the phone.
- Build Host validates SHA-256, package name, version upgrade, APK parseability,
  and the installed app's signing certificate before invoking Android's
  official package installer.
- Android may require user confirmation. The apps do not bypass that control.
- Temporary update files live in app cache and are deleted after acceptance,
  rejection, or cancellation.

## Build

```bash
./gradlew :developer:assembleDebug :build-host:assembleDebug
```

Release builds use the signing environment variables documented in
`.github/workflows/android.yml`. Without the original signing key, a release
cannot update an already installed APK signed by a different key.